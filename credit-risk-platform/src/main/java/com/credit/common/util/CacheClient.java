package com.credit.common.util;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.TimeoutUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.credit.common.util.RedisConstants.*;


@Slf4j
@Component
public class CacheClient {
    private StringRedisTemplate stringRedisTemplate;
    public CacheClient(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }



    public void set(String key, Object value, Long time, TimeUnit unit){
        long ttlSeconds = applyJitter(time, unit);
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), ttlSeconds, TimeUnit.SECONDS);
    }
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));

    }
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        //1. 从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2. 判断是否存在
        if(StrUtil.isNotBlank(json)) {
            //3.存在，直接返回
            return JSONUtil.toBean(json, type);

        }
        //判断命中是否是空值
        if (json != null){
            //返回一个错误信息
            return null;
        }
        //4.不存在，根据id查询数据库 — 热点 Key 互斥回源
        String lockKey = LOCK_CACHE_REBUILD_KEY + key;
        boolean locked = tryLock(lockKey);
        try {
            if (locked) {
                String retryJson = stringRedisTemplate.opsForValue().get(key);
                if (StrUtil.isNotBlank(retryJson)) {
                    return JSONUtil.toBean(retryJson, type);
                }
                if (retryJson != null) {
                    return null;
                }
                R loaded = dbFallback.apply(id);
                if (loaded == null) {
                    stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                    return null;
                }
                this.set(key, loaded, time, unit);
                return loaded;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            String waited = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(waited)) {
                return JSONUtil.toBean(waited, type);
            }
            return dbFallback.apply(id);
        } finally {
            if (locked) {
                unlock(lockKey);
            }
        }
    }

    /** TTL ±10% 抖动，缓解缓存雪崩 */
    static long applyJitter(long time, TimeUnit unit) {
        if (time <= 0) {
            return time;
        }
        long baseSeconds = unit.toSeconds(time);
        double factor = 0.9 + ThreadLocalRandom.current().nextDouble(0.2);
        return Math.max(1L, Math.round(baseSeconds * factor));
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <R, ID> R queryWithLogicalExpire(
            String keyprefix, ID id, Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit unit){
        String key = keyprefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 空值缓存：写入 ""，避免不存在的数据反复穿透 DB
        if (json != null && json.isEmpty()) {
            return null;
        }
        if (StrUtil.isBlank(json)) {
            R loaded = dbFallback.apply(id);
            if (loaded == null) {
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            this.setWithLogicalExpire(key, loaded, time, unit);
            return loaded;
        }
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }

        String lockKey = LOCK_CACHE_REBUILD_KEY + key;
        boolean isLock = tryLock(lockKey);
        if(isLock){
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    R r1 = dbFallback.apply(id);
                    if (r1 == null) {
                        stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                        return;
                    }
                    this.setWithLogicalExpire(key,r1,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally{
                    unlock(lockKey);
                }

            });

        }
        return r;

    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

}
