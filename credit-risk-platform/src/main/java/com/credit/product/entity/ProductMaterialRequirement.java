package com.credit.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("tb_product_material_requirement")
public class ProductMaterialRequirement {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String documentType;
    private Boolean required;
    private Integer minMonths;
    private BigDecimal minConfidence;
    private String description;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
