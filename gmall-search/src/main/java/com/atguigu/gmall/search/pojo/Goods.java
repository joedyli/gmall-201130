package com.atguigu.gmall.search.pojo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Date;
import java.util.List;

@Data
@Document(indexName = "goods", type = "info", shards = 3, replicas = 2)
public class Goods {

    // 商品列表所需字段
    @Id
    private Long skuId;
    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String title;
    @Field(type = FieldType.Keyword, index = false)
    private String subTitle;
    @Field(type = FieldType.Keyword, index = false)
    private String defaultImage;
    @Field(type = FieldType.Double)
    private Double price;

    @Field(type = FieldType.Long)
    private Long sales;// 销量
    @Field(type = FieldType.Date)
    private Date createTime; // 新品排序字段
    @Field(type = FieldType.Boolean)
    private Boolean store = false; // 是否有货

    // 品牌聚合
    @Field(type = FieldType.Long)
    private Long brandId;
    @Field(type = FieldType.Keyword)
    private String brandName;
    @Field(type = FieldType.Keyword)
    private String logo;

    // 分类聚合
    @Field(type = FieldType.Long)
    private Long categoryId;
    @Field(type = FieldType.Keyword)
    private String categoryName;

    // 规格参数聚合
    @Field(type = FieldType.Nested)
    private List<SearchAttrValue> searchAttrs;
}
