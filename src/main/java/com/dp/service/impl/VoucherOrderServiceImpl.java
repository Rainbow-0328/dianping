package com.dp.service.impl;

import com.dp.dto.Result;
import com.dp.entity.SeckillVoucher;
import com.dp.entity.VoucherOrder;
import com.dp.mapper.VoucherOrderMapper;
import com.dp.service.ISeckillVoucherService;
import com.dp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.utils.RedisIdWorker;
import com.dp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService iSeckillVoucherService;
    @Autowired
    private RedisIdWorker redisIdWorker;

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1. 查询优惠卷
        SeckillVoucher seckillVoucher = iSeckillVoucherService.getById(voucherId);
        if(seckillVoucher == null){
            return Result.fail("优惠卷不存在");
        }
        // 2. 判断秒杀是否开始
        if(LocalDateTime.now().isBefore(seckillVoucher.getBeginTime()) ||
                // 3. 判断秒杀是否结束
                seckillVoucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀尚未开始或已经结束");
        }
        return createVoucherOrder(voucherId, seckillVoucher);
    }
    @Transactional
    public Result createVoucherOrder(Long voucherId, SeckillVoucher seckillVoucher) {
        // 判断用户是否下过单了
        Integer count = query().eq("user_id", UserHolder.getUser().getId()).eq("voucher_id", voucherId).count();
        if(count > 0){
            return Result.fail("用户已经抢过该优惠卷了");
        }
        // 4. 判断库存是否充足
        if(seckillVoucher.getStock() <= 0){
            return Result.fail("优惠卷已经被抢完了");
        }
        // 5. 扣减库存
        boolean success = iSeckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id", voucherId)
                .gt("stock" , 0).update();
        if(!success){
            return Result.fail("扣减库存失败");
        }
        // 6. 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setId(redisIdWorker.nextId("order"));
        voucherOrder.setUserId(UserHolder.getUser().getId());
        // 7. 返回订单id
        save(voucherOrder);
        return Result.ok(voucherOrder.getId());
    }
}
