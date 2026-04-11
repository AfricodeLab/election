package com.brandedhustlers.electionbackend.common.config.cache;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RedisRateThrottlingService {

    private final RedisTemplate<String, String> redisTemplate;
    private final int maxRequests = 5;
    private final int timeWindowSeconds = 10;

    public RedisRateThrottlingService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isAllowed(String clientId){
        String key = "rate_limit:" + clientId;
        Long requestCount = redisTemplate.opsForValue().increment(key, 1);

        if (requestCount == 1){
            redisTemplate.expire(key, timeWindowSeconds, TimeUnit.SECONDS);
        }

        return requestCount <= maxRequests;
    }
}
