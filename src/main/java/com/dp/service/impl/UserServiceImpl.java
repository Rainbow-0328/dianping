package com.dp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.dto.LoginFormDTO;
import com.dp.dto.Result;
import com.dp.dto.UserDTO;
import com.dp.entity.User;
import com.dp.mapper.UserMapper;
import com.dp.service.IUserService;
import com.dp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.dp.contants.RedisConstants.*;
import static com.dp.contants.RedisConstants.LOGIN_CODE_KEY;
import static com.dp.utils.SystemConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            // 2. 如果不符合,返回错误信息
           return Result.fail("手机号格式错误");
        }
        // 3. 符合,生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 4. 保存到session
        // 4. 将验证码保存到redis,并且设置有效期
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone , code , LOGIN_CODE_TTL , TimeUnit.MINUTES);
        log.debug("验证码:{}" , code);
//        session.setAttribute("code" , code);
//        session.setAttribute("phone" , phone);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. 校验手机号是否合规
        if(RegexUtils.isPhoneInvalid(loginForm.getPhone())){
            // 如果不合规直接返回,或者手机号和刚才发送的手机号不一致
            return Result.fail("手机号不合规");
        }
        // 2. 通过redis校验手机号是否存在
        String code = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + loginForm.getPhone());
        if(code == null || !code.equals(loginForm.getCode())){
            return Result.fail("验证码错误");
        }
        // 3. 判断用户是否存在
        User user = query().eq("phone", loginForm.getPhone()).one();
        if(user == null){
            user = createUserWithPhone(loginForm);
        }
        // 4. 将用户存储在redis中
        // 通过uuid作为用户的token
        String token = UUID.randomUUID().toString();
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO , new HashMap<>() , CopyOptions.create()
                .setIgnoreNullValue(true)  // 设置忽略空值
                // 设置将Value转为String类型的值
                .setFieldValueEditor((fileName , fileValue) -> fileValue.toString()));
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token , userMap);
        // 为用户的session设置有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY + token , LOGIN_USER_TTL , TimeUnit.MINUTES);
        // 删除用户的手机号的redis数据
        stringRedisTemplate.delete(LOGIN_CODE_KEY + loginForm.getPhone());
        return Result.ok(token);
    }

    private User createUserWithPhone(LoginFormDTO loginForm) {
        User user = new User();
        user.setPhone(loginForm.getPhone());
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
