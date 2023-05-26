package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Resource
    private IFollowService iFollowService;
    @Override
    public Result queryById(Long id) {
        //查询blog
        Blog blog = getById(id);
        if (blog==null){
            return Result.fail("笔记不存在！");
        }
        queryBlogUser(blog);
        //查询blog是否被点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        //获取当前用户id,如果可以点赞
        UserDTO user = UserHolder.getUser();
        if (user==null){
            //用户未登录。不能点赞
            return;
        }
        //创建缓存key
        Long userId =user.getId();
        String key="blog:like:"+blog.getId();
        //判断当前用户是否已点赞
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score!=null);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        //获取当前用户id,如果可以点赞
        Long userId = UserHolder.getUser().getId();
        //创建缓存key
        String key="blog:like:"+id;
        //判断当前用户是否已点赞
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score==null){
            //如果未点赞，可以点赞，点赞数加1
            boolean success = update().setSql("liked=liked+1").eq("id", id).update();
            if (success){
                //保存点赞数到数据库
                stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            }
        }else{
            //如果已点赞，取消点赞，点赞数减1
            boolean success = update().setSql("liked=liked-1").eq("id", id).update();
            if (success){
                //把用户从redis缓存中移除
                stringRedisTemplate.opsForZSet().remove(key,userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryByIdLikes(Long id) {
        String key="blog:like:"+id;
        //查询top5点赞用户zrange key 0 4
        Set<String> ids = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (ids==null || ids.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //解析其中的用户id
        List<Long> userIds = ids.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", userIds);
        //根据用户id查询用户
        List<UserDTO> userDTOS =
                userService.query().in("id",ids).last("Order By field(id,"+idStr+")").list()
                        .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        //返回

        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 1.获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 2.保存探店博文
        boolean isSuccess = save(blog);
        if (!isSuccess){
            return Result.fail("新增笔记失败！");
        }
        //查询笔记作者的所有粉丝
        List<Follow> follows = iFollowService.query().eq("follow_user_id", user.getId()).list();
        //推送笔记给所有的粉丝
        for (Follow follow : follows) {
            //获取粉丝id
            Long userId = follow.getUserId();
            //推送
            String key="feed:"+userId;
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }
        // 3.返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //收件箱当前用户
        Long userId = UserHolder.getUser().getId();

        //查询收件箱zrevrangebyscore key max min limit offset count
        String key="feed:"+userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        //解析数据：blogId，minTime(时间戳)，offset
        List<Long> ids = new ArrayList<>(typedTuples.size());
        if (typedTuples==null || typedTuples.isEmpty()){
            return Result.ok();
        }
        long minTime=0;
        int os=1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            //获取blogid
            String idsStr = typedTuple.getValue();
            ids.add(Long.valueOf(idsStr));
            //获取分数（时间戳）
            long time = typedTuple.getScore().longValue();
            if (time==minTime){
                os++;
            }else{
                minTime=time;
                os=1;
            }
        }
        //根据blogId,查询blog封装返回
        String str = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("order By field(id," + str + ")").list();
        for (Blog blog : blogs) {
            //查询blog
            queryBlogUser(blog);
            //查询blog是否被点赞
            isBlogLiked(blog);
        }
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }

    public void queryBlogUser(Blog blog){
        //查询blog有关的用户
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
