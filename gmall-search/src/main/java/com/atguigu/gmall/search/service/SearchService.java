package com.atguigu.gmall.search.service;

import com.atguigu.gmall.pms.entity.BrandEntity;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.search.pojo.Goods;
import com.atguigu.gmall.search.pojo.SearchParamVo;
import com.atguigu.gmall.search.pojo.SearchResponseAttrVo;
import com.atguigu.gmall.search.pojo.SearchResponseVo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.range.Range;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class SearchService {

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public SearchResponseVo search(SearchParamVo paramVo) {

        try {
            SearchRequest request = new SearchRequest(new String[]{"goods"}, this.buildDsl(paramVo));
            SearchResponse response = this.restHighLevelClient.search(request, RequestOptions.DEFAULT);

            SearchResponseVo responseVo = this.parseResult(response);
            // 设置分页参数
            responseVo.setPageNum(paramVo.getPageNum());
            responseVo.setPageSize(paramVo.getPageSize());

            return responseVo;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 解析搜索结果集
     * @param response
     * @return
     */
    private SearchResponseVo parseResult(SearchResponse response){
        SearchResponseVo responseVo = new SearchResponseVo();

        // 解析hits，获取具体数据
        SearchHits hits = response.getHits();
        responseVo.setTotal(hits.getTotalHits()); // 总记录数

        SearchHit[] hitsHits = hits.getHits();
        if (hitsHits == null || hitsHits.length == 0){
            throw new RuntimeException("当前搜索条件没有对应的数据！");
        }

        List<Goods> goodsList = Stream.of(hitsHits).map(hitsHit -> {
            try {
                // 获取_source,进行反序列化
                String json = hitsHit.getSourceAsString();
                Goods goods = MAPPER.readValue(json, Goods.class);

                // 获取高亮的title
                Map<String, HighlightField> highlightFields = hitsHit.getHighlightFields();
                HighlightField highlightField = highlightFields.get("title");
                goods.setTitle(highlightField.getFragments()[0].string());

                return goods;
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            return null;
        }).collect(Collectors.toList());
        responseVo.setGoodsList(goodsList);

        // 解析aggregation，获取聚合数据
        Map<String, Aggregation> aggregationMap = response.getAggregations().asMap();

        // 解析出品牌
        ParsedLongTerms brandIdAgg = (ParsedLongTerms)aggregationMap.get("brandIdAgg");
        List<? extends Terms.Bucket> buckets = brandIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(buckets)){
            responseVo.setBrands(buckets.stream().map(bucket -> {
                BrandEntity brandEntity = new BrandEntity();
                brandEntity.setId(((Terms.Bucket) bucket).getKeyAsNumber().longValue());
                // 获取品牌名称 和 品牌logo的自聚合
                Map<String, Aggregation> subAggMap = ((Terms.Bucket) bucket).getAggregations().asMap();
                // 解析品牌名称的聚合结果集 获取品牌名称
                ParsedStringTerms brandNameAgg = (ParsedStringTerms)subAggMap.get("brandNameAgg");
                List<? extends Terms.Bucket> brandNameAggBuckets = brandNameAgg.getBuckets();
                if (!CollectionUtils.isEmpty(brandNameAggBuckets)){
                    brandEntity.setName(brandNameAggBuckets.get(0).getKeyAsString());
                }
                // 解析品牌logo的聚合结果集 获取 logo
                ParsedStringTerms logoAgg = (ParsedStringTerms)subAggMap.get("logoAgg");
                List<? extends Terms.Bucket> logoAggBuckets = logoAgg.getBuckets();
                if (!CollectionUtils.isEmpty(logoAggBuckets)){
                    brandEntity.setLogo(logoAggBuckets.get(0).getKeyAsString());
                }
                return brandEntity;
            }).collect(Collectors.toList()));
        }

        // 获取分类的聚合结果集
        ParsedLongTerms categoryIdAgg = (ParsedLongTerms)aggregationMap.get("categoryIdAgg");
        // 解析分类聚合结果集，或者桶集合
        List<? extends Terms.Bucket> categoryIdAggBuckets = categoryIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(categoryIdAggBuckets)){
            responseVo.setCategories(categoryIdAggBuckets.stream().map(bucket -> {
                CategoryEntity categoryEntity = new CategoryEntity();
                // 获取桶中的key，就是分类Id
                categoryEntity.setId(((Terms.Bucket) bucket).getKeyAsNumber().longValue());
                // 获取分类名称的子聚合
                ParsedStringTerms categoryNameAgg = (ParsedStringTerms) ((Terms.Bucket) bucket).getAggregations().get("categoryNameAgg");
                // 获取分类名称的桶
                List<? extends Terms.Bucket> categoryNameAggBuckets = categoryNameAgg.getBuckets();
                if (!CollectionUtils.isEmpty(categoryNameAggBuckets)){
                    categoryEntity.setName(categoryNameAggBuckets.get(0).getKeyAsString());
                }
                return categoryEntity;
            }).collect(Collectors.toList()));
        }

        // 获取规格参数的聚合
        ParsedNested attrAgg = (ParsedNested)aggregationMap.get("attrAgg");
        // 获取嵌套结果集中规格参数id的子聚合
        ParsedLongTerms attrIdAgg = (ParsedLongTerms)attrAgg.getAggregations().get("attrIdAgg");
        List<? extends Terms.Bucket> attrIdAggBuckets = attrIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(attrIdAggBuckets)){
            List<SearchResponseAttrVo> filters = attrIdAggBuckets.stream().map(bucket -> {
                SearchResponseAttrVo searchResponseAttrVo = new SearchResponseAttrVo();
                // 当前桶中的key就是规格参数的Id
                searchResponseAttrVo.setAttrId(((Terms.Bucket) bucket).getKeyAsNumber().longValue());
                // 获取当前桶中的子聚合
                Map<String, Aggregation> subAggMap = ((Terms.Bucket) bucket).getAggregations().asMap();
                // 解析规格参数名的子聚合 获取 规格参数名称
                ParsedStringTerms attrNameAgg = (ParsedStringTerms)subAggMap.get("attrNameAgg");
                List<? extends Terms.Bucket> attrNameAggBuckets = attrNameAgg.getBuckets();
                // 规格参数名有且仅有一个桶
                if (!CollectionUtils.isEmpty(attrNameAggBuckets)){
                    searchResponseAttrVo.setAttrName(attrNameAggBuckets.get(0).getKeyAsString());
                }

                // 获取了规格参数值的子聚合
                ParsedStringTerms attrValueAgg = (ParsedStringTerms)subAggMap.get("attrValueAgg");
                List<? extends Terms.Bucket> attrValueAggBuckets = attrValueAgg.getBuckets();
                if (!CollectionUtils.isEmpty(attrValueAggBuckets)){
                    // 桶中的key组成规格参数值的集合
                    searchResponseAttrVo.setAttrValues(attrValueAggBuckets.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList()));
                }
                return searchResponseAttrVo;
            }).collect(Collectors.toList());
            responseVo.setFilters(filters);
        }

        return responseVo;
    }

    /**
     * 构建DSL条件
     * @param paramVo
     * @return
     */
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

        // 2.排序的构建 1-价格降序 2-价格升序 3-销量降序 4-新品降序
        Integer sort = paramVo.getSort();
        if (sort != null) {

            switch (sort) {
                case 1: sourceBuilder.sort("price", SortOrder.DESC); break;
                case 2: sourceBuilder.sort("price", SortOrder.ASC); break;
                case 3: sourceBuilder.sort("sales", SortOrder.DESC); break;
                case 4: sourceBuilder.sort("createTime", SortOrder.DESC); break;
                default:
                    sourceBuilder.sort("_score", SortOrder.DESC);
                    break;
            }
        }

        // 3.构建分页
        Integer pageNum = paramVo.getPageNum();
        Integer pageSize = paramVo.getPageSize();
        sourceBuilder.from((pageNum - 1) * pageSize);
        sourceBuilder.size(pageSize);

        // 4.高亮
        sourceBuilder.highlighter(
                new HighlightBuilder()
                        .field("title")
                        .preTags("<font style='color:red;'>")
                        .postTags("</font>"));

        // 5.聚合
        // 5.1. 品牌聚合
        sourceBuilder.aggregation(AggregationBuilders.terms("brandIdAgg").field("brandId")
                .subAggregation(AggregationBuilders.terms("brandNameAgg").field("brandName"))
                .subAggregation(AggregationBuilders.terms("logoAgg").field("logo")));

        // 5.2. 分类聚合
        sourceBuilder.aggregation(AggregationBuilders.terms("categoryIdAgg").field("categoryId")
                .subAggregation(AggregationBuilders.terms("categoryNameAgg").field("categoryName")));

        // 5.3. 规格参数的嵌套聚合
        sourceBuilder.aggregation(AggregationBuilders.nested("attrAgg", "searchAttrs")
                .subAggregation(AggregationBuilders.terms("attrIdAgg").field("searchAttrs.attrId")
                        .subAggregation(AggregationBuilders.terms("attrNameAgg").field("searchAttrs.attrName"))
                        .subAggregation(AggregationBuilders.terms("attrValueAgg").field("searchAttrs.attrValue"))));

        // 6.构建结果集过滤
        sourceBuilder.fetchSource(new String[]{"skuId", "title", "subTitle", "defaultImage", "price"}, null);
        System.out.println(sourceBuilder);
        return sourceBuilder;
    }
}
