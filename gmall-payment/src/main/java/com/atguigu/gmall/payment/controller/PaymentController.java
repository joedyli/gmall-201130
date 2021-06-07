package com.atguigu.gmall.payment.controller;

import com.atguigu.gmall.common.exception.OrderException;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.payment.interceptor.LoginInterceptor;
import com.atguigu.gmall.payment.pojo.UserInfo;
import com.atguigu.gmall.payment.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

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
}
