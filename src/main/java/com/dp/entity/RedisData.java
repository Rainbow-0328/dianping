package com.dp.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author zhou
 * @Description // 附带过期时间的数据,在原来的实体的基础上,新增逻辑过期时间
 * @Date 2023/9/1
 */
@Data
public class RedisData {
	private LocalDateTime expireTime;
	private Object data;
}
