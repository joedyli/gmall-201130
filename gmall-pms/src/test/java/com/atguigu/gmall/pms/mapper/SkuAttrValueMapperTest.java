package com.atguigu.gmall.pms.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SkuAttrValueMapperTest {

    @Autowired
    private SkuAttrValueMapper skuAttrValueMapper;

    @Test
    void querySaleAttrValuesMappingSkuId() {
        System.out.println(this.skuAttrValueMapper.querySaleAttrValuesMappingSkuId(Arrays.asList(111l, 112l, 113l, 114l)));
    }
}