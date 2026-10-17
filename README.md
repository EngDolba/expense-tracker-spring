# Financial Ledger — Spring

Java/Spring Boot rewrite of `../expense-tracker` (Next.js + Prisma). The
original is untouched and stays the reference until this one replaces it.

A private, single-user, Georgian/English ledger with three financially
isolated registers (expenses and income, obligations, accounts) and a separate
fitness tracker with a guided workout runner.

| | |
|---|---|
| `backend/` | Spring Boot 4.0, Java 21, Maven, PostgreSQL, Flyway, Spring Security, Apache POI |
| `frontend/` | React 19 SPA (Vite 8, React Router 7, TanStack Query, Tailwind 4, Radix, Recharts) |
| `Dockerfile` | One image: the SPA is embedded in the jar and served from the same origin as `/api` |

## Accounting boundary

Unchanged from the original: the registers share categories and currencies but
never post into one another. An expense never changes an account; an
obligation outlay or reimbursement never creates an expense or account event;
an account's balance is its opening balance plus its own events only. Fitness
is outside the financial totals. Integration tests assert each of these.

## Running locally

No PostgreSQL install needed. `DevServer` starts an embedded one:

```bash
cd backend && ./mvnw spring-boot:test-run
```

```bash
cd frontend && pnpm install && pnpm dev
```

Open http://localhost:5173 and sign in as `dev` / `dev`. Vite proxies `/api` to
:8080. Data is lost when the backend stops.

Against a real database, set the environment from `.env.example` and run
`./mvnw spring-boot:run` (Flyway migrates on start).

## Tests

```bash
cd backend && ./mvnw verify
```

```bash
cd frontend && pnpm lint && pnpm typecheck && pnpm test
```

```bash
cd frontend && pnpm test:e2e
```

- Backend unit tests cover the logic ported from `src/lib/*.test.ts`: money,
  categories, rules, every fitness rule, the workbook parser, auth.
- `*IT` tests boot the whole app on embedded PostgreSQL 17 (no Docker). They
  exercise every endpoint, the ledger isolation, imports, the full workout
  lifecycle and the Prisma migration.
- Frontend: the original Vitest suites (fitness rules, categories, money,
  number field, workout runner) run unchanged.
- End to end: the original Playwright specs (login, ledgers, XLDR import, every
  fitness flow) on desktop Chrome and Pixel 7. They start the backend on
  embedded PostgreSQL and the production build themselves. The visual
  snapshot spec was not ported; its baselines were rendered by Next.js. On
  Windows, stopping `mvnw.cmd` can leave its forked JVM running, and the next
  run reuses it with its data. Stop stray `java` processes first.

## Configuration

| Variable | |
|---|---|
| `DATABASE_URL` | `postgresql://user:pass@host/db?sslmode=require` (Neon/RDS form) or a `jdbc:` URL |
| `APP_USERNAME`, `APP_PASSWORD_HASH` | The single owner. Existing bcryptjs `$2b$` hashes work. `APP_PASSWORD_HASH_BASE64` is also accepted. |
| `SESSION_SECRET` | 32+ characters (or `SESSION_SECRET_BASE64`). With the same secret, sessions from the old app stay valid. |
| `APP_SECURE_COOKIE` | `false` only for plain-http local use |

## API

JSON under `/api`. Everything except login, the CSRF bootstrap and health
needs the `ledger-session` cookie. Mutations need the `X-XSRF-TOKEN` header
echoing the `XSRF-TOKEN` cookie. Errors are RFC 7807 problems with a stable
`code` property.

| Area | Endpoints |
|---|---|
| Auth | `GET /auth/csrf`, `POST /auth/login`, `POST /auth/logout`, `GET /auth/me`, `GET /health` |
| Categories | `GET/POST /categories`, `PUT /categories/{id}`, `POST /categories/{id}/archive` |
| Expenses | `GET/POST /expenses`, `DELETE /expenses/{id}`, `POST /rules`, `DELETE /rules/{id}` |
| Obligations | `GET /obligations`, `POST /obligations/{parties,cases,entries}`, `DELETE /obligations/entries/{id}` |
| Accounts | `GET/POST /accounts`, `POST /accounts/{events,transfers,reconcile}`, `PUT /exchange-rates` |
| Overview | `GET /dashboard`, `GET /exports/expenses`, `GET /exports/accounts` (CSV) |
| Import | `GET/POST /imports` (multipart `workbook`), `GET /imports/{id}`, `POST /imports/{id}/commit` |
| Fitness | `GET /fitness`, exercises, variations, plans, days, plan-exercises (+ `/superset` toggle) |
| Workouts | `POST /fitness/workouts` (start or resume), `GET/DELETE /fitness/workouts/{id}`, `PUT .../note`, `POST .../complete`, `PUT /fitness/active-sets/{id}`, `PUT /fitness/active-exercises/{id}/{variation,wrap-up}` |

Each page's `GET` returns the same JSON shape the Next.js page loader
produced.

## Migrating the existing data

The schema is new (UUID keys, join tables for tags). `PrismaMigrator` copies the
old database into an empty new one in a single transaction. It then
reconciles row counts, expense and income totals, outstanding obligations,
every account balance and total workout volume, and rolls everything back on
any mismatch.

```bash
cd backend && SOURCE_DATABASE_URL='postgresql://…old…' DATABASE_URL='postgresql://…new…' ./mvnw spring-boot:run -Dspring-boot.run.profiles=migrate
```

The source is only read. Run it against a fresh database. It refuses a
non-empty one.

## Deployment

```bash
docker build -t ledger .
```

The image runs as a non-root user, needs only a writable `/tmp` (so
`--read-only` works), and health-checks `/api/health`. CI builds it and scans it
with Trivy. The AWS infrastructure (Terraform, ECR, RDS) follows
`../expense-tracker/docs/aws-cloud-security-plan.md` and is not part of this
repository yet.

## Deliberate differences from the original

- **Exact money.** Amounts are `BigDecimal` end to end instead of JavaScript
  numbers.
- **Obligations.** Each party's outstanding balance is computed over all its
  entries; the original summed only the latest 300. A case must belong to the
  entry's party.
- **Security** (from the AWS plan's phase 2):
  - The post-login redirect only follows same-site paths (fixes the
    `//evil.example` open redirect).
  - Logins are rate-limited: 5 failures per username or 20 per IP in 15
    minutes, logged.
  - CSRF uses double-submit cookies.
  - CSP, HSTS, frame-ancestors and referrer-policy headers are set.
  - Upload size is enforced before parsing.
- **Importer.** Impossible dates such as 31.02 are flagged instead of passed on.
  Excel error cells read as empty.
- **Form errors** show as a dismissible notice above the page instead of an
  error screen.
- Category rename requires a non-empty name.
