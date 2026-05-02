package com.team7.eventticketing.event.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Value("${spring.application.name}")
    private String serviceName;

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer serializer = buildJsonSerializer();

        RedisCacheConfiguration defaultConfig = baseConfig(Duration.ofMinutes(10), serializer);

        Map<String, RedisCacheConfiguration> caches = new HashMap<>();
        // --- CRUD entity detail caches: 15 min ---
        caches.put("event",         baseConfig(Duration.ofMinutes(15), serializer));
        caches.put("event-session", baseConfig(Duration.ofMinutes(15), serializer));

        // --- Feature caches ---

        // F1  search results:          5 min
        caches.put("S2-F1", baseConfig(Duration.ofMinutes(5), serializer));

        // F3  revenue DTO:             10 min
        caches.put("S2-F3", baseConfig(Duration.ofMinutes(15), serializer));

        // F5  JSONB attribute filter:   5 min
        caches.put("S2-F5", baseConfig(Duration.ofMinutes(5), serializer));

        // F6  top-rated report DTO:    10 min
        caches.put("S2-F6", baseConfig(Duration.ofMinutes(15), serializer));

        // F9  unverified sessions DTO: 10 min
        caches.put("S2-F9", baseConfig(Duration.ofMinutes(15), serializer));

        // F10 full-text search (M2):    5 min
        caches.put("S2-F10", baseConfig(Duration.ofMinutes(5), serializer));

        // F12 event performance dashboard (M2): 10 min  ← the one you asked about
        caches.put("S2-F12", baseConfig(Duration.ofMinutes(15), serializer));


        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(caches)
                .build();
    }

    private RedisCacheConfiguration baseConfig(Duration ttl,
                                               GenericJackson2JsonRedisSerializer serializer) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .computePrefixWith(cacheName -> serviceName + "::" + cacheName + "::")
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(serializer));
    }

    private GenericJackson2JsonRedisSerializer buildJsonSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType(Object.class)
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * Graceful degradation per PDF §4.4.6 g.
     * If Redis is unreachable, log and continue — Spring falls back to invoking the
     * underlying method (DB) so the request still returns correct data.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Redis GET failed (cache={}, key={}): {}", cache.getName(), key, ex.getMessage());
            }
            @Override
            public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
                log.warn("Redis PUT failed (cache={}, key={}): {}", cache.getName(), key, ex.getMessage());
            }
            @Override
            public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Redis EVICT failed (cache={}, key={}): {}", cache.getName(), key, ex.getMessage());
            }
            @Override
            public void handleCacheClearError(RuntimeException ex, Cache cache) {
                log.warn("Redis CLEAR failed (cache={}): {}", cache.getName(), ex.getMessage());
            }
        };
    }
}