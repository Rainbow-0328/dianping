package com.dp.filter;

import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import java.io.IOException;

import static com.dp.contants.RedisConstants.MALICE_KEY;

/**
 * @Author zhou
 * @Description //
 * @Date 2023/8/31
 */
//@Component
//@WebFilter("/code")//过滤路径
//public class MyFilter implements Filter {
//
//    @Autowired
//    private StringRedisTemplate stringRedisTemplate;
//
//    @Override
//    public void init(FilterConfig filterConfig) throws ServletException {
//
//        System.out.println("Filter 前置");
//    }
//
//    @Override
//    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
//        String remoteAddr = servletRequest.getRemoteAddr();
//        String loginCount = stringRedisTemplate.opsForValue().get(MALICE_KEY + remoteAddr);
//        if(StrUtil.isBlank(loginCount)){
//             stringRedisTemplate.opsForValue().set(MALICE_KEY + remoteAddr , "1");
//        }
//        if (Integer.parseInt(loginCount) >= 3){
//            // 模拟发送消息,用户频繁登录,账户锁定
//            // 返回统一的异常
//        }
//        System.out.println("Filter 处理中");
//        filterChain.doFilter(servletRequest, servletResponse);
//    }
//
//    @Override
//    public void destroy() {
//
//        System.out.println("Filter 后置");
//    }
//}
