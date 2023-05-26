package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.xml.crypto.Data;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService iUserService;

    @Override
    public Result follow(Long followUserId, Boolean is) {
        //获取当前用户信息
        UserDTO user = UserHolder.getUser();
        String key="follows:"+user.getId();
        //判断当前用户是否关注
        if(is){
            //已关注
            Follow follow = new Follow();
            follow.setUserId(user.getId());
            follow.setFollowUserId(followUserId);
            follow.setCreateTime(LocalDateTime.now());
            boolean isSuccess = save(follow);
            if (isSuccess){
                //保存成功，存到缓存中
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());

            }
        }else {
            //未关注
            QueryWrapper<Follow> followQueryWrapper = new QueryWrapper<Follow>().eq("user_id", user.getId()).eq("follow_user_id", followUserId);
            boolean isSuccess = remove(followQueryWrapper);
            if (isSuccess){
                //将缓存中的数据删除
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryByUserId(Long followUserId) {
        //获取登录用户信息
        Long userId = UserHolder.getUser().getId();
        //查询当前用户所有的关注信息
        Integer count = query().eq("follow_user_id", followUserId).eq("user_id",userId).count();
        //判断
        return Result.ok(count>0);
    }

    @Override
    public Result commonsUsers(Long userId) {
        //获取当前用户信息
        Long uId = UserHolder.getUser().getId();
        //获取两个色图集合的交集
        String userKey="follows:"+uId;
        String userCurrent="follows:"+userId;
        //查询用户关注的人

        Set<String> commonsUsers = stringRedisTemplate.opsForSet().intersect(userKey, userCurrent);
        //是否存在共同用户
        if (commonsUsers==null || commonsUsers.isEmpty()){
            //不存在，返回空集合
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = commonsUsers.stream().map(Long::valueOf).collect(Collectors.toList());

        List<UserDTO> collect = iUserService.listByIds(ids).stream().map(user -> BeanUtil.copyProperties(user,UserDTO.class)).collect(Collectors.toList());
        return Result.ok(collect);
    }

}
