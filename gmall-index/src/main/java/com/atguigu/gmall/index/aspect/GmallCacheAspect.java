package com.atguigu.gmall.index.aspect;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.index.config.GmallCache;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class GmallCacheAspect {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RBloomFilter bloomFilter;

    @Around("@annotation(com.atguigu.gmall.index.config.GmallCache)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable{



        // 获取目标方法的签名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        // 获取目标方法对象
        Method method = signature.getMethod();
        // 获取目标方法上的注解对象
        GmallCache gmallCache = method.getAnnotation(GmallCache.class);
        // 获取目标方法的返回值类型：
        Class<?> returnType = method.getReturnType();
        // 获取目标方法的参数列表
        String args = StringUtils.join(joinPoint.getArgs(), ",");
        String key = gmallCache.prefix() + args;

        if (!this.bloomFilter.contains(key)) {
            return null;
        }

        // 查询缓存，如果缓存中有 直接返回
        String json = this.redisTemplate.opsForValue().get(key);
        if (StringUtils.isNotBlank(json)){
            return JSON.parseObject(json, returnType);
        }

        // 防止缓存击穿，添加分布式锁
        String lock = gmallCache.lock();
        RLock fairLock = this.redissonClient.getFairLock(lock + args);
        fairLock.lock();

        try {
            // 再次确认缓存中是否存在，如果存在，则直接返回
            String json2 = this.redisTemplate.opsForValue().get(key);
            if (StringUtils.isNotBlank(json2)){
                return JSON.parseObject(json2, returnType);
            }

            // 执行目标方法
            Object result = joinPoint.proceed(joinPoint.getArgs());

            // 把目标方法的返回结果集放入缓存（雪崩）
            if (result != null) {
                int timeout = gmallCache.timeout() + new Random().nextInt(gmallCache.random());
                this.redisTemplate.opsForValue().set(key, JSON.toJSONString(result), timeout, TimeUnit.MINUTES);
            }

            return result;
        } finally {
            fairLock.unlock();
        }
    }

//    @Pointcut("execution(* com.atguigu.gmall.index.service.*.*(..))")
//    public void pointcut(){}

    /**
     * 获取目标对象的类：joinPoint.getTarget().getClass()
     * 获取目标对象的签名：(MethodSignature) joinPoint.getSignature()
     * 获取目标方法的参数列表：joinPoint.getArgs()
     * @param joinPoint
     */
//    @Before("pointcut()")
//    public void before(JoinPoint joinPoint){
//        System.out.println("===============================================================");
//        System.out.println("前值通知：" + joinPoint.getTarget().getClass().getName());
//        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
//        System.out.println("目标对象的方法：" + signature.getMethod().getName());
//        System.out.println("目标方法的参数：" + joinPoint.getArgs());
//        System.out.println("===============================================================");
//    }
//
//    @AfterReturning(value = "execution(* com.atguigu.gmall.index.service.*.*(..))", returning = "result")
//    public void afterReturning(Object result){
//        System.out.println("==============================返回后通知========= " + result + "========================");
//    }
//
//    @AfterThrowing(value = "execution(* com.atguigu.gmall.index.service.*.*(..))", throwing = "ex")
//    public void afterThrowing(Exception ex){
//        System.out.println("==============================异常后通知==============" + ex.getMessage() + "===================");
//    }
//
//    @After("execution(* com.atguigu.gmall.index.service.*.*(..))")
//    public void after(){
//        System.out.println("==============================最终通知===============================");
//    }


}
