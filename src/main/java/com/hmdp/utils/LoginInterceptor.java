package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //判断用户是否需要拦截（threadLocal中是否存在用户）
        if(UserHolder.getUser()==null ){
            //没有用户，需要拦截
            response.setStatus(401);
            log.error("用户没有经过鉴权校验！");
            //拦截
            return false;
        }
        //有用户则放心
        return true;
    }
}
