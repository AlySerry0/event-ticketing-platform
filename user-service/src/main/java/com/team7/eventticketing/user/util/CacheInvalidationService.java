package com.team7.eventticketing.user.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

@Service
public class CacheInvalidationService {

    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationService.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    public void invalidateCacheWildcard(String pattern) {
        try {
            Set<String> keysToDelete = new HashSet<>();
            redisTemplate.execute((org.springframework.data.redis.connection.RedisConnection connection) -> {
                try (org.springframework.data.redis.core.Cursor<byte[]> cursor = connection.keyCommands().scan(ScanOptions.scanOptions().match(pattern).count(100).build())) {
                    while (cursor.hasNext()) {
                        keysToDelete.add(new String(cursor.next(), StandardCharsets.UTF_8));
                    }
                }
                return null;
            });
            if (!keysToDelete.isEmpty()) {
                redisTemplate.delete(keysToDelete);
            }
        } catch (Exception e) {
            log.warn("Redis cache invalidation failed for pattern: {}", pattern, e);
        }
    }
}
