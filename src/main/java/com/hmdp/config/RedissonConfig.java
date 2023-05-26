package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(){
        Config config = new Config();
        config.useSingleServer().setAddress("redis://localhost:6379").setPassword("root");
        return Redisson.create(config);
    }
//    @Bean
//    public RedissonClient getRedissonClient1(){
//        Config config = new Config();
//        config.useSingleServer().setAddress("redis://localhost:7000").setPassword("root");
//        return Redisson.create(config);
//    }
//    @Bean
//    public RedissonClient getRedissonClient2(){
//        Config config = new Config();
//        config.useSingleServer().setAddress("redis://localhost:7001").setPassword("root");
//        return Redisson.create(config);
//    }
//    @Bean
//    public RedissonClient getRedissonClient3(){
//        Config config = new Config();
//        config.useSingleServer().setAddress("redis://localhost:7002").setPassword("root");
//        return Redisson.create(config);
//    }
//    @Bean
//    public RedissonClient getRedissonClient4(){
//        Config config = new Config();
//        config.useSingleServer().setAddress("redis://localhost:7003").setPassword("root");
//        return Redisson.create(config);
//    }
//    @Bean
//    public RedissonClient getRedissonClient5(){
//        Config config = new Config();
//        config.useSingleServer().setAddress("redis://localhost:7004").setPassword("root");
//        return Redisson.create(config);
//    }
//    @Bean
//    public RedissonClient getRedissonClient6(){
//        Config config = new Config();
//        config.useSingleServer().setAddress("redis://localhost:7005").setPassword("root");
//        return Redisson.create(config);
//    }
}
