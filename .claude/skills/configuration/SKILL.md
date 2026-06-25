---
name: configuration
description: Use when adding or changing application configuration in this backend — application.yml and the dev/prod profiles, environment variables and secrets, datasource/Hikari/JPA settings, per-environment toggles, or injecting a config value into a bean.
---

# Configuration & Environments

## Overview

Spring Boot externalized config: one **base** `application.yml` plus per-profile overrides
`application-dev.yml` / `application-prod.yml`, all under `app/src/main/resources/`. All secrets
come from **environment variables** — nothing sensitive is hardcoded. Profiles are selected at
launch with `--spring.profiles.active=dev|prod`.

## File layout

| File                   | Holds                                                              |
|------------------------|-------------------------------------------------------------------|
| `application.yml`      | Base: datasource, Redis, RabbitMQ, mail, Supabase, Firebase, JWT secret, app (`chirp.*`) settings, nginx trusted IPs. |
| `application-dev.yml`  | Dev overrides: `ddl-auto: update`, long JWT TTL, rate limiting off, localhost URLs, debug logging. |
| `application-prod.yml` | Prod overrides: `ddl-auto: validate`, 15-min JWT TTL, rate limiting on, real domain URLs, `require-proxy: true`. |

A value put in base applies everywhere unless a profile overrides it.

## Secrets are env vars

Every credential is referenced as `${ENV_VAR}` in `application.yml`:

```yaml
spring:
  datasource: { url: jdbc:postgresql://...supabase.co:5432/postgres, username: postgres, password: ${POSTGRES_PASSWORD} }
  data: { redis: { host: "...redislabs.com", password: ${REDIS_PASSWORD}, port: 15401 } }
  rabbitmq: { host: ...cloudamqp.com, password: ${RABBITMQ_PASSWORD}, ssl: { enabled: true } }
  mail: { password: ${MAILGUN_PASSWORD} }
jwt:        { secret: ${JWT_SECRET_BASE64} }
supabase:   { service-key: ${SUPABASE_SERVICE_KEY} }
```

Required at runtime: `POSTGRES_PASSWORD`, `REDIS_PASSWORD`, `RABBITMQ_PASSWORD`,
`MAILGUN_PASSWORD`, `JWT_SECRET_BASE64`, `SUPABASE_SERVICE_KEY`. Firebase creds are a classpath
JSON, not an env var (see [[firebase-push]]). Generate the JWT secret with
`openssl rand -base64 64`.

**Never** hardcode a secret or commit one. Add a new secret as `${NEW_VAR}` and document it in
the deploy environment — see [[deployment]].

## Key per-environment differences

| Setting                          | dev                  | prod                  | Why |
|----------------------------------|----------------------|-----------------------|-----|
| `spring.jpa.hibernate.ddl-auto`  | `update`             | `validate`            | dev auto-evolves schema; prod fails fast on drift. See [[jpa-persistence]]. |
| `jwt.expiration-minutes`         | `1000`               | `15`                  | convenient locally; short-lived in prod. See [[security-and-auth]]. |
| `chirp.rate-limit.ip.apply-limit`| `false`              | `true`                | don't throttle local testing. See [[rate-limiting]]. |
| `nginx.require-proxy`            | `false`              | `true`                | prod requires requests via the trusted proxy. |
| `chirp.email.url` / websocket origin | `http://localhost:8080` | real domain     | link building + WS origin allowlist. |

## App-specific config namespace (`chirp.*`)

Custom settings live under the `chirp.` prefix and are injected with `@param:Value`:

```yaml
chirp:
  email: { from: "mail@chirp.com", verification: { expiry-hours: 24 }, reset-password: { expiry-minutes: 30 } }
  rate-limit: { ip: { apply-limit: true } }
  web-socket: { allowed-origin: "https://<host>" }
```

```kotlin
class EmailService(@param:Value("\${chirp.email.from}") private val emailFrom: String)
```

Use the `param:` use-site target in constructors (see [[kotlin-spring-conventions]]). For a
structured/proxy config, this project also uses dedicated config holders (e.g. `NginxConfig`
binding `nginx.trusted-ips`).

## Notable base settings to preserve

- **Hikari pool** tuned under `spring.datasource.hikari` (pool size, timeouts, prepared-statement
  cache) — keep when changing the datasource.
- **JPA**: `show_sql`/`format_sql` on for visibility; Postgres dialect.
- **RabbitMQ listener** retry/concurrency under `spring.rabbitmq.listener.simple` (see
  [[rabbitmq-events]]).
- **Redis** connect/timeout values for the Lettuce client (see [[caching-redis]]).

## Adding configuration

1. Add the value to `application.yml` (base) and override per profile if it differs by environment.
2. Reference it: `@param:Value("\${your.key}")` (or a `@ConfigurationProperties`/config-holder
   bean for groups).
3. Secret? Use `${ENV_VAR}` and add the var to the deploy environment, never a literal.

## Common mistakes

- Hardcoding a secret instead of `${ENV_VAR}`.
- Putting an environment-specific value in base `application.yml` so it leaks across profiles.
- `ddl-auto: update` in prod (the project uses `validate` — pair with manual schema management
  since there's no Flyway/Liquibase).
- Forgetting `--spring.profiles.active` at launch (no profile = base only).
- `@Value` without the `param:` target in a constructor.
