package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    private String name;
    private static final String KEY_PREFIX="lock:";

    private static final String ID_PREFIX= UUID.randomUUID().toString(true)+"-";
    private StringRedisTemplate redisTemplate;

    private static final DefaultRedisScript<Long> REDIS_SCRIPT;

    static {
        REDIS_SCRIPT=new DefaultRedisScript<>();
        REDIS_SCRIPT.setLocation(new ClassPathResource("unlock.lura"));
        REDIS_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate redisTemplate) {
        this.name = name;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        //获取线程的表示
        String threadId = ID_PREFIX+Thread.currentThread().getId();
        Boolean success=redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX+name,threadId+"",timeoutSec, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(success);
    }

    @Override
    public void unlock() {
        redisTemplate.execute(REDIS_SCRIPT, Collections.singletonList(KEY_PREFIX+name),ID_PREFIX+Thread.currentThread().getId());
//        //获取线程标识
//        String threadId = ID_PREFIX+Thread.currentThread().getId();
//        //获取锁中的标识
//        String lockFlag = redisTemplate.opsForValue().get(KEY_PREFIX + name);
//        //判断吧标志是否一致
//        if(threadId.equals(lockFlag)){
//            //释放锁
//            redisTemplate.delete(KEY_PREFIX+name);
//        }

    }
}
