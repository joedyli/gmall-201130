package com.atguigu.gmall.pms.service.impl;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.pms.mapper.SpuMapper;
import com.atguigu.gmall.pms.entity.SpuEntity;
import com.atguigu.gmall.pms.service.SpuService;


@Service("spuService")
public class SpuServiceImpl extends ServiceImpl<SpuMapper, SpuEntity> implements SpuService {

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<SpuEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<SpuEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public PageResultVo querySpuByCidOrPage(Long categoryId, PageParamVo pageParamVo) {

        QueryWrapper<SpuEntity> wrapper = new QueryWrapper<>();

        // 如果不为0查询指定分类，如果为0则查询所有分类
        if (categoryId != 0){
            wrapper.eq("category_id", categoryId);
        }

        // 获取搜索关键字
        String key = pageParamVo.getKey();
        if (StringUtils.isNotBlank(key)){
            wrapper.and(t -> t.eq("id", key).or().like("name", key));
        }

        IPage<SpuEntity> page = this.page(
                pageParamVo.getPage(),
                wrapper
        );

        return new PageResultVo(page);
    }

}