package com.atguigu.gmall.gateway.filter;

import lombok.Data;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

@Component
public class TestGatewayFilterFactory extends AbstractGatewayFilterFactory<TestGatewayFilterFactory.KeyValueConfig> {

    public TestGatewayFilterFactory(){
        super(KeyValueConfig.class);
    }

    @Override
    public GatewayFilter apply(KeyValueConfig config) {
        return new GatewayFilter() {
            @Override
            public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                System.out.println("我是局部过滤器，我拦截特定路由对应服务的请求。paramters=" + config.paramters);
                return chain.filter(exchange);
            }
        };
    }

    @Override
    public List<String> shortcutFieldOrder() {
//        return Arrays.asList("value", "key");
        return Arrays.asList("paramters");
    }

    @Override
    public ShortcutType shortcutType() {
        return ShortcutType.GATHER_LIST;
    }

    @Data
    public static class KeyValueConfig{
//        private String key;
//        private String value;
        private List<String> paramters;
    }
}
