package com.credit.product.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.credit.product.entity.ProductMaterialRequirement;
import com.credit.product.mapper.ProductMaterialRequirementMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductMaterialRequirementService {

    @Resource
    private ProductMaterialRequirementMapper productMaterialRequirementMapper;

    public List<ProductMaterialRequirement> listEnabled(Long productId) {
        if (productId == null) {
            return new ArrayList<>();
        }
        return productMaterialRequirementMapper.selectList(
                new QueryWrapper<ProductMaterialRequirement>()
                        .eq("product_id", productId)
                        .eq("enabled", true)
                        .orderByAsc("id"));
    }

    public List<Map<String, Object>> toAgentView(Long productId) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ProductMaterialRequirement row : listEnabled(productId)) {
            Map<String, Object> item = new HashMap<>();
            item.put("documentType", row.getDocumentType());
            item.put("required", Boolean.TRUE.equals(row.getRequired()));
            item.put("minMonths", row.getMinMonths());
            item.put("minConfidence", row.getMinConfidence());
            item.put("description", row.getDescription());
            list.add(item);
        }
        return list;
    }
}
