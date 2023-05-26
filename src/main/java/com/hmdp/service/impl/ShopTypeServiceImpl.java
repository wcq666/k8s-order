package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOPTYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public Result queryTypeList() {
        //从redis中查询商铺类型缓存
        String shopType = redisTemplate.opsForValue().get(RedisConstants.CACHE_SHOPTYPE_KEY + "list");
        //判断是否存在
        if (StrUtil.isNotBlank(shopType)){
            //存在，返回数据
            List<ShopType> shopTypes = JSONUtil.toList(shopType, ShopType.class);
            return Result.ok(shopTypes);
        }
        //不存在，根据数据库查询结果
        List<ShopType> list = query().orderByAsc("sort").list();
        //不存在，返回错误信息
        if (list==null){
            return Result.fail("商户类型不存在！");
        }
        //存在，写入redis
        redisTemplate.opsForValue().set(RedisConstants.CACHE_SHOPTYPE_KEY + "list",JSONUtil.toJsonStr(list));
        return Result.ok(list);
    }
}
