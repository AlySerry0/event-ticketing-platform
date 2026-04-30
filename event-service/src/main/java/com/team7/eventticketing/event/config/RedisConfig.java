package com.team7.eventticketing.event.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
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
public class RedisConfig {

    @Value("${spring.application.name}")
    private String serviceName;

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        // Base config — shared serializer and key prefix, no default TTL yet
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .computePrefixWith(cacheName -> serviceName + "::" + cacheName + "::")
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();

        // --- CRUD entity detail caches: 15 min ---
        cacheConfigs.put("event",         base.entryTtl(Duration.ofMinutes(15)));
        cacheConfigs.put("event-session", base.entryTtl(Duration.ofMinutes(15)));

        // --- Feature caches ---

        // F1  search results:          5 min
        cacheConfigs.put("S2-F1", base.entryTtl(Duration.ofMinutes(5)));

        // F3  revenue DTO:             10 min
        cacheConfigs.put("S2-F3", base.entryTtl(Duration.ofMinutes(10)));

        // F5  JSONB attribute filter:   5 min
        cacheConfigs.put("S2-F5", base.entryTtl(Duration.ofMinutes(5)));

        // F6  top-rated report DTO:    10 min
        cacheConfigs.put("S2-F6", base.entryTtl(Duration.ofMinutes(10)));

        // F9  unverified sessions DTO: 10 min
        cacheConfigs.put("S2-F9", base.entryTtl(Duration.ofMinutes(10)));

        // F10 full-text search (M2):    5 min
        cacheConfigs.put("S2-F10", base.entryTtl(Duration.ofMinutes(5)));

        // F12 event performance dashboard (M2): 10 min  ← the one you asked about
        cacheConfigs.put("S2-F12", base.entryTtl(Duration.ofMinutes(10)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base.entryTtl(Duration.ofMinutes(10))) // fallback for anything not listed
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}