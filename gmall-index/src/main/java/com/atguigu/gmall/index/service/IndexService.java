package com.atguigu.gmall.index.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.index.config.GmallCache;
import com.atguigu.gmall.index.feign.GmallPmsClient;
import com.atguigu.gmall.index.utils.DistributedLock;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.google.common.base.Charsets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
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
        // ??????????????????????????????????????????
        String json = this.redisTemplate.opsForValue().get(KEY_PREFIX + pid);
        if (StringUtils.isNotBlank(json)) {
            return JSON.parseArray(json, CategoryEntity.class);
        }

        // ????????????????????????????????????????????????
        RLock fairLock = this.redissonClient.getFairLock(LOCK_PREFIX + pid);
        fairLock.lock();

        try {
            // ????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
            String json2 = this.redisTemplate.opsForValue().get(KEY_PREFIX + pid);
            if (StringUtils.isNotBlank(json2)) {
                return JSON.parseArray(json2, CategoryEntity.class);
            }

            // ????????????????????????????????????
            ResponseVo<List<CategoryEntity>> listResponseVo = this.pmsClient.queryLvl2WithSubByPid(pid);
            List<CategoryEntity> categoryEntities = listResponseVo.getData();
            // ????????????
            if (CollectionUtils.isEmpty(categoryEntities)) {
                // ??????????????????????????????????????????null??????????????????????????????
                this.redisTemplate.opsForValue().set(KEY_PREFIX + pid, JSON.toJSONString(categoryEntities), 5, TimeUnit.MINUTES);
            } else {
                // ?????????????????????????????????????????????????????????
                this.redisTemplate.opsForValue().set(KEY_PREFIX + pid, JSON.toJSONString(categoryEntities), 90 + new Random().nextInt(15), TimeUnit.DAYS);
            }
            return categoryEntities;
        } finally {
            fairLock.unlock();
        }
    }

    public void testLock() {
        // ??????
        RLock lock = this.redissonClient.getLock("lock");
        lock.lock();

        String numString = this.redisTemplate.opsForValue().get("num");
        if (StringUtils.isBlank(numString)) {
            this.redisTemplate.opsForValue().set("num", "1");
        }
        int num = Integer.parseInt(numString);
        this.redisTemplate.opsForValue().set("num", String.valueOf(++num));

//        try {
//            TimeUnit.SECONDS.sleep(200);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }

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
        System.out.println("??????????????????");
        this.distributedLock.unlock("lock", uuid);
    }

    public void testLock2() {
        // ??????
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
            // ??????????????????
            // this.redisTemplate.expire("lock", 3, TimeUnit.SECONDS);
            String numString = this.redisTemplate.opsForValue().get("num");
            if (StringUtils.isBlank(numString)) {
                this.redisTemplate.opsForValue().set("num", "1");
            }
            int num = Integer.parseInt(numString);
            this.redisTemplate.opsForValue().set("num", String.valueOf(++num));
            // ??????
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

        System.out.println("????????????");

        //rwLock.readLock().unlock();
    }

    public void testWrite() {
        RReadWriteLock rwLock = this.redissonClient.getReadWriteLock("rwLock");
        rwLock.writeLock().lock(10, TimeUnit.SECONDS);

        System.out.println("????????????");
    }

    public void testsSemaphore() {
        RSemaphore semaphore = this.redissonClient.getSemaphore("semaphore");
        semaphore.trySetPermits(3);
        try {
            System.out.println(Thread.currentThread().getName() + ": ?????????????????????");
            semaphore.acquire();
            System.out.println(Thread.currentThread().getName() + ": ??????????????????");
            TimeUnit.SECONDS.sleep(10);
            System.out.println(Thread.currentThread().getName() + ": ?????????");
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
        //????????????
        cdl.countDown();
    }

//    public static void main(String[] args) {
//        BloomFilter<CharSequence> bloomFilter = BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), 20, 0.3);
//        bloomFilter.put("1");
//        bloomFilter.put("2");
//        bloomFilter.put("3");
//        bloomFilter.put("4");
//        bloomFilter.put("5");
//        bloomFilter.put("6");
//        bloomFilter.put("7");
//        System.out.println(bloomFilter.mightContain("1"));
//        System.out.println(bloomFilter.mightContain("3"));
//        System.out.println(bloomFilter.mightContain("5"));
//        System.out.println(bloomFilter.mightContain("7"));
//        System.out.println(bloomFilter.mightContain("9"));
//        System.out.println(bloomFilter.mightContain("10"));
//        System.out.println(bloomFilter.mightContain("11"));
//        System.out.println(bloomFilter.mightContain("12"));
//        System.out.println(bloomFilter.mightContain("13"));
//        System.out.println(bloomFilter.mightContain("14"));
//        System.out.println(bloomFilter.mightContain("15"));
//        System.out.println(bloomFilter.mightContain("16"));
//        System.out.println(bloomFilter.mightContain("17"));
//        System.out.println(bloomFilter.mightContain("18"));
//        System.out.println(bloomFilter.mightContain("19"));
//        System.out.println(bloomFilter.mightContain("20"));
//        System.out.println(bloomFilter.mightContain("21"));
//        System.out.println(bloomFilter.mightContain("22"));
//        System.out.println(bloomFilter.mightContain("23"));
//    }
}
