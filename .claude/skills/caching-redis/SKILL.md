---
name: caching-redis
description: Use when adding or changing Redis caching in this backend — @Cacheable/@CacheEvict on a method, the RedisCacheManager/TTL setup, cache key/condition design, evicting a cache from within the same bean, or the secure cache value serializer.
---

# Caching (Redis)

## Overview

Annotation-driven Spring Cache backed by Redis (Lettuce, non-blocking). A `RedisCacheManager`
with per-cache TTLs and a **secure polymorphic Jackson serializer**; caches are
**transaction-aware** so nothing is written until the DB transaction commits. The live example
is caching the first page of a chat's messages.

(Redis is also used directly, not via the cache abstraction, for rate limiting — see
[[rate-limiting]].)

## RedisConfig (app module)

```kotlin
@Configuration
@EnableCaching
class RedisConfig {
    @Bean
    fun cacheManager(connectionFactory: LettuceConnectionFactory): RedisCacheManager {
        val polymorphicTypeValidator = BasicPolymorphicTypeValidator.builder()
            .allowIfSubType("java.util.")
            .allowIfSubType("kotlin.collections.")
            .allowIfSubType("com.project.chirp.")          // whitelist app types only
            .build()
        val objectMapper = JsonMapper.builder()
            .addModule(kotlinModule())
            .polymorphicTypeValidator(polymorphicTypeValidator)
            .activateDefaultTyping(polymorphicTypeValidator, DefaultTyping.NON_FINAL)
            .build()
        val cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1L))                // default TTL
            .serializeValuesWith(SerializationPair.fromSerializer(GenericJacksonJsonRedisSerializer(objectMapper)))
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(cacheConfig)
            .withCacheConfiguration("messages", cacheConfig.entryTtl(Duration.ofMinutes(30)))  // per-cache TTL
            .transactionAware()                            // write to cache only after commit
            .build()
    }
}
```

Things to keep when adding caches:
- **Per-cache TTL** via `withCacheConfiguration("<name>", config.entryTtl(...))`. Default 1h;
  the `messages` cache is 30m ("as short as possible, as long as necessary").
- **`transactionAware()`** — cache writes happen only after the surrounding DB transaction
  commits, so you never cache rolled-back data.
- **Secure serializer** — the polymorphic type validator whitelists only `java.util.`,
  `kotlin.collections.`, and `com.project.chirp.` types (Jackson 3, `tools.jackson.*`). Don't
  broaden it; cached values are deserialized with default typing, which is a gadget risk if
  opened up.

## Caching a read (@Cacheable)

```kotlin
@Cacheable(
    value = ["messages"],
    key = "#chatId",
    condition = "#before == null && #pageSize <= 50",   // only cache the first page
    sync = true,                                          // one loader; concurrent callers wait
)
fun getChatMessages(chatId: ChatId, before: Instant?, pageSize: Int): List<ChatMessageDto> { ... }
```

- **Cache the DTO, not the entity** — `getChatMessages` returns `List<ChatMessageDto>`. Caching
  the lighter wire type keeps entries small and avoids lazy-loading surprises on deserialization.
  See [[domain-modeling]].
- **`condition`** restricts what's cached — here only the first page (no cursor, small page),
  because that's the hot read; deeper pages always hit the DB.
- **`sync = true`** collapses a thundering herd: the first caller populates the cache while
  others wait, instead of all hitting the DB.
- **`key`** is a SpEL expression over the params (`#chatId`).

## Evicting (@CacheEvict)

Evict when the underlying data changes so the next read repopulates:

```kotlin
@Transactional
@CacheEvict(value = ["messages"], key = "#chatId")
fun sendMessage(chatId: ChatId, ...): ChatMessage { ... }
```

## The self-invocation gotcha (MessageCacheEvictionHelper)

Spring's cache annotations work via proxies, so **calling an annotated method from another method
of the same bean bypasses the annotation**. When a class needs to evict its own cache from a
method that isn't itself the cache-annotated one, the eviction is delegated to a tiny separate
bean:

```kotlin
@Component
class MessageCacheEvictionHelper {
    @CacheEvict(value = ["messages"], key = "#chatId")
    fun evictMessagesCache(chatId: ChatId) { /* NO-OP: Spring performs the evict */ }
}
```

`ChatMessageService.deleteMessage` calls `messageCacheEvictionHelper.evictMessagesCache(chatId)`
— an external bean call, so the proxy fires. Use this pattern whenever you must trigger
`@Cacheable`/`@CacheEvict` from within the same class.

## Adding a new cache

1. Pick a cache name; give it a TTL in `RedisConfig` via `withCacheConfiguration(name, ...)`
   (or accept the 1h default).
2. `@Cacheable(value=["name"], key="#id", condition=..., sync=true)` on the read; cache a DTO.
3. `@CacheEvict(value=["name"], key="#id")` on every write that invalidates it.
4. If you must evict from inside the same bean, add/extend a `*CacheEvictionHelper`.
5. Ensure the module has `spring-boot-starter-data-redis`.

## Common mistakes

- Caching a JPA entity instead of a DTO (heavy, lazy-loading hazards on deserialize).
- Self-invoking a `@Cacheable`/`@CacheEvict` method from the same bean — silently no-ops. Use the
  helper-bean pattern.
- Forgetting `@CacheEvict` on a write path → stale reads.
- Widening the polymorphic type whitelist — security risk with default typing.
- Caching every page instead of restricting with `condition` to the hot first page.
- Omitting `transactionAware()` and caching data that later rolls back.
