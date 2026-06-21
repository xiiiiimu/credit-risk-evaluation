package com.credit.credit.cache;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.credit.credit.dto.CreditApplyVO;
import com.credit.credit.entity.CreditProduct;
import com.credit.common.util.CacheClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Cache Aside：写 MySQL 成功后删除缓存；读穿透/雪崩/击穿由 CacheClient 统一处理。
 */
@Service
public class CreditCacheService {

    /** 规范 Key：application:{id} */
    public static final String KEY_APPLICATION = "application:";
    /** 规范 Key：workflow:{id} */
    public static final String KEY_WORKFLOW = "workflow:";
    /** 规范 Key：risk_result:{workflowId} */
    public static final String KEY_RISK_RESULT = "risk_result:";

    /** 兼容旧 Key */
    public static final String KEY_PRODUCT = "credit:product:";
    public static final String KEY_APPLY = KEY_APPLICATION;
    public static final String KEY_APPLY_MINE = "credit:apply:mine:";
    public static final String KEY_RECORD_SUMMARY = "credit:record:summary:";

    private static final long NULL_TTL_MINUTES = 2L;

    @Resource
    private CacheClient cacheClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 产品信息主要来自产品列表，正常用户一般不会随机查不存在 productId；
     * 若产品详情接口暴露给外部，参数校验 + 空值缓存可防止不存在产品反复穿透数据库。
     */
    public CreditProduct getProduct(Long productId, Function<Long, CreditProduct> dbFallback) {
        if (productId == null || productId <= 0) {
            return null;
        }
        return cacheClient.queryWithLogicalExpire(
                KEY_PRODUCT, productId, CreditProduct.class, dbFallback, 30L, TimeUnit.MINUTES);
    }

    public CreditApplyVO getApplication(Long applicationId, Function<Long, CreditApplyVO> dbFallback) {
        return cacheClient.queryWithPassThrough(
                KEY_APPLICATION, applicationId, CreditApplyVO.class, dbFallback, 10L, TimeUnit.MINUTES);
    }

    public List<CreditApplyVO> getMyApplications(Long userId, Function<Long, List<CreditApplyVO>> dbFallback) {
        String key = KEY_APPLY_MINE + userId;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toList(json, CreditApplyVO.class);
        }
        if (json != null) {
            return Collections.emptyList();
        }
        List<CreditApplyVO> list = dbFallback.apply(userId);
        if (list == null || list.isEmpty()) {
            stringRedisTemplate.opsForValue().set(key, "", NULL_TTL_MINUTES, TimeUnit.MINUTES);
            return Collections.emptyList();
        }
        cacheClient.set(key, list, 5L, TimeUnit.MINUTES);
        return list;
    }

    public String getBureauSummary(Long userId, Function<Long, String> dbFallback) {
        return cacheClient.queryWithPassThrough(
                KEY_RECORD_SUMMARY, userId, String.class, dbFallback, 5L, TimeUnit.MINUTES);
    }

    public void evictApplication(Long applicationId, Long userId) {
        if (applicationId != null) {
            stringRedisTemplate.delete(KEY_APPLICATION + applicationId);
            stringRedisTemplate.delete(KEY_APPLY + applicationId);
        }
        if (userId != null) {
            stringRedisTemplate.delete(KEY_APPLY_MINE + userId);
            stringRedisTemplate.delete(KEY_RECORD_SUMMARY + userId);
        }
    }

    public void evictWorkflow(String workflowId) {
        if (workflowId != null) {
            stringRedisTemplate.delete(KEY_WORKFLOW + workflowId);
        }
    }

    public void evictRiskResult(String workflowId) {
        if (workflowId != null) {
            stringRedisTemplate.delete(KEY_RISK_RESULT + workflowId);
        }
    }

    public void evictProduct(Long productId) {
        if (productId != null) {
            stringRedisTemplate.delete(KEY_PRODUCT + productId);
            stringRedisTemplate.delete("product:" + productId);
            stringRedisTemplate.delete("product_rule:" + productId);
            stringRedisTemplate.delete("product_material:" + productId);
        }
    }
}
