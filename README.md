# okak backend

Ktor + Postgres + Exposed + Flyway. Подписочный LLM-чат.

## Локально

```bash
cp .env.example .env
# заполни JWT_SECRET и при желании APP_PORT/POSTGRES_*
docker compose up -d
curl http://localhost:8080/health
```

## Тесты

```bash
./gradlew test
```

Тесты используют in-memory репозитории (`USE_IN_MEMORY_DB=true`), Postgres не нужен.

## Деплой в Dokploy

1. В Dokploy создать проект → Application → Compose.
2. Source: GitHub `mint1524/okak_android_backend`, branch `main`.
3. Compose Path: `docker-compose.yml`.
4. Environment variables (вкладка Environment):
   ```
   POSTGRES_DB=okak
   POSTGRES_USER=okak
   POSTGRES_PASSWORD=<сильный пароль>
   JWT_SECRET=<минимум 32 символа>
   APP_PORT=8080
   ```
5. Domains: добавить домен и включить HTTPS (Let's Encrypt). Traefik сам перенаправит на сервис `app:8080`.
6. Deploy. После первого старта Flyway создаст схему автоматически.

### Health check

`GET /health` отдаёт `{"status":"ok"}`.

### Обновление

Коммит в `main` → Dokploy подтянет новый образ (auto-deploy если включён) или ручной Redeploy.

## Эндпоинты

| Метод | Путь | Назначение |
|---|---|---|
| POST | /auth/register | регистрация |
| POST | /auth/login | логин |
| GET | /user/me | профиль + статус подписки |
| GET | /chats | список чатов |
| POST | /chats | создать чат |
| DELETE | /chats/{id} | удалить чат |
| GET | /chats/{id}/messages | история сообщений |
| POST | /chats/{id}/messages | отправить сообщение, получить ответ от LLM |
| GET | /subscriptions/plans | доступные тарифы |
| GET | /subscriptions/status | статус подписки |
| POST | /subscriptions/verify | активировать подписку (заглушка под Google Play) |

## Стек

- Ktor 3.4 (Netty)
- Postgres 16, Exposed 0.56 DSL, HikariCP
- Flyway 9.22.3 (миграции в `src/main/resources/db/migration`)
- jBCrypt, JWT (HS256)
- Mock LLM в `llm/LlmClient.kt` — заменить на реальный провайдер за фичефлагом
