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

## Деплой

Шпаргалка:
1. В Dokploy: Project → New Application → Compose, репо `mint1524/okak_android_backend`, branch `main`.
2. Environment: переменные из `.env.example`, обязательно сменить `JWT_SECRET` и `POSTGRES_PASSWORD`. Если нужен реальный LLM — `LLM_PROVIDER=groq` + `LLM_API_KEY=gsk_...`.
3. Domains: домен → service `app` → port `8080`, HTTPS on.
4. Deploy.

Полный гайд (VPS + бэкапы + откат + мониторинг): [`docs/DEPLOY.md`](docs/DEPLOY.md).

### Health check

`GET /health` отдаёт `{"status":"ok"}`.

## LLM провайдер

`LLM_PROVIDER=mock` (по умолчанию) — заглушка для разработки и тестов.

`LLM_PROVIDER=groq` — реальный Groq (OpenAI-совместимый API). Получить ключ на https://console.groq.com/keys, прописать в `LLM_API_KEY`. Модель по умолчанию `llama-3.3-70b-versatile`. Под капотом Ktor-клиент делает один POST на `${LLM_BASE_URL}/chat/completions`. При сетевой ошибке/таймауте отдаётся вежливый fallback вместо 500-ки.

Чтобы добавить ещё одного провайдера (OpenAI, Gemini и т.п.) — реализовать `LlmClient` в `src/main/kotlin/llm/` и добавить ветку в `Application.module()`.

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
