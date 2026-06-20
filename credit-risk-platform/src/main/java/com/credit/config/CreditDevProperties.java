package com.credit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "credit.dev")
public class CreditDevProperties {

    private boolean exposeLoginCode = true;
}
