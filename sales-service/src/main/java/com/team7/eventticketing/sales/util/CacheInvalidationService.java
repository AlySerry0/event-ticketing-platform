package com.team7.eventticketing.sales.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class CacheInvalidationService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    public void invalidateCacheWildcard(String pattern) {
        redisTemplate.execute((org.springframework.data.redis.connection.RedisConnection connection) -> {
            try (org.springframework.data.redis.core.Cursor<byte[]> cursor = connection.keyCommands().scan(ScanOptions.scanOptions().match(pattern).count(100).build())) {
                while (cursor.hasNext()) {
                    connection.keyCommands().del(cursor.next());
                }
            } catch (Exception e) {
                // Ignore exception
            }
            return null;
        });
    }
}
