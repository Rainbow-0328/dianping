package com.dp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.dp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.dp.contants.RedisConstants.*;
import static com.dp.contants.RedisConstants.CACHE_SHOP_KEY;

/**
 * @Author zhou
 * @Description // 缓存工具类
 * @Date 2023/9/1
 */
@Slf4j
@Component
public class CacheClient {

	private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    // 带TTL的过期时间
    public void set(String key , Object value , Long time , TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key , JSONUtil.toJsonStr(value) , time , unit);
    }
    // 逻辑过期的设置key
    public void setLogicalExpire(String key , Object value , Long time , TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key , JSONUtil.toJsonStr(value));
    }

    // 获取锁方法
    private boolean tryLock(String key){
        //  如果直接将其返回,可能会出现拆箱和装箱之间的问题
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key , "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    // 释放锁方法
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    // 预防缓存穿透的方法
    public <R , ID> R queryWithPassThrough(ID id , String keyPrefix , Class<R> type ,
                                           Function<ID , R> callback , Long time ,
                                           TimeUnit timeUnit){
        // 1. 从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(keyPrefix + id);
        // 2 . 判断是否存在
        if (StrUtil.isNotBlank(json)) { // 此处的判断如果为"",那么依然返回值是false,所以依然会进入到后续操作
            // 3. 存在直接返回    获取到商铺对象
            // 此处因为要将缓存穿透的解决方式进行封装,所以返回的是一个shop对象
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class); // 将返回的商品序列化为对应的商铺对象
            return JSONUtil.toBean(json, type); // 返回商品对象
        }
        // 判断获取到的值是否为空字符串
        if(json != null){  // 如果不进行判断,那么就会造成需要继续去访问数据库,依然对数据库造成压力
            // 返回错误信息 将原来的返回错误信息的操作更改为返回null
            return  null;
        }
        // 4. 不存在,根据ID查询数据库
        R r = callback.apply(id);
        if (r == null) {
            // 如果不存在,则将空值存储到redis中,用于规避缓存穿透
            stringRedisTemplate.opsForValue().set(keyPrefix + id , "" , time , timeUnit);
            // 5. 不存在直接返回错误 将原来的返回错误信息的操作更改为返回null
            return null;
        }
        // 6. 写入redis
        stringRedisTemplate.opsForValue().set(keyPrefix + id, JSONUtil.toJsonStr(r) , time , timeUnit);
        // 7. 返回
        return r;
    }

    // 通过线程池的方式进行热点key的重新构建,避免任务太多造成线程过多,导致服务宕机
    private final static ExecutorService HOT_KEY_REBUILD = Executors.newFixedThreadPool(10);
    //------------------基于逻辑过期解决缓存击穿问题-----------------------
    public <R , ID> R queryWithLogicalExpire(ID id , String keyPrefix , String lockPrefix ,
                                             Class<R> type , Function<ID , R> callback ,
                                             Long time , TimeUnit timeUnit) {
        // 1. 从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(keyPrefix + id);
        // 2 . 判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            if("111111".equals(shopJson)){
                return null;
            }
            // 如果是空,证明访问的是非热点key,以带TTL的方式进行缓存,解决缓存穿透
            R r = queryWithPassThrough(id , keyPrefix , type , callback , time , timeUnit);
            // 2.1 不存在,直接返回
            return r;
        }
        // 3. 存在,判断缓存是否过期
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        // 4. 未过期,直接返回
        if(redisData.getExpireTime().isAfter(LocalDateTime.now())){
            return r;
        }
        // 5. 过期,尝试获取互斥锁
        boolean flag = tryLock(lockPrefix + id);
        // 5.1 如果没有获取到锁,直接将旧数据返回
        if(!flag){
            return r;
        }
        // 6. 获取锁成功
        // 6.1 再次进行判断redis中的数据的逻辑过期是否已过期
        shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        redisData = JSONUtil.toBean(shopJson, RedisData.class);
        r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        // 6.2 未过期,直接返回
        if(redisData.getExpireTime().isAfter(LocalDateTime.now())){
            return r;
        }
        // 6.3 获取到锁之后,如果逻辑过期时间还是已经过期的,则开启独立线程
        HOT_KEY_REBUILD.submit(()-> {
            try {
                // (ID id , String keyPrefix,Function<ID , R> callback , Long time , TimeUnit timeUnit){
                this.saveShop2Redis(id , keyPrefix , callback , time , timeUnit);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }finally {
                unlock(lockPrefix + id);
            }
        });
        // 返回旧数据
        return r;
    }

    // ------------------------------提前数据预热-------------------------------
    // 将数据存入到redis中
    public <R , ID> void saveShop2Redis(ID id , String keyPrefix,
                                        Function<ID , R> callback ,
                                        Long time , TimeUnit timeUnit){
        // 1. 查询店铺数据
        R r = callback.apply(id);
        // 2. 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(r);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        // 3. 写入redis
        stringRedisTemplate.opsForValue().set(keyPrefix + id , JSONUtil.toJsonStr(redisData));
    }

}
