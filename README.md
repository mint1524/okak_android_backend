# okak backend

Серверная часть мобильного приложения: подписочный чат с LLM-моделью.
Стек — Ktor, PostgreSQL, Exposed, Flyway.

## Локальный запуск

```bash
cp .env.example .env
docker compose up -d
curl http://localhost:8080/health
```

В `.env` задаются `JWT_SECRET`, параметры PostgreSQL (`POSTGRES_*`, `APP_PORT`) и
настройки LLM-провайдера.

## Тесты

```bash
./gradlew test
```

Тесты работают на in-memory репозиториях (`USE_IN_MEMORY_DB=true`), внешняя БД не требуется.

## LLM-провайдер

Провайдер выбирается переменной `LLM_PROVIDER`:

- `mock` — детерминированная заглушка для разработки и тестов;
- `groq` — OpenAI-совместимый endpoint (`LLM_BASE_URL`, `LLM_API_KEY`, `LLM_MODEL`,
  модель по умолчанию `llama-3.3-70b-versatile`).

Запрос уходит одним POST на `${LLM_BASE_URL}/chat/completions`; при сетевой ошибке или
таймауте возвращается безопасный fallback вместо 500. Реализации лежат в
`src/main/kotlin/llm`. Новый провайдер — это реализация `LlmClient` и ветка в `Application.module()`.

## Эндпоинты

| Метод | Путь | Назначение |
|---|---|---|
| POST | /auth/register | регистрация |
| POST | /auth/login | логин |
| POST | /auth/refresh | обновление токена |
| GET | /user/me | профиль и статус подписки |
| GET | /chats | список чатов |
| POST | /chats | создать чат |
| PATCH | /chats/{id} | переименовать чат |
| DELETE | /chats/{id} | удалить чат |
| GET | /chats/{id}/messages | история сообщений |
| POST | /chats/{id}/messages | отправить сообщение, получить ответ LLM |
| GET | /subscriptions/plans | доступные тарифы |
| GET | /subscriptions/status | статус подписки |
| POST | /subscriptions/verify | активация подписки |

## Стек

- Ktor 3.4 (Netty)
- PostgreSQL 16, Exposed DSL, HikariCP
- Flyway (миграции в `src/main/resources/db/migration`)
- jBCrypt, JWT (HS256)
