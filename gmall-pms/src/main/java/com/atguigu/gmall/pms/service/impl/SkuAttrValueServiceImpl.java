package com.atguigu.gmall.pms.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.pms.entity.AttrEntity;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.atguigu.gmall.pms.mapper.AttrMapper;
import com.atguigu.gmall.pms.mapper.SkuMapper;
import com.atguigu.gmall.pms.vo.SaleAttrValueVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.pms.mapper.SkuAttrValueMapper;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.service.SkuAttrValueService;
import org.springframework.util.CollectionUtils;
import springfox.documentation.annotations.ApiIgnore;


@Service("skuAttrValueService")
public class SkuAttrValueServiceImpl extends ServiceImpl<SkuAttrValueMapper, SkuAttrValueEntity> implements SkuAttrValueService {

    @Autowired
    private AttrMapper attrMapper;

    @Autowired
    private SkuMapper skuMapper;

    @Autowired
    private SkuAttrValueMapper attrValueMapper;

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<SkuAttrValueEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<SkuAttrValueEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public List<SkuAttrValueEntity> querySearchAttrValuesByCidAndSkuId(Long cid, Long skuId) {
        // 1.根据分类的id查询出检索类型的规格参数
        List<AttrEntity> attrEntities = this.attrMapper.selectList(new QueryWrapper<AttrEntity>().eq("category_id", cid).eq("search_type", 1));
        if (CollectionUtils.isEmpty(attrEntities)){
            return null;
        }
        // 获取attrIds
        List<Long> attrIds = attrEntities.stream().map(AttrEntity::getId).collect(Collectors.toList());

        // 2.根据skuId和AttrIds查询销售类型的规格参数和值
        return this.list(new QueryWrapper<SkuAttrValueEntity>().eq("sku_id", skuId).in("attr_id", attrIds));
    }

    @Override
    public List<SaleAttrValueVo> querySaleAttrValuesBySpuId(Long spuId) {
        // 根据spuId查询sku集合
        List<SkuEntity> skuEntities = this.skuMapper.selectList(new QueryWrapper<SkuEntity>().eq("spu_id", spuId));
        if (CollectionUtils.isEmpty(skuEntities)){
            return null;
        }
        // 获取skuId集合
        List<Long> skuIds = skuEntities.stream().map(SkuEntity::getId).collect(Collectors.toList());

        // 查询所有销售属性
        List<SkuAttrValueEntity> attrValueEntities = this.list(new QueryWrapper<SkuAttrValueEntity>().in("sku_id", skuIds).orderByAsc("attr_id"));
        if (CollectionUtils.isEmpty(attrValueEntities)){
            return null;
        }

        List<SaleAttrValueVo> saleAttrValueVos = new ArrayList<>();
        // 以attrId作为key，以相同attrId对应的ListSkuAttrValueEntity>作为值
        Map<Long, List<SkuAttrValueEntity>> map = attrValueEntities.stream().collect(Collectors.groupingBy(SkuAttrValueEntity::getAttrId));
        map.forEach((attrId, skuAttrValueEntities) -> { // 需要把每个k-v结构转化成SaleAttrValueVo对象
            SaleAttrValueVo saleAttrValueVo = new SaleAttrValueVo();
            saleAttrValueVo.setAttrId(attrId);
            // groupby的结果集合中至少会有一个元素，取第一个
            saleAttrValueVo.setAttrName(skuAttrValueEntities.get(0).getAttrName());
            Set<String> attrValues = skuAttrValueEntities.stream().map(SkuAttrValueEntity::getAttrValue).collect(Collectors.toSet());
            saleAttrValueVo.setAttrValue(attrValues);
            saleAttrValueVos.add(saleAttrValueVo); // 放入集合
        });
        return saleAttrValueVos;
    }

    @Override
    public String querySaleAttrValuesMappingSkuIdBySpuId(Long spuId) {
        // 根据spuId查询sku集合
        List<SkuEntity> skuEntities = this.skuMapper.selectList(new QueryWrapper<SkuEntity>().eq("spu_id", spuId));
        if (CollectionUtils.isEmpty(skuEntities)){
            return null;
        }
        // 获取skuId集合
        List<Long> skuIds = skuEntities.stream().map(SkuEntity::getId).collect(Collectors.toList());

        // 查询映射关系
        List<Map<String, Object>> maps = this.attrValueMapper.querySaleAttrValuesMappingSkuId(skuIds);
        if (CollectionUtils.isEmpty(maps)){
            return null;
        }
        Map<String, Long> mapping = maps.stream().collect(Collectors.toMap(map -> map.get("attrValues").toString(), map -> (Long) map.get("sku_id")));
        return JSON.toJSONString(mapping);
    }

}