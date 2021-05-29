package com.atguigu.gmall.item.vo;

import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.pms.entity.SkuImagesEntity;
import com.atguigu.gmall.pms.vo.ItemGroupVo;
import com.atguigu.gmall.pms.vo.SaleAttrValueVo;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class ItemVo {

    // 一二三级分类
    private List<CategoryEntity> categories;
    // 品牌相关信息
    private Long brandId;
    private String brandName;
    // spu的相关信息
    private Long spuId;
    private String spuName;

    // sku相关信息
    private Long skuId;
    private String title;
    private String subTitle;
    private BigDecimal price;
    private String defaultImages;
    private Integer weight;

    // 优惠信息
    private List<ItemSaleVo> sales; // 营销信息

    // 图片列表
    private List<SkuImagesEntity> images;

    // 是否有货
    private Boolean store = false;

    // 销售属性列表
    // [
    //  {attrId: 4, attrName: 颜色, attrValues: [暗夜黑, 白天白]}
    //  {attrId: 5, attrName: 内存, attrValues: [8G, 12G]}
    //  {attrId: 6, attrName: 存储, attrValues: [128G, 256G]}
    // ]
    private List<SaleAttrValueVo> saleAttrs;

    // 当前sku的销售属性
    // {4: 白天白, 5: 8G, 6: 128G}
    private Map<Long, String> saleAttr;

    // 销售属性组合与skuId的映射关系
    // {白天白,8G,128: 10, 白天白,12G,256G: 20}
    private String skuJsons;

    // 商品描述信息
    private List<String> spuImages;

    // 规格参数
    private List<ItemGroupVo> groups;
}
