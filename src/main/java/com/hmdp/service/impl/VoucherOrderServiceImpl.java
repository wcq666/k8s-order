package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import com.sun.javafx.scene.control.ReadOnlyUnbackedObservableList;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

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
    private RedisIdWorker redisIdWorker;

    String queueName="stream.orders";

    @Resource
    private ISeckillVoucherService iSeckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    private BlockingQueue<VoucherOrder> blockingDeque=new ArrayBlockingQueue<>(1024*1024);

    private static  final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    private class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {

            while (true){
                //获取订单中的订单信息
                try {
                    VoucherOrder voucherOrder = blockingDeque.take();
                    System.out.println("异步处理的订单为："+voucherOrder);
                    handleVoucherOrder(voucherOrder);
                } catch (InterruptedException e) {
                    log.error("处理订单异常",e);
                }
                //创建订单
            }
        }

    }


    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT=new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lura"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private IVoucherOrderService proxy;
    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户id
        Long userId = UserHolder.getUser().getId();
        //执行Lura脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        //判断结果是否为零
        int r=result.intValue();
        if ( r!=0 ){
            //2.1不为零,代表没有购买资格
            return Result.fail(r==1 ? "库存不足!" : "不能重复下单!");
        }
        //2.2为零,有购买资格,把订单信息保存到阻塞队列中
        VoucherOrder voucherOrder = new VoucherOrder();
        //生成订单id
        long orderId = redisIdWorker.nextId("order:");
        voucherOrder.setId(orderId);
        //用户id
        voucherOrder.setUserId(userId);
        //返回订单id
        voucherOrder.setVoucherId(voucherId);
        //放入阻塞队列
        blockingDeque.add(voucherOrder);
        //获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //返回订单id
        return Result.ok(orderId);
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        //创建锁对象
        RLock lock = redissonClient.getLock("order:" + userId);
        //获取锁
        boolean isLock = lock.tryLock();
        System.out.println("锁住的对象为："+" order:" + userId);
        //判断是否获取锁成功
        if ( ! isLock ){
            //获取锁失败，返回错误重试
            log.error("不允许重复下单！");
            return;
        }
        try{
            proxy.createVoucherOrder(voucherOrder);
        }finally {
            //释放锁
            lock.unlock();
        }
    }

    @Transactional
    public  void createVoucherOrder(VoucherOrder voucherOrder){
        Long id = voucherOrder.getUserId();
        //查询订单
        Integer count = query().eq("user_id", id).eq("voucher_id", voucherOrder).count();
        //判断是否存在
        if(count>0){
            //用户已经购买过了
            log.error("用户已经购买过一次！");
            return ;
        }
        //扣减库存
        boolean success = iSeckillVoucherService.update()
                .setSql("stock=stock-1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock",0)
                .update();
        if (!success){
            //扣减失败
            log.error("库存不足！");
            return;
        }
        save(voucherOrder);
    }

    public class streamQueueVoucherOrderHandle implements Runnable {

        @Override
        public void run() {
            while(true){
                try{
                //1.获取消息队列中的订单消息XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.order
                    List<MapRecord<String, Object, Object>> read = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    //判断消息是否获取成功
                    if (read==null || list().isEmpty()){
                        //如果获取失败，说明没有消息，继续下一次循环
                        continue;
                    }
                    //解析消息中的订单信息
                    MapRecord<String, Object, Object> record = read.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //如果获取成功，可以下单
                    handleVoucherOrder(voucherOrder);
                    //ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                }catch (Exception e){
                    handlePandingList();
                }
            }
        }
    }

    private void handlePandingList() {
        while(true){
            try {
                //1.获取pending-list中的订单消息XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.order
                List<MapRecord<String, Object, Object>> read = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                        StreamOffset.create(queueName, ReadOffset.from("0"))
                );
                //判断消息是否获取成功
                if (read==null || list().isEmpty()){
                    //如果获取失败，说明没有消息，继续下一次循环
                    break;
                }
                //解析消息中的订单信息
                MapRecord<String, Object, Object> record = read.get(0);
                Map<Object, Object> values = record.getValue();
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                //如果获取成功，可以下单
                handleVoucherOrder(voucherOrder);
                //ACK确认
                stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
            }catch (Exception e){
                log.error("处理pending-list订单异常");
                try {
                    Thread.sleep(20);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
    }
}


//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //查询优惠券
//        SeckillVoucher seckillVoucher = iSeckillVoucherService.getById(voucherId);
//        //判断秒杀是否开始
//        if ( seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())){
//            //尚未开始
//            Result.fail("秒杀尚未开始！");
//        }
//        //判断秒杀是否已经结束
//        if ( seckillVoucher.getEndTime().isBefore(LocalDateTime.now())){
//            Result.fail("秒杀已经结束！");
//        }
//        //判断库存是否充足
//        if (seckillVoucher.getStock()<1){
//            Result.fail("库存不足！");
//        }
//        //一人一单
//        UserDTO user = UserHolder.getUser();
//        Long id = user.getId();
////        synchronized (id.toString().intern()) {
////            //获取代理对象
////            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
////            return proxy.createVoucherOrder(voucherId);
////        }
//        //创建锁对象
//        //SimpleRedisLock lock = new SimpleRedisLock("order:"+id,stringRedisTemplate);
//        RLock lock = redissonClient.getLock("order:" + id);
//        //获取锁
//        boolean isLock = lock.tryLock();
//        //判断是否获取锁成功
//        if ( ! isLock ){
//            //获取锁失败，返回错误重试
//            return Result.fail("不允许重复下单！");
//        }
//        try{
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }finally {
//            //释放锁
//            lock.unlock();
//        }
//    }