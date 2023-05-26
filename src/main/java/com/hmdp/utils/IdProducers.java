package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class IdProducers {

    @Autowired
    StringRedisTemplate redisTemplate;

    public  Long getIds(String prefix){
        long start=1640995200L;
        long now = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        long ZONE_COUNT=now-start;

        String format = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        long count = redisTemplate.opsForValue().increment("inrc:" + prefix + ":" + format);
        return ZONE_COUNT << 32 | count;
    }
}
