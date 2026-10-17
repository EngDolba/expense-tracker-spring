# Financial Ledger — Spring

Java/Spring Boot rewrite of `../expense-tracker` (Next.js). The original stays
untouched as the reference implementation until this one reaches parity.

- `backend/`: Spring Boot 4.0, Java 21, Maven, PostgreSQL + Flyway.
- `frontend/`: the React UI ported to a Vite SPA (not started yet).

## Accounting boundary

Expenses, obligations and accounts are financially isolated, as in the
original: none of them posts into another, and an account's balance comes only
from its opening balance and its own events. Fitness is separate from the
financial totals.

## Backend

Configuration comes from the environment (see `.env.example`): `DATABASE_URL`
(libpq or `jdbc:` form), `APP_USERNAME`, `APP_PASSWORD_HASH` (existing `$2b$`
hashes work), `SESSION_SECRET` (32+ characters), and `APP_SECURE_COOKIE=false`
for plain-http local development only.

```bash
cd backend && ./mvnw verify
```

`*IT` tests boot the whole app against Postgres in Docker (Testcontainers) and
are skipped when Docker is unavailable.

### Auth API

| | |
|---|---|
| `GET /api/auth/csrf` | Issues the `XSRF-TOKEN` cookie; echo its value as `X-XSRF-TOKEN` on every POST/PUT/PATCH/DELETE. |
| `POST /api/auth/login` | `{username, password}` → sets the HttpOnly `ledger-session` cookie (HS256 JWT, 14 days). 401 `invalid_credentials`, 429 `too_many_attempts` (5 failures per username or 20 per IP in 15 min). |
| `POST /api/auth/logout` | Clears the session cookie. |
| `GET /api/auth/me` | The signed-in user, or 401 `unauthenticated`. |
| `GET /api/health` | 200 when `SELECT 1` works, 503 otherwise. Public. |

Errors are RFC 7807 problems with a stable `code` property for the frontend to
translate.
