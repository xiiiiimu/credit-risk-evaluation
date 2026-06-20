package com.credit.agent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class AgentWebConfig implements WebMvcConfigurer {

    @Resource
    private InternalApiKeyInterceptor internalApiKeyInterceptor;
    @Resource
    private TraceIdInterceptor traceIdInterceptor;
    @Resource
    private AdminAuthInterceptor adminAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(traceIdInterceptor)
                .addPathPatterns("/**")
                .order(0);
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/admin/credit/**")
                .order(2);
        registry.addInterceptor(internalApiKeyInterceptor)
                .addPathPatterns("/internal/**")
                .order(3);
    }
}
