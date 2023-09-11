package com.dp.config;

import com.dp.interceptor.LoginInterceptor;
import com.dp.interceptor.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @Author zhou
 * @Description // 用于配置自定义的拦截器
 * @Date 2023/8/30
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {
	@Autowired
	private StringRedisTemplate stringRedisTemplate;
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new LoginInterceptor(stringRedisTemplate))
				.excludePathPatterns(
						"/user/login" ,
						"/user/code" ,
						"/shop/**" ,
						"/upload/**" ,
						"/voucher/**" ,
						"/shop-type/**" ,
						"/blog/hot" ,
						"/user/me"
				).order(1);
		registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).addPathPatterns("/**").order(0);
	}
}
