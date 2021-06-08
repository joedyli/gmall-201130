package com.atguigu.gmall.payment.controller;

import com.alipay.api.AlipayApiException;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.exception.OrderException;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.payment.config.AlipayTemplate;
import com.atguigu.gmall.payment.interceptor.LoginInterceptor;
import com.atguigu.gmall.payment.pojo.PayAsyncVo;
import com.atguigu.gmall.payment.pojo.PayVo;
import com.atguigu.gmall.payment.pojo.PaymentInfoEntity;
import com.atguigu.gmall.payment.pojo.UserInfo;
import com.atguigu.gmall.payment.service.PaymentService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RCountDownLatch;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Controller
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private AlipayTemplate alipayTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @GetMapping("pay.html")
    public String toPay(@RequestParam("orderToken")String orderToken, Model model){

        // 查询订单是否存在
        OrderEntity orderEntity = this.paymentService.queryOrder(orderToken);
        if (orderEntity == null){
            throw new OrderException("您要支付的订单不存在！");
        }

        // 如果存在判断是否自己的
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getUserId();
        if (orderEntity.getUserId() != userId){
            throw new OrderException("要支付的订单不属于您！");
        }

        // 判断订单状态
        if (orderEntity.getStatus() != 0){
            throw new OrderException("当前订单无法支付！");
        }

        model.addAttribute("orderEntity", orderEntity);

        return "pay";
    }

    @GetMapping("alipay.html")
    @ResponseBody // 以其他视图形式展示方法的返回结果集
    public String toAlipay(@RequestParam("orderToken")String orderToken){
        // 查询订单是否存在
        OrderEntity orderEntity = this.paymentService.queryOrder(orderToken);
        if (orderEntity == null){
            throw new OrderException("您要支付的订单不存在！");
        }

        // 如果存在判断是否自己的
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getUserId();
        if (orderEntity.getUserId() != userId){
            throw new OrderException("要支付的订单不属于您！");
        }

        // 判断订单状态
        if (orderEntity.getStatus() != 0){
            throw new OrderException("当前订单无法支付！");
        }

        try {
            // TODO: 调用支付接口
            PayVo payVo = new PayVo();
            payVo.setOut_trade_no(orderEntity.getOrderSn());
            // 支付金额，不要写订单中的金额，就写0.01
            payVo.setTotal_amount("0.01");
            payVo.setSubject("谷粒商城支付平台");
            // 记录对账信息
            Long payId = this.paymentService.savePaymentInfo(payVo);
            payVo.setPassback_params(payId.toString());
            String form = this.alipayTemplate.pay(payVo);

            return form;
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 同步回调
     * @return
     */
    @GetMapping("pay/success")
    public String paySuccess(){

        // TODO: 获取订单编号，查询订单

        return "paysuccess";
    }

    /**
     * 异步回调
     * @return
     */
    @PostMapping("pay/ok")
    public Object payOk(PayAsyncVo payAsyncVo){
        // 1.验签
        Boolean flag = this.alipayTemplate.checkSignature(payAsyncVo);
        if (!flag){
            return "failure";
        }

        // 2.校验业务参数：app_id、out_trade_no、total_amount
        String app_id = this.alipayTemplate.getApp_id(); // 服务内的appId
        String appId = payAsyncVo.getApp_id();  // 支付宝响应的appId

        // 订单编号
        String out_trade_no = payAsyncVo.getOut_trade_no(); // 支付宝响应的订单编号
        String payId = payAsyncVo.getPassback_params();
        PaymentInfoEntity paymentInfoEntity = this.paymentService.queryById(payId);
        String outTradeNo = paymentInfoEntity.getOutTradeNo(); // 获取对账表中的订单编号

        // 金额
        String total_amount = payAsyncVo.getTotal_amount();
        BigDecimal totalAmount = paymentInfoEntity.getTotalAmount();
        if (!StringUtils.equals(app_id, appId) || !StringUtils.equals(out_trade_no, outTradeNo)
            || new BigDecimal(total_amount).compareTo(totalAmount) != 0
        ){
            return "failure";
        }

        // 3.校验回调的状态：TRADE_SUCCESS
        if (!StringUtils.equals("TRADE_SUCCESS", payAsyncVo.getTrade_status())){
            return "failure";
        }

        // 4.记录对账信息
        if (this.paymentService.udpatePaymentInfo(payAsyncVo) == 1) {
            // 5.发送消息给mq，修改订单状态 pay -> oms -> wms
            this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE", "order.pay", out_trade_no);
        }

        // 返回成功
        return "success";
    }

    @GetMapping("seckill/{skuId}")
    public String seckill(@PathVariable("skuId")Long skuId, Model model){

        UserInfo userInfo = LoginInterceptor.getUserInfo();

        RLock lock = this.redissonClient.getLock("seckill:lock:" + skuId);
        lock.lock();

        try {
            String stockString = this.redisTemplate.opsForValue().get("seckill:info:" + skuId);
            if (StringUtils.isBlank(stockString) || Integer.parseInt(stockString) <= 0){
                throw new OrderException("手慢无，请下次再来");
            }

            this.redisTemplate.opsForValue().decrement("seckill:info:" + skuId);
            Map<String, Object> msg = new HashMap<>();
            String orderToken = IdWorker.getIdStr();
            msg.put("orderToken", orderToken);
            msg.put("skuId", skuId);
            msg.put("userId", userInfo.getUserId());
            msg.put("count", 1);
            this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE", "seckill.create", msg);

            RCountDownLatch countDownLatch = this.redissonClient.getCountDownLatch("seckill:cdl:" + orderToken);
            countDownLatch.trySetCount(1);

            model.addAttribute("orderToken", orderToken);
            return "secsuccess";
        } catch (NumberFormatException e) {
            e.printStackTrace();
        } catch (OrderException e) {
            e.printStackTrace();
        } catch (AmqpException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
        model.addAttribute("msg", "秒杀失败，下次再来");
        return "failure";
    }

    @GetMapping("seckill/order/{orderToken}")
    public ResponseVo<OrderEntity> queryOrder(@PathVariable("orderToken")String orderToken) throws InterruptedException {

        RCountDownLatch countDownLatch = this.redissonClient.getCountDownLatch("seckill:cdl:" + orderToken);
        countDownLatch.await();

        OrderEntity orderEntity = this.paymentService.queryOrder(orderToken);
        return ResponseVo.ok(orderEntity);
    }
}
