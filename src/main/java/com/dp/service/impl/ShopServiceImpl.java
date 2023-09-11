package com.dp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONString;
import cn.hutool.json.JSONUtil;
import com.dp.dto.Result;
import com.dp.entity.Shop;
import com.dp.mapper.ShopMapper;
import com.dp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.utils.CacheClient;
import com.dp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.dp.contants.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private CacheClient cacheClient;

    // ID id , String keyPrefix , String lockPrefix ,
    //                                             Class<R> type , Function<ID , R> callback ,
    //                                             Long time , TimeUnit timeUnit

    @Override
    public Result queryById(Long id) {
        Shop shop = cacheClient.queryWithLogicalExpire(id , CACHE_SHOP_KEY , LOCK_SHOP_KEY ,
                Shop.class , id2 -> getById(id2) , CACHE_SHOP_TTL , TimeUnit.MINUTES);
        if(shop == null){
            return Result.fail("商铺不存在");
        }
        // 7. 返回数据
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail("店铺ID不能为空");
        }
        // 1. 更新数据库
        updateById(shop);
        // 2. 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }





    // 初始版本的查询,未做缓存穿透和缓存击穿的解决方案
    public Result queryCacheById(Long id) {
        // 1. 从redis查询商铺缓存
        String shopCache = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        // 2. 判断是否存在
        if(StrUtil.isNotBlank(shopCache)){
            Shop shop = JSONUtil.toBean(shopCache, Shop.class, false);
            // 3. 存在,直接返回
            return Result.ok(shop);
        }
        // 如果查到的缓存是"" , 因为缓存过空字符串,所以就是防止缓存穿透的,直接返回即可
        if(shopCache != null){
            return Result.fail("店铺不存在");
        }
        Shop shop = getById(id);
        // 4. 不存在,根据id查询数据库
        if(shop == null){
            // 缓存空对象,用于防止缓存穿透,设置TTL为2分钟
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id , "" , CACHE_NULL_TTL , TimeUnit.MINUTES);
            // 5. 不存在,返回错误
            return Result.fail("商铺不存在");
        }
        // 6. 存在,写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id , JSONUtil.toJsonStr(shop) , CACHE_SHOP_TTL , TimeUnit.MINUTES);
        // 7. 返回数据
        return Result.ok(shop);
    }

    // 基于互斥锁的方式解决缓存击穿问题
    public Shop queryWithMutex(Long id){
        // 1. 从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        // --------------------------此处解决缓存穿透的问题----------------------------------
        // 2 . 判断是否存在
        if (StrUtil.isNotBlank(shopJson)) { // 此处的判断如果为"",那么依然返回值是false,所以依然会进入到后续操作
            // 3. 存在直接返回    获取到商铺对象
            // 此处因为要将缓存穿透的解决方式进行封装,所以返回的是一个shop对象
            return JSONUtil.toBean(shopJson, Shop.class); // 返回商品对象
        }
        // 判断获取到的值是否为空字符串
        if(shopJson != null){  // 如果不进行判断,那么就会造成需要继续去访问数据库,依然对数据库造成压力
            // 返回错误信息 将原来的返回错误信息的操作更改为返回null
            return  null;
        }

        // -------------------------此处开始解决缓存穿透问题-----------------------
        // 4. 实现缓存重建
        // 4.1 获取互斥锁
        Shop shop = null;
        try {
            boolean flag = tryLock(LOCK_SHOP_KEY + id);
            if (!flag) {
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            //----------------------获取到锁之后进行二次检查------------------------
            shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
            // 进行二次检查 , 如果获取到锁之后,再查询一次redis中是否存在数据,如果已经有了,则可以直接返回
            if (StrUtil.isNotBlank(shopJson)) {  // 如果是非空直接进行返回
                // 3. 存在直接返回    获取到商铺对象
                // 此处因为要将缓存穿透的解决方式进行封装,所以返回的是一个shop对象
                return JSONUtil.toBean(shopJson, Shop.class); // 返回商品对象
            }
            // 判断获取到的值是否为空字符串
            if (shopJson != null) {  // 如果不进行判断,那么就会造成需要继续去访问数据库,依然对数据库造成压力
                // 返回错误信息 将原来的返回错误信息的操作更改为返回null
                return null;
            }

            //----------------------当检查到还是不存在时,再进行热点key重建------------------------
            // 4. 不存在,根据ID查询数据库
            shop = getById(id);
            if (shop == null) {
                // 如果不存在,则将空值存储到redis中,用于规避缓存穿透
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                // 5. 不存在直接返回错误 将原来的返回错误信息的操作更改为返回null
                return null;
            }
            // 6. 写入redis
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        }catch (InterruptedException e){
            throw new RuntimeException(e);
        }finally {
            // 释放锁
            unlock(LOCK_SHOP_KEY + id);
        }
        // 7. 返回
        return shop;
    }

    // 获取锁方法
    private boolean tryLock(String key){
        //  如果直接将其返回,可能会出现拆箱和装箱之间的问题
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    // 释放锁方法
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
    // 预防缓存穿透的方法
    public Shop queryWithPassThrough(Long id){
        // 1. 从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        // 2 . 判断是否存在
        if (StrUtil.isNotBlank(shopJson)) { // 此处的判断如果为"",那么依然返回值是false,所以依然会进入到后续操作
            // 3. 存在直接返回    获取到商铺对象
            // 此处因为要将缓存穿透的解决方式进行封装,所以返回的是一个shop对象
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class); // 将返回的商品序列化为对应的商铺对象
            return JSONUtil.toBean(shopJson, Shop.class); // 返回商品对象
        }
        // 判断获取到的值是否为空字符串
        if(shopJson != null){  // 如果不进行判断,那么就会造成需要继续去访问数据库,依然对数据库造成压力
            // 返回错误信息 将原来的返回错误信息的操作更改为返回null
            return  null;
        }
        // 4. 不存在,根据ID查询数据库
        Shop shop = getById(id);
        if (shop == null) {
            // 如果不存在,则将空值存储到redis中,用于规避缓存穿透
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id , "" , CACHE_NULL_TTL , TimeUnit.MINUTES);
            // 5. 不存在直接返回错误 将原来的返回错误信息的操作更改为返回null
            return null;
        }
        // 6. 写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop) , CACHE_SHOP_TTL , TimeUnit.MINUTES);
        // 7. 返回
        return shop;
    }

    // 通过线程池的方式进行热点key的重新构建,避免任务太多造成线程过多,导致服务宕机
    private final static ExecutorService HOT_KEY_REBUILD = Executors.newFixedThreadPool(10);
    //------------------基于逻辑过期解决缓存击穿问题-----------------------
    public Shop queryWithLogicalExpire(Long id) {
        // 1. 从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        // 2 . 判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            if("".equals(shopJson)){
                return null;
            }
            // 如果是空,证明访问的是非热点key,以带TTL的方式进行缓存,解决缓存穿透
            Shop shop = queryWithPassThrough(id);
            // 2.1 不存在,直接返回
            return shop;
        }
        // 3. 存在,判断缓存是否过期
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        // 4. 未过期,直接返回
        if(redisData.getExpireTime().isAfter(LocalDateTime.now())){
            return shop;
        }
        // 5. 过期,尝试获取互斥锁
        boolean flag = tryLock(LOCK_SHOP_KEY + id);
        // 5.1 如果没有获取到锁,直接将旧数据返回
        if(!flag){
            return shop;
        }
        // 6. 获取锁成功
        // 6.1 再次进行判断redis中的数据的逻辑过期是否已过期
        shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        redisData = JSONUtil.toBean(shopJson, RedisData.class);
        shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        // 6.2 未过期,直接返回
        if(redisData.getExpireTime().isAfter(LocalDateTime.now())){
            return shop;
        }
        // 6.3 获取到锁之后,如果逻辑过期时间还是已经过期的,则开启独立线程
        HOT_KEY_REBUILD.submit(()-> {
            try {
                this.saveShop2Redis(id);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }finally {
                unlock(LOCK_SHOP_KEY + id);
            }
        });
        // 返回旧数据
        return shop;
    }

    // ------------------------------提前数据预热-------------------------------
    // 将数据存入到redis中
    public void saveShop2Redis(Long id){
        // 1. 查询店铺数据
        Shop byId = getById(id);
        // 2. 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(byId);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(30));
        // 3. 写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id , JSONUtil.toJsonStr(redisData));
    }
}
