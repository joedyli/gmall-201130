package com.atguigu.gmall.index.config;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GmallCache {

    /**
     * 缓存前缀
     * @return
     */
    String prefix() default "";

    /**
     * 缓存的过期时间，单位：min
     * 默认30min
     * @return
     */
    int timeout() default 30;

    /**
     * 为了防止缓存雪崩，给过期时间添加随机值
     * 这是随机值的范围。单位min
     * @return
     */
    int random() default 10;

    /**
     * 为了防止缓存击穿，给缓存会添加分布式锁
     * 这里指定分布式锁key的前缀
     * @return
     */
    String lock() default "lock:";
}
