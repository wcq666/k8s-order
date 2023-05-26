package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;
import static java.util.concurrent.TimeUnit.SECONDS;

@Component
@Slf4j
public class RedisClient {

    private final StringRedisTemplate redisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);

    public RedisClient(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit){
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        //设置逻辑过期
        RedisData redisData=new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallBack, Long time, TimeUnit unit){
        //1.从redis查询商铺缓存
        String shopJson = redisTemplate.opsForValue().get(keyPrefix + id);
        //2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //3.存在，直接返回
            R r = JSONUtil.toBean(shopJson, type);
            return r;
        }
        //命中的是否是空值r
        if (shopJson!=null){
            return null;
        }
        //4.不存在，根据id查询数据库
        R r = dbFallBack.apply(id);
        //5.数据库，不存在，返回错误
        if(r==null){
            redisTemplate.opsForValue().set(keyPrefix+id,"",time,unit);
            return null;
        }
        //6.存在，写入redis
        redisTemplate.opsForValue().set(keyPrefix + id,JSONUtil.toJsonStr(r),time, unit);
        //7.返回
        return r;
    }


    public <R,ID> R queryWithLogicalExpire(String keyPrefix,ID id,Class<R> type,Function<ID,R> dbCallBAck,Long time, TimeUnit unit){
        //1.从redis查询商铺缓存
        String shopJson = redisTemplate.opsForValue().get(keyPrefix + id);
        //2.判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            //未命中
            return null;
        }
        //命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())){
            //未过期，直接返回店铺信息
            return r;
        }
        //已过期，需要缓存重建
        //缓存重建
        //获取互斥锁
        String lockKey=LOCK_SHOP_KEY+id;
        boolean isLock = tryLock(lockKey);
        //判断是否获取锁成功
        if (isLock){
            CACHE_REBUILD_EXECUTOR.submit( ()->{
                //重建缓存
                try {
                    R apply = dbCallBAck.apply(id);
                    this.setWithLogicalExpire(keyPrefix + id,apply,time,unit);
                }catch (Exception e){
                    throw  new RuntimeException();
                }finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }
        //返回过期店铺信息
        return r;
    }

    private boolean tryLock(String key){
        Boolean flag=redisTemplate.opsForValue().setIfAbsent(key,"1",10, SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key){
        redisTemplate.delete(key);
    }

}
