package com.atguigu.gmall.wms.vo;

import lombok.Data;

@Data
public class SkuLockVo {

    private Long skuId;

    private Integer count;

    private Boolean lock; // 锁定是否成功

    private Long wareSkuId; // 一旦锁定成功，记录锁定成功的仓库id
}
