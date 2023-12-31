package com.dp.interceptor;

import com.dp.dto.UserDTO;
import com.dp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @Author zhou
 * @Description // 登录拦截器
 * @Date 2023/8/30
 */
//@Component
public class LoginInterceptor implements HandlerInterceptor {

	private StringRedisTemplate stringRedisTemplate;

	public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
		this.stringRedisTemplate = stringRedisTemplate;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		// 1.判断是否需要拦截（ThreadLocal中是否有用户）
		if (UserHolder.getUser() == null) {
			// 没有，需要拦截，设置状态码
			response.setStatus(401);
			// 拦截
			return false;
		}
		// 有用户，则放行
		return true;
	}
}
