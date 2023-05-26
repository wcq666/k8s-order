package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if (RegexUtils.isPhoneInvalid(phone)){
            //如果不符合，返回错误
            return Result.fail("手机号格式不正确！");
        }
        //符合生成验证码
        String code = RandomUtil.randomNumbers(6);
        //保存验证码到redis
        redisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY+phone,code,RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //发送验证码
        log.debug("发送短信验证码成功，验证码: {}",code);
        //返回ok
        return Result.ok(code);
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号
        String phone=loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)){
            //不符合，返回错误信息
            return Result.fail("手机号格式错误！");
        }
        //从redis获取校验验证码
        String cacheCode= redisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY+phone);
        String code = loginForm.getCode();
        if ( cacheCode == null || !cacheCode.equals(code)){
            //不一致，报错
            return Result.fail("验证码不正确！");
        }
        //一致，根据手机号查询用户，select * from t_user where phone=?
        User user = query().eq("phone", phone).one();
        //判断用户是否存在
        if ( user == null ){
            //不存在创建用户保存
           user= createUserWithPhone(phone);
        }
        //保存用户信息到redis中
        //7.1随机生成token作为登录令牌
        UUID token = UUID.randomUUID();
        //7.2将user对象转换为hash存储
        Map<String, Object> stringObjectMap = BeanUtil.beanToMap(user,new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fieldName,filedValue)->filedValue.toString()));
        //7.3存储
        redisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY+token,stringObjectMap);
        //设置有效期
        redisTemplate.expire(RedisConstants.LOGIN_USER_KEY+token,RedisConstants.LOGIN_USER_TTL,TimeUnit.SECONDS);
        log.debug("生成的token ：{}",token);
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        //获取当前用户
        UserDTO user = UserHolder.getUser();
        //获取当前时间
        LocalDateTime now = LocalDateTime.now();
        //拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key=USER_SIGN_KEY+user.getId()+keySuffix;
        //获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //写入redis setbit key offset 1
        redisTemplate.opsForValue().setBit(key,dayOfMonth-1,true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        //获取当前用户
        UserDTO user = UserHolder.getUser();
        //获取当月天数
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        int dayOfMonth = now.getDayOfMonth();
        //拼接key
        String key=USER_SIGN_KEY+user.getId()+keySuffix;
        System.out.println(key+"  :  "+dayOfMonth);
        //获取本月截止今天为止的所有的签到记录，返回的是一个10进制的数值bitfield sign：1010：202304 get u4 0
        List<Long> longList = redisTemplate.opsForValue().bitField(
                key, BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if (longList==null || longList.isEmpty()){
            //没有任何结果
            Result.ok(0);
        }
        Long num = longList.get(0);
        System.out.println("num = " + num);
        if (num==null || num==0){
        return Result.ok(0);
        }
        //循环遍历
        int count =0;
        while (true){
            //让这个数与1做与运算，得到数字的最后一个bit位 // 判断这个bit位是否为零
            if ((num&1)==0){
                //如果为0，说明未签到，结束
                break;
            }else {
                //如果不为0，说明已签到，计数器加1
                count++;
            }
            //把数字右移一位，抛弃最后一位bit位，继续下一个bit位
            num>>>=1;
        }
        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
        //创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomNumbers(10));
        //保存用户
        save(user);
        return user;
    }
}
