package com.atguigu.gmall.cart.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.feign.GmallPmsClient;
import com.atguigu.gmall.cart.feign.GmallSmsClient;
import com.atguigu.gmall.cart.feign.GmallWmsClient;
import com.atguigu.gmall.cart.interceptor.LoginInterceptor;
import com.atguigu.gmall.cart.mapper.CartMapper;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.cart.pojo.UserInfo;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.exception.CartException;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.concurrent.ListenableFuture;
import springfox.documentation.spring.web.json.Json;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class CartService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CartAsyncService asyncService;

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallWmsClient wmsClient;

    @Autowired
    private GmallSmsClient smsClient;

    private static final String KEY_PREFIX = "cart:info:";
    private static final String PRICE_PREFIX = "cart:price:";

    public void addCart(Cart cart) {
        // 获取登录状态：userId userKey
        String userId = getUserId();

        // 获取内层的map结构
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);

        // 判断当前用户的购物车是否包含该记录
        String skuId = cart.getSkuId().toString();
        BigDecimal count = cart.getCount();
        if (hashOps.hasKey(skuId)) {
            // 如果已包含，则更新数量
            String json = hashOps.get(skuId).toString();
            cart = JSON.parseObject(json, Cart.class);
            cart.setCount(cart.getCount().add(count));
            // 写入数据库
            this.asyncService.updateCart(userId, skuId, cart);
        } else {
            // 如果不包含，则新增记录
            cart.setUserId(userId);
            cart.setCheck(true);

            // 获取sku相关信息
            ResponseVo<SkuEntity> skuEntityResponseVo =
                    this.pmsClient.querySkuById(cart.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity == null) {
                throw new CartException("没有对应的商品");
            }
            cart.setTitle(skuEntity.getTitle());
            cart.setPrice(skuEntity.getPrice());
            cart.setDefaultImage(skuEntity.getDefaultImage());

            // 查询库存
            ResponseVo<List<WareSkuEntity>> wareResponseVo =
                    this.wmsClient.queryWareSkusBySkuId(cart.getSkuId());
            List<WareSkuEntity> wareSkuEntities = wareResponseVo.getData();
            if (!CollectionUtils.isEmpty(wareSkuEntities)){
                cart.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
            }

            // 查询销售属性
            ResponseVo<List<SkuAttrValueEntity>> responseVo = this.pmsClient.querySaleAttrValuesBySkuId(cart.getSkuId());
            List<SkuAttrValueEntity> skuAttrValueEntities = responseVo.getData();
            cart.setSaleAttrs(JSON.toJSONString(skuAttrValueEntities));

            // 查询营销信息
            ResponseVo<List<ItemSaleVo>> salesResponseVo = this.smsClient.querySalesBySkuId(cart.getSkuId());
            List<ItemSaleVo> itemSaleVos = salesResponseVo.getData();
            cart.setSales(JSON.toJSONString(itemSaleVos));

            this.asyncService.insertCart(userId, cart);
            // 添加实时价格缓存
            this.redisTemplate.opsForValue().set(PRICE_PREFIX + skuId, skuEntity.getPrice().toString());
        }
        hashOps.put(skuId, JSON.toJSONString(cart));
    }



    private String getUserId() {
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        String userId = null;
        if (userInfo.getUserId() == null){
            userId = userInfo.getUserKey();
        } else {
            userId = userInfo.getUserId().toString();
        }
        return userId;
    }

    public Cart queryCart(Long skuId) {
        String userId = this.getUserId();

        // 内层操作对象
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        if (!hashOps.hasKey(skuId.toString())){
            throw new CartException("该用户没有对应的购物车记录");
        }

        String json = hashOps.get(skuId.toString()).toString();

        return JSON.parseObject(json, Cart.class);
    }

    @Async
    public void executor1(){
        try {
            System.out.println("executor1开始执行");
            TimeUnit.SECONDS.sleep(5);
            int i = 1/0;
            System.out.println("executor1执行结束。。。。。。。。。。");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Async
    public void executor2(){
        try {
            System.out.println("executor2开始执行");
            TimeUnit.SECONDS.sleep(4);
            System.out.println("executor2执行结束。。。。。。。。。。");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public List<Cart> queryCarts() {

        // 1.获取userkey，查询未登录的购物车
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        String userKey = userInfo.getUserKey();
        String unLoginKey = KEY_PREFIX + userKey;
        // 获取内层的map
        BoundHashOperations<String, Object, Object> unLoginHashOps = this.redisTemplate.boundHashOps(unLoginKey);
        List<Object> unLoginCartJsons = unLoginHashOps.values();
        List<Cart> unLoginCarts = null;
        if (!CollectionUtils.isEmpty(unLoginCartJsons)){
            unLoginCarts = unLoginCartJsons.stream().map(json -> {
                Cart cart = JSON.parseObject(json.toString(), Cart.class);
                // 查询购物车实时价格缓存
                cart.setCurrentPrice(new BigDecimal(this.redisTemplate.opsForValue().get(PRICE_PREFIX + cart.getSkuId())));
                return cart;
            }).collect(Collectors.toList());
        }

        // 2.获取userId，判断是否为空，为空则直接返回未登录的购物车
        Long userId = userInfo.getUserId();
        if (userId == null){
            return unLoginCarts;
        }

        // 3.不为空，把未登录的购物车合并到已登录的购物车中
        BoundHashOperations<String, Object, Object> loginHashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        if (!CollectionUtils.isEmpty(unLoginCarts)){
            unLoginCarts.forEach(cart -> {
                String skuId = cart.getSkuId().toString();
                BigDecimal count = cart.getCount(); // 未登录购物车中的数量
                if (loginHashOps.hasKey(cart.getSkuId().toString())){
                    String json = loginHashOps.get(skuId).toString();
                    cart = JSON.parseObject(json, Cart.class);
                    cart.setCount(cart.getCount().add(count));
                    // 异步写入mysql
                    this.asyncService.updateCart(userId.toString(), skuId, cart);
                } else {
                    cart.setUserId(userId.toString());
                    // 异步写入mysql
                    this.asyncService.insertCart(userId.toString(), cart);
                }
                // 写入redis
                loginHashOps.put(skuId, JSON.toJSONString(cart));
            });
        }

        // 4.删除未登录的购物车
        this.redisTemplate.delete(unLoginKey); // 删除redis中的购物车
        this.asyncService.deleteCart(userKey);

        // 5.获取已登录的购物车，并返回
        List<Object> loginCartJsons = loginHashOps.values();
        if (!CollectionUtils.isEmpty(loginCartJsons)){
            return loginCartJsons.stream().map(json -> {
                Cart cart = JSON.parseObject(json.toString(), Cart.class);
                cart.setCurrentPrice(new BigDecimal(this.redisTemplate.opsForValue().get(PRICE_PREFIX + cart.getSkuId())));
                return cart;
            }).collect(Collectors.toList());
        }
        return null;
    }

    public void updateNum(Cart cart) {
        String userId = this.getUserId();

        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        String skuId = cart.getSkuId().toString();
        BigDecimal count = cart.getCount();
        if (!hashOps.hasKey(skuId)){
            throw new CartException("您没有对应的购物车记录");
        }
        String json = hashOps.get(skuId).toString();
        cart = JSON.parseObject(json, Cart.class);
        cart.setCount(count);

        // 写入redis
        hashOps.put(skuId, JSON.toJSONString(cart));
        // 异步写入mysql
        this.asyncService.updateCart(userId, skuId, cart);
    }

    public void deleteCart(Long skuId) {
        String userId = this.getUserId();

        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);

        hashOps.delete(skuId.toString());
        this.asyncService.deleteCartByUserIdAndSkuId(userId, skuId);

    }
}
