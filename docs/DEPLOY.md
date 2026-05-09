# Деплоймент-гайд (Dokploy + VPS)

Полный сценарий: «голый Ubuntu» → продакшен с HTTPS, Postgres-бэкапами и автодеплоем по push.

## 0. Что должно быть на руках

- VPS (Ubuntu 22.04 / 24.04 LTS, минимум 2 vCPU / 2 GB RAM / 20 GB диск).
- Доменное имя, A-запись которого ведёт на IP сервера (например `okak.example.com`).
- SSH-доступ под root или sudo-пользователя.
- Аккаунт GitHub с правом push в `mint1524/okak_android_backend`.
- Ключ Groq из `https://console.groq.com/keys` (или другого LLM-провайдера).

## 1. Подготовка VPS

```bash
ssh root@<IP>
apt update && apt upgrade -y
apt install -y curl ufw

# Минимальный фаервол
ufw allow OpenSSH
ufw allow http
ufw allow https
ufw enable
```

## 2. Установка Dokploy

```bash
curl -sSL https://dokploy.com/install.sh | sh
```

Ставится автоматом: Docker, Docker Compose, Traefik, сам Dokploy. Веб-консоль откроется на `http://<IP>:3000` — создать админ-аккаунт с почтой/паролем.

После регистрации в Settings → Server указать домен (если есть) и включить HTTPS для самой консоли Dokploy.

## 3. Подключение GitHub

В Dokploy: **Settings → Git → Add provider → GitHub**. Авторизоваться через OAuth, выбрать репозиторий `mint1524/okak_android_backend`. Это даст веб-хук для авто-деплоя по push в `main`.

## 4. Создание проекта и приложения

1. **Projects → New Project** → имя `okak`.
2. Внутри проекта **New → Application → Compose**.
3. **Source**:
   - Provider: GitHub
   - Repo: `mint1524/okak_android_backend`
   - Branch: `main`
   - Compose Path: `docker-compose.yml`
4. **Environment**: вставить блок (не оставлять JWT_SECRET и POSTGRES_PASSWORD по умолчанию):
   ```env
   POSTGRES_DB=okak
   POSTGRES_USER=okak
   POSTGRES_PASSWORD=<openssl rand -hex 24>
   JWT_SECRET=<openssl rand -hex 32>
   APP_PORT=8080
   LLM_PROVIDER=groq
   LLM_API_KEY=<gsk_...>
   LLM_MODEL=llama-3.3-70b-versatile
   LLM_BASE_URL=https://api.groq.com/openai/v1
   LLM_SYSTEM_PROMPT=Ты дружелюбный ассистент. Отвечай по-русски, кратко и по делу.
   LLM_MAX_TOKENS=512
   LLM_TEMPERATURE=0.7
   ```
   Пометить `JWT_SECRET`, `LLM_API_KEY`, `POSTGRES_PASSWORD` как **Secret** — они скроются в UI и логах.
5. **Domains**:
   - Host: `okak.example.com`
   - Service: `app`
   - Port: `8080`
   - Path: `/`
   - HTTPS: on, Let's Encrypt provider.
   Traefik сам выпустит сертификат после нажатия Save.
6. **Volumes / Compose**: ничего менять не надо — `okak_db` уже описан как named volume в `docker-compose.yml`. Это переживает рестарт контейнера.
7. **Auto Deploy**: включить. Теперь push в `main` будет автоматически собирать и перезапускать.
8. **Deploy**. Через 2-3 минуты `https://okak.example.com/health` отдаст `{"status":"ok"}`.

## 5. Подключение Android-клиента

В `OKAKAPP/app/src/main/java/com/example/okakapp/data/remote/ApiClient.kt` заменить:
```kotlin
private const val BASE_URL = "https://okak.example.com/"
```
Сделать релиз-сборку:
```bash
./gradlew :app:assembleRelease
```
HTTPS — конфиг `network_security_config.xml` уже допускает любые HTTPS-домены, ничего менять не надо.

## 6. Бэкапы Postgres

### Ручной (на сервере):
```bash
docker exec okak-app okak-app-android-back-db-1 pg_dump -U okak okak | gzip > /var/backups/okak-$(date +%Y%m%d-%H%M).sql.gz
```

### По расписанию через cron (раз в сутки в 03:00, хранить 14 дней):
```cron
0 3 * * * docker exec okak-app-android-back-db-1 pg_dump -U okak okak | gzip > /var/backups/okak-$(date +\%Y\%m\%d).sql.gz && find /var/backups -name 'okak-*.sql.gz' -mtime +14 -delete
```

### Восстановление:
```bash
gunzip -c /var/backups/okak-20260509.sql.gz | docker exec -i okak-app-android-back-db-1 psql -U okak -d okak
```

## 7. Мониторинг и логи

- **Логи приложения** в реальном времени: Dokploy → Application → Logs (Traefik + контейнер).
- **CLI**: `docker logs -f okak-app-android-back-app-1`.
- **Health-проба** для внешнего мониторинга (UptimeRobot и т.п.): URL `https://okak.example.com/health`, интервал 5 минут.
- **Использование БД**: `docker exec okak-app-android-back-db-1 psql -U okak -d okak -c "SELECT count(*) FROM messages;"`

## 8. Обновление

- **Авто**: push в `main` → Dokploy ловит вебхук → пересобирает образ → перезапускает контейнер. Старая версия остаётся в кэше до следующего пересоздания.
- **Ручное**: Application → Redeploy. Иногда полезно после изменения env-переменных.

## 9. Откат версии

Самый быстрый путь:
```bash
ssh root@<IP>
cd /etc/dokploy/projects/<project-id>/
git log -5
git checkout <старый_sha> -- docker-compose.yml
docker compose up -d --build
```

В UI Dokploy: Application → Deployments → выбрать предыдущее зелёное → Redeploy this version.

## 10. Ротация секретов

JWT_SECRET / LLM_API_KEY можно поменять в Environment без даунтайма — Dokploy сам перезапустит контейнер. После смены `JWT_SECRET` все ранее выданные токены станут невалидными — пользователям придётся залогиниться заново. Это ожидаемо.

## 11. Чек-лист «всё ли хорошо»

```bash
curl https://okak.example.com/health                 # → {"status":"ok"}
curl -X POST https://okak.example.com/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"smoke@example.com","password":"qwerty123"}' # → {"accessToken":"..."}
docker logs okak-app-android-back-app-1 | tail -50    # → "LLM provider=groq" и зелёные ответы
docker exec okak-app-android-back-db-1 psql -U okak -d okak -c '\dt'  # → users, chats, messages, subscriptions, flyway_schema_history
```

Если все четыре пункта зелёные — продакшен готов.
