package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.IdProducers;
import com.hmdp.utils.RedisClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {

    private ExecutorService executorService=Executors.newFixedThreadPool(500);

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ShopServiceImpl shopService;

    @Autowired
    private RedisClient redisClient;

    @Resource
    private IdProducers idProducers;

    @Test
    public void testHyperLogLog(){
        String[] values=new String[1000];
        int j=0;
        for (int i = 0; i < 1000000; i++) {
            j=i%1000;
            values[j]="user:"+i;
            if (j==999){
                //发送redis
                stringRedisTemplate.opsForHyperLogLog().add("hl2",values);
            }
        }
        //统计数量
        Long count = stringRedisTemplate.opsForHyperLogLog().size("hl2");
        System.out.println("count = " + count);
    }

    @Test
    public void TestTimeWorker() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(300);

        Runnable r=new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 100; i++) {
                    long order = redisIdWorker.nextId("order");
                    System.out.println("order = " + order);
                }
                countDownLatch.countDown();
            }
        };

        long start=System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            executorService.submit(r);
        }
        countDownLatch.await();
        long end = System.currentTimeMillis();
        System.out.println("总用时为："+(end-start));
    }

    @Test
    void testSaveShop() throws InterruptedException {

        Shop shop = shopService.getById(1L);
        redisClient.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY+1l,shop,10L, TimeUnit.SECONDS);
    }

    ExecutorService executorService1=Executors.newFixedThreadPool(500);

    @Test
    void testIds() throws InterruptedException {

        CountDownLatch countDownLatch=new CountDownLatch(500);
        Thread task = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 100; i++) {
                    Long orderid = idProducers.getIds("order");
                    System.out.println("当前线程是： "+Thread.currentThread().getId()+"生成的orderid = " + orderid);
                }
                countDownLatch.countDown();
            }
        });

        for (int i = 0; i < 300; i++) {
            executorService1.submit(task);
        }
        countDownLatch.await();
        
    }

    @Test
    public void loadShopData(){
        //查询店铺信息
        List<Shop> list = shopService.list();
        //把店铺信息按照typeId分组，typeId一致的放到一个集合
        Map<Long, List<Shop>> shops = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //分批写入redis中
        for (Map.Entry<Long, List<Shop>> shop : shops.entrySet()) {
            //获取类型id
            Long typeId = shop.getKey();
            String key="shop:geo:"+typeId;
            //获取同类型的店铺集合
            List<Shop> shopList = shop.getValue();
            ArrayList<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(shopList.size());
                      //写入redis GEOADD key 经度纬度 member
            for (Shop shopv : shopList){
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shopv.getId().toString(),
                        new Point(shopv.getX(),shopv.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key,locations);
        }
    }
}
