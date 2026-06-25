# Skills catalog

Project-local Claude skills documenting how this **Kotlin + Spring Boot modular-monolith
backend** is built. They're committed with the repo so they travel with every fork — use this
repo as a template, and these skills teach an agent to extend it the same way.

**Code is the source of truth.** These skills were extracted from the actual code; where the
root `STRUCTURE.md` blueprint disagrees (e.g. rate limiting), follow the skills and the code.

## Start here

**[chirp-architecture](chirp-architecture/SKILL.md)** is the hub — module layout, the
api/service/domain/infra layering, and the "add a new feature module end-to-end" walkthrough that
routes into every other skill. Pair it with **[kotlin-spring-conventions](kotlin-spring-conventions/SKILL.md)**,
the global ruleset.

## Catalog

| Skill | Topic |
|-------|-------|
| [chirp-architecture](chirp-architecture/SKILL.md) | Module boundaries, layering, new-module blueprint (hub) |
| [kotlin-spring-conventions](kotlin-spring-conventions/SKILL.md) | DI, bean stereotypes, KDoc, class vs data class, logging |
| [gradle-build-system](gradle-build-system/SKILL.md) | Convention plugins, version catalog, bootJar, CVE pins |
| [domain-modeling](domain-modeling/SKILL.md) | Entity ↔ model ↔ DTO, mappers, type-safe IDs, sealed types |
| [jpa-persistence](jpa-persistence/SKILL.md) | Entities, schemas, relations, repositories, transactions |
| [rest-api](rest-api/SKILL.md) | Controllers, DTO validation, requestUserId, exception handling |
| [rabbitmq-events](rabbitmq-events/SKILL.md) | Cross-module async events, exchanges/queues, listeners |
| [spring-application-events](spring-application-events/SKILL.md) | In-process events, `@TransactionalEventListener(AFTER_COMMIT)` |
| [security-and-auth](security-and-auth/SKILL.md) | JWT, Spring Security, refresh-token rotation, password hashing |
| [rate-limiting](rate-limiting/SKILL.md) | Per-IP + per-email throttling, Redis Lua scripts |
| [caching-redis](caching-redis/SKILL.md) | `@Cacheable`/`@CacheEvict`, TTLs, self-invocation eviction |
| [websocket-realtime](websocket-realtime/SKILL.md) | Raw WebSocket, session maps, JWT handshake, broadcasts |
| [supabase-storage](supabase-storage/SKILL.md) | Signed-URL direct uploads, RestClient bean |
| [firebase-push](firebase-push/SKILL.md) | FCM, platform config, failure classification, retries |
| [mailgun-email](mailgun-email/SKILL.md) | SMTP + Thymeleaf templates, event-driven sending |
| [configuration](configuration/SKILL.md) | `application.yml` profiles, env-var secrets, toggles |
| [deployment](deployment/SKILL.md) | GitHub Actions → rsync → systemd, credential injection |

## Note on testing

The repo has essentially no tests (only an empty context-load test), so there's no testing
convention to copy — the available test stack is noted in `gradle-build-system`. Treat
test-writing as greenfield.
