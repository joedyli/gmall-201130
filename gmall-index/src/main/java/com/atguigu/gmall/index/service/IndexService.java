package com.atguigu.gmall.index.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.index.config.GmallCache;
import com.atguigu.gmall.index.feign.GmallPmsClient;
import com.atguigu.gmall.index.utils.DistributedLock;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class IndexService {

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "index:cates:";

    private static final String LOCK_PREFIX = "index:lock:cates:";

    @Autowired
    private DistributedLock distributedLock;

    @Autowired
    private RedissonClient redissonClient;

    public List<CategoryEntity> queryLvl1Categories() {

        ResponseVo<List<CategoryEntity>> categoryResponseVo = this.pmsClient.queryCategoriesByPid(0l);
        return categoryResponseVo.getData();
    }

    @GmallCache(prefix = KEY_PREFIX, timeout = 129600, random = 14400, lock = LOCK_PREFIX)
    public List<CategoryEntity> queryLvl2WithSubByPid(Long pid) {
        ResponseVo<List<CategoryEntity>> listResponseVo = this.pmsClient.queryLvl2WithSubByPid(pid);
        List<CategoryEntity> categoryEntities = listResponseVo.getData();
        return categoryEntities;
    }

    public List<CategoryEntity> queryLvl2WithSubByPid2(Long pid) {
        // 查询缓存，缓存中命中直接返回
        String json = this.redisTemplate.opsForValue().get(KEY_PREFIX + pid);
        if (StringUtils.isNotBlank(json)) {
            return JSON.parseArray(json, CategoryEntity.class);
        }

        // 为了防止缓存击穿，添加了分布式锁
        RLock fairLock = this.redissonClient.getFairLock(LOCK_PREFIX + pid);
        fairLock.lock();

        try {
            // 在获取分布式锁的过程中，可能有其他请求已经把数据放入缓存了。此时可以再次确认缓存中有没有
            String json2 = this.redisTemplate.opsForValue().get(KEY_PREFIX + pid);
            if (StringUtils.isNotBlank(json2)) {
                return JSON.parseArray(json2, CategoryEntity.class);
            }

            // 如果缓存中没有，远程调用
            ResponseVo<List<CategoryEntity>> listResponseVo = this.pmsClient.queryLvl2WithSubByPid(pid);
            List<CategoryEntity> categoryEntities = listResponseVo.getData();
            // 放入缓存
            if (CollectionUtils.isEmpty(categoryEntities)) {
                // 为了防止缓存穿透，数据即使为null也缓存，缓存时间较短
                this.redisTemplate.opsForValue().set(KEY_PREFIX + pid, JSON.toJSONString(categoryEntities), 5, TimeUnit.MINUTES);
            } else {
                // 为了防止缓存雪崩，给缓存时间添加随机值
                this.redisTemplate.opsForValue().set(KEY_PREFIX + pid, JSON.toJSONString(categoryEntities), 90 + new Random().nextInt(15), TimeUnit.DAYS);
            }
            return categoryEntities;
        } finally {
            fairLock.unlock();
        }
    }

    public void testLock() {
        // 加锁
        RLock lock = this.redissonClient.getLock("lock");
        lock.lock();

        String numString = this.redisTemplate.opsForValue().get("num");
        if (StringUtils.isBlank(numString)) {
            this.redisTemplate.opsForValue().set("num", "1");
        }
        int num = Integer.parseInt(numString);
        this.redisTemplate.opsForValue().set("num", String.valueOf(++num));

        try {
            TimeUnit.SECONDS.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        lock.unlock();
    }

    public void testLock3() {
        String uuid = UUID.randomUUID().toString();
        Boolean flag = this.distributedLock.tryLock("lock", uuid, 30);
        if (flag) {
            String numString = this.redisTemplate.opsForValue().get("num");
            if (StringUtils.isBlank(numString)) {
                this.redisTemplate.opsForValue().set("num", "1");
            }
            int num = Integer.parseInt(numString);
            this.redisTemplate.opsForValue().set("num", String.valueOf(++num));

            //this.testSubLock(uuid);
            try {
                TimeUnit.SECONDS.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            this.distributedLock.unlock("lock", uuid);
        }
    }

    public void testSubLock(String uuid) {
        this.distributedLock.tryLock("lock", uuid, 30);
        System.out.println("测试可重入锁");
        this.distributedLock.unlock("lock", uuid);
    }

    public void testLock2() {
        // 加锁
        String uuid = UUID.randomUUID().toString();
        Boolean lock = this.redisTemplate.opsForValue().setIfAbsent("lock", uuid, 3, TimeUnit.SECONDS);
        if (!lock) {
            try {
                Thread.sleep(100);
                testLock();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            // 设置过期时间
            // this.redisTemplate.expire("lock", 3, TimeUnit.SECONDS);
            String numString = this.redisTemplate.opsForValue().get("num");
            if (StringUtils.isBlank(numString)) {
                this.redisTemplate.opsForValue().set("num", "1");
            }
            int num = Integer.parseInt(numString);
            this.redisTemplate.opsForValue().set("num", String.valueOf(++num));
            // 解锁
            String script = "if(redis.call('get', KEYS[1]) == ARGV[1]) then return redis.call('del', KEYS[1]) else return 0 end";
            this.redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList("lock"), uuid);
//            if (StringUtils.equals(this.redisTemplate.opsForValue().get("lock"), uuid)){
//                this.redisTemplate.delete("lock");
//            }
        }

    }

    public void testRead() {
        RReadWriteLock rwLock = this.redissonClient.getReadWriteLock("rwLock");
        rwLock.readLock().lock(10, TimeUnit.SECONDS);

        System.out.println("测试读锁");

        //rwLock.readLock().unlock();
    }

    public void testWrite() {
        RReadWriteLock rwLock = this.redissonClient.getReadWriteLock("rwLock");
        rwLock.writeLock().lock(10, TimeUnit.SECONDS);

        System.out.println("测试写锁");
    }

    public void testsSemaphore() {
        RSemaphore semaphore = this.redissonClient.getSemaphore("semaphore");
        semaphore.trySetPermits(3);
        try {
            System.out.println(Thread.currentThread().getName() + ": 尝试获取停车位");
            semaphore.acquire();
            System.out.println(Thread.currentThread().getName() + ": 获取了停车位");
            TimeUnit.SECONDS.sleep(10);
            System.out.println(Thread.currentThread().getName() + ": 开走了");
            semaphore.release();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void testsLatch() {
        RCountDownLatch cdl = this.redissonClient.getCountDownLatch("cdl");
        cdl.trySetCount(6);
        try {
            cdl.await();


        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void testsCountDown() {
        RCountDownLatch cdl = this.redissonClient.getCountDownLatch("cdl");
        //。。。。
        cdl.countDown();
    }
}
