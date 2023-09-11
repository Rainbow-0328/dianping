package com.dp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @Author zhou
 * @Description // redis的生成唯一id
 * @Date 2023/9/1
 */
@Component
public class RedisIdWorker {


	private StringRedisTemplate stringRedisTemplate;

	public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
		this.stringRedisTemplate = stringRedisTemplate;
	}

	private static final long BEGIN_TIMESTAMP = 1640995200L;
	/**
	 * 序列号的位数
	 */
	private static final int COUNT_BITS = 32;
	public long nextId(String keyPrefix){
		// 1. 生成时间戳 从开始时间减去当前时间得到的时间
		// 获取当前的时间
		LocalDateTime now = LocalDateTime.now();
		// 将获取到的当前时间.转为秒数
		long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
		long timestamp = nowSecond - BEGIN_TIMESTAMP;
		// 2. 生成序列号
		// 2.1 获取当前的日期  一方面可以减小全局id不够用的情况  另一方面还有利于查询
		String date =  now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
		// 2.2 自增长
		long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
		// 3. 拼接并返回
		return timestamp << COUNT_BITS | count;
	}
}
