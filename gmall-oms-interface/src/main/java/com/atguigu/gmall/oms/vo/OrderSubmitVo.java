package com.atguigu.gmall.oms.vo;

import com.atguigu.gmall.ums.entity.UserAddressEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderSubmitVo {

    private String orderToken; // 防重的唯一标识

    private UserAddressEntity address; // 收获地址

    private Integer payType; // 支付方式

    private String deliveryCompany; //  配送方式，物流公司

    private Integer bounds; // 积分

    private List<OrderItemVo> items; // 送货清单

    private BigDecimal totalPrice; // 总价格。验价
}
