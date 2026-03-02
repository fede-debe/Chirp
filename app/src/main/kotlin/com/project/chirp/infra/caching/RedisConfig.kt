package com.project.chirp.infra.caching

import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import tools.jackson.databind.DefaultTyping
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import tools.jackson.module.kotlin.kotlinModule
import java.time.Duration

/**
 * Configures the [CacheManager] to enable annotation-driven Redis caching.
 *
 * This implementation leverages Spring Boot's caching abstraction to store "real"
 * application data (e.g., chat messages) in Redis. This reduces latency by
 * bypassing the primary database for subsequent requests of the same data.
 *
 * ### Key Features:
 * * **Reduced Overhead:** Replaces manual cache logic with Spring annotations.
 * * **Faster Responses:** Ideal for frequently accessed data like the first page of chat history.
 * * **Redis Abstraction:** Uses Redis as the backing provider for the Spring Cache SPI.
 *
 * @see org.springframework.cache.CacheManager
 * @see org.springframework.cache.annotation.EnableCaching
 *
 * We need a serializer to serialize and deserialize objects to and from Redis.
 */
@Configuration
@EnableCaching
class RedisConfig {

    @Bean
    fun cacheManager(
        connectionFactory: LettuceConnectionFactory
    ): RedisCacheManager {
        /***
         * Configures the Redis cache manager with a custom serializer for polymorphic types.
         * It is important to create a custom validator instead of using the default one,
         * because it could be a security risk if not properly configured by receiving
         * data that is not meant for the backend.
         */
        val polymorphicTypeValidator = BasicPolymorphicTypeValidator.builder()
            .allowIfSubType("java.util.") // Allow Java lists
            .allowIfSubType("kotlin.collections.") // Kotlin collections
            .allowIfSubType("com.project.chirp.")
            .build()

        /***
         * Configures the Jackson object mapper with a custom validator for polymorphic types.
         * This ensures that only types allowed by the validator are deserialized.
         */
        val objectMapper = JsonMapper.builder()
            .addModule(kotlinModule())
            .polymorphicTypeValidator(polymorphicTypeValidator)
            .activateDefaultTyping(polymorphicTypeValidator, DefaultTyping.NON_FINAL)
            .build()

        /***
         * Configures the Redis cache configuration with a custom serializer for polymorphic types.
         * entryTtl sets the time-to-live for cache entries.
         * serializeValuesWith sets the serializer for cache values which uses the object mapper.
         */
        val cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1L))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    GenericJacksonJsonRedisSerializer(objectMapper)
                )
            )

        /***
         * Configures the Redis cache manager with a default cache configuration and a custom cache configuration for "messages".
         * The "messages" cache has a shorter TTL of 30 minutes. (as short as possible, as long as necessary)
         *
         * cacheDefaults sets the default configuration for all caches.
         * withCacheConfiguration sets a custom configuration for a specific cache.
         * transactionAware make sure only loaded messages are cached if database transaction was successful.
         */
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(cacheConfig)
            .withCacheConfiguration(
                "messages",
                cacheConfig.entryTtl(Duration.ofMinutes(30))
            )
            .transactionAware()
            .build()
    }
}