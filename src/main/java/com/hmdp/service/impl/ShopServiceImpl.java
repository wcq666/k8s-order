package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import io.netty.util.internal.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Resource
    private RedisClient redisClient;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR=Executors.newFixedThreadPool(10);

    @Override
    public Result queryById(Long id) {
        /**
         * //1.从redis查询商铺缓存
         *         String shopJson = redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
         *         //2.判断是否存在
         *         if (StrUtil.isNotBlank(shopJson)) {
         *             //3.存在，直接返回
         *             Shop shop = JSONUtil.toBean(shopJson, Shop.class);
         *             return Result.ok(shop);
         *         }
         *         //命中的是否是空值
         *         if (shopJson!=null){
         *             return Result.fail("商铺id不存在");
         *         }
         *         //4.不存在，根据id查询数据库
         *         Shop shop = getById(id);
         *         //5.数据库，不存在，返回错误
         *         if(shop==null){
         *             redisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
         *             return Result.fail("店铺不存在");
         *         }
         *         //6.存在，写入redis
         *         redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
         */

        //缓存穿透
        //Shop shop = queryWithPassThrough(id);
        //互斥锁结局缓存穿透
        Shop shop = redisClient.queryWithLogicalExpire(CACHE_SHOP_KEY,id, Shop.class,this::getById, CACHE_SHOP_TTL,TimeUnit.MINUTES);
        if(shop==null){
            return Result.fail("店铺不存在");
        }
        //7.返回
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id=shop.getId();
        if ( id==null ){
            //不存在，将控空值写入redis
            return Result.fail("店铺id不能为空");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        redisTemplate.delete(CACHE_SHOP_KEY+shop.getId());
        //返回结果
        return Result.ok();
    }

    @Override
    public Result queryShopByTypeId(Integer typeId, Integer current, Double x, Double y) {
        //是否需要根据坐标查询
        if(x==null || y==null){
            //不需要坐标查询，按数据库查询
            Page<Shop> page = query().eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        //计算分页参数
        int from=(current-1)*SystemConstants.DEFAULT_PAGE_SIZE;
        int end=current*SystemConstants.DEFAULT_PAGE_SIZE;
        //查询redis，按照距离排序，分页.结果：shopId,distance
        String key="shop:geo:"+typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo()
                .search(key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));

        //解析id查询shop
        if (results==null){
            return Result.ok();
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size()<from){
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids=new ArrayList<>(list.size());
        Map<String, Distance> distanceMap=new HashMap<>(list.size());
        //截取from~end的部分
        list.stream().skip(from).forEach(result->{
            //获取店铺id
            String shopId = result.getContent().getName();
            ids.add(Long.valueOf(shopId));
            //获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopId,distance);
        });
        //根据id查shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("order by field(id," + idStr + ")").list();
        for(Shop shop : shops){
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        //返回
        return Result.ok(shops);
    }

    public Shop queryWithMutex(Long id){
        //1.从redis查询商铺缓存
        String shopJson = redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //3.存在，直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //命中的是否是空值
        if (shopJson!=null){
            return null;
        }
        //实现缓存重建
        //获取互斥锁
        String lockKey="lock:shop:"+id;
        Shop shop=null;
        try{
            boolean isLock = tryLock(lockKey);
            //判断获取是否成功
            if ( ! isLock ) {
                //如果失败。则休眠并重试
                Thread.sleep(1000);
                return queryWithMutex(id);
            }

            //成功，根据id查询数据库
            shop = getById(id);
            //模拟重建延时
            Thread.sleep(200);
            //5.数据库，不存在，返回错误
            if(shop==null){
                redisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            //6.存在，写入redis
            redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        }catch (InterruptedException e){
            throw new RuntimeException();
        }finally {
            //释放互斥锁
            unlock(lockKey);
        }
        //7.返回
        return shop;
    }

    public Shop queryWithPassThrough(Long id){
        //1.从redis查询商铺缓存
        String shopJson = redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //3.存在，直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //命中的是否是空值
        if (shopJson!=null){
            return null;
        }
        //4.不存在，根据id查询数据库
        Shop shop = getById(id);
        //5.数据库，不存在，返回错误
        if(shop==null){
            redisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //6.存在，写入redis
        redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //7.返回
        return shop;
    }

    public Shop queryWithLogicalExpire(Long id){
        //1.从redis查询商铺缓存
        String shopJson = redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2.判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            //未命中
            return null;
        }
        //命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())){
            //未过期，直接返回店铺信息
            return shop;
        }
        //已过期，需要缓存重建
        //缓存重建
        //获取互斥锁
        String lockKey="lock:shop"+id;
        boolean isLock = tryLock(lockKey);
        //判断是否获取锁成功
        if (isLock){
            CACHE_REBUILD_EXECUTOR.submit( ()->{
                //重建缓存
                try {
                    this.saveShop2Redis(id,30L);
                }catch (RuntimeException | InterruptedException e){
                    throw  new RuntimeException();
                }finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }
        //返回过期店铺信息
        return shop;
    }

    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
        //查询店铺信息
        Shop byId = getById(id);
        Thread.sleep(200);
        //封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(byId);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //写入redis
        redisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }
    private boolean tryLock(String key){
        Boolean flag=redisTemplate.opsForValue().setIfAbsent(key,"1",10, SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key){
        redisTemplate.delete(key);
    }


}
