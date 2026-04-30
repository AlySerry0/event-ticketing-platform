package com.team7.eventticketing.ticket.config;

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
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .computePrefixWith(cacheName -> serviceName + "::" + cacheName + "::")
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();

        cacheConfigs.put("S4-F1",
                config.entryTtl(Duration.ofMinutes(5)));

        cacheConfigs.put("S4-F3",
                config.entryTtl(Duration.ofMinutes(10)));

        cacheConfigs.put("S4-F5",
                config.entryTtl(Duration.ofMinutes(5)));

        cacheConfigs.put("S4-F6",
                config.entryTtl(Duration.ofMinutes(10)));

        cacheConfigs.put("S4-F8",
                config.entryTtl(Duration.ofMinutes(15)));

        cacheConfigs.put("S4-F9",
                config.entryTtl(Duration.ofMinutes(10)));

        cacheConfigs.put("S4-F10",
                config.entryTtl(Duration.ofMinutes(10)));

        cacheConfigs.put("S4-F12",
                config.entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
