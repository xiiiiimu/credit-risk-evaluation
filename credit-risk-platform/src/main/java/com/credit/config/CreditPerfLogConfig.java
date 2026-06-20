package com.credit.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "credit.perf-log.enabled", havingValue = "true")
public class CreditPerfLogConfig {

    @Bean
    public FilterRegistrationBean<HttpPerfLoggingFilter> httpPerfLoggingFilterRegistration() {
        FilterRegistrationBean<HttpPerfLoggingFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new HttpPerfLoggingFilter());
        bean.addUrlPatterns("/api/credit/apply/submit");
        bean.setOrder(Integer.MIN_VALUE);
        return bean;
    }
}
