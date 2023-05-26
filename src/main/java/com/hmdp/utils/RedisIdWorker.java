package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final long BEGIN_TIMESTAMP = 1640995200L;
    private static final int COUNT_ITMESTAMP=32;
    public long nextId(String keyPrefix){
        //生成时间戳
        LocalDateTime now= LocalDateTime.now();
        long nowSeconds = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp=nowSeconds-BEGIN_TIMESTAMP;

        //生成序列号,获取当天日期，精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = redisTemplate.opsForValue().increment("incr" + keyPrefix + ":" + date);
        //凭拼接返回

        return timestamp << COUNT_ITMESTAMP | count;
    }
}
