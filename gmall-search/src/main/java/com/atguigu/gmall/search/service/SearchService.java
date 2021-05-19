package com.atguigu.gmall.search.service;

import com.atguigu.gmall.search.pojo.SearchParamVo;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.List;

@Service
public class SearchService {

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    public void search(SearchParamVo paramVo) {

        try {
            SearchRequest request = new SearchRequest(new String[]{"goods"}, this.buildDsl(paramVo));
            SearchResponse response = this.restHighLevelClient.search(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private SearchSourceBuilder buildDsl(SearchParamVo paramVo){
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        String keyword = paramVo.getKeyword();
        if (StringUtils.isBlank(keyword)){
            // TODO：打广告
            return sourceBuilder;
        }

        // 1.检索条件的构建
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        sourceBuilder.query(boolQueryBuilder);
        // 1.1. 构建匹配查询
        boolQueryBuilder.must(QueryBuilders.matchQuery("title", keyword).operator(Operator.AND));

        // 1.2. 构建过滤
        // 1.2.1. 构建品牌过滤
        List<Long> brandId = paramVo.getBrandId();
        if (!CollectionUtils.isEmpty(brandId)){
            boolQueryBuilder.filter(QueryBuilders.termsQuery("brandId", brandId));
        }

        // 1.2.2. 构建分类过滤
        List<Long> categoryId = paramVo.getCategoryId();
        if (!CollectionUtils.isEmpty(categoryId)){
            boolQueryBuilder.filter(QueryBuilders.termsQuery("categoryId", categoryId));
        }

        // 1.2.3. 构建价格区间
        Double priceFrom = paramVo.getPriceFrom();
        Double priceTo = paramVo.getPriceTo();
        // 如果任何一个价格不为空，就要添加价格区间的过滤
        if (priceFrom != null || priceTo != null) {
            // 构建范围查询
            RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery("price");
            boolQueryBuilder.filter(rangeQueryBuilder);
            // 如果起始价格不为空，要添加大于等于
            if (priceFrom != null) {
                rangeQueryBuilder.gte(priceFrom);
            }
            if (priceTo != null) {
                rangeQueryBuilder.lte(priceTo);
            }
        }

        // 1.2.4. 构建是否有货
        Boolean store = paramVo.getStore();
        if (store != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("store", store));
        }

        // 1.2.5. 构建规格参数["5:256G-512G", "props=4:8G-12G"]
        List<String> props = paramVo.getProps();
        if (!CollectionUtils.isEmpty(props)){
            props.forEach(prop -> { // 5:256G-512G

                // 以冒号分割，分割出规格attrId 和 256G-512G
                String[] attrs = StringUtils.split(prop, ":");
                // 如果分割后的数组 合法，才需要添加嵌套过滤
                if (attrs != null && attrs.length == 2) {
                    // 构建bool查询
                    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                    // 规格参数id的词条查询
                    boolQuery.must(QueryBuilders.termQuery("searchAttrs.attrId", attrs[0]));
                    String[] attrValues = StringUtils.split(attrs[1], "-");
                    // 规格参数值得词条查询
                    boolQuery.must(QueryBuilders.termsQuery("searchAttrs.attrValue", attrValues));
                    boolQueryBuilder.filter(QueryBuilders.nestedQuery("searchAttrs", boolQuery, ScoreMode.None));
                }
            });
        }

        // 2.排序的构建

        // 3.构建分页

        // 4.高亮

        // 5.聚合

        System.out.println(sourceBuilder);
        return sourceBuilder;
    }
}
