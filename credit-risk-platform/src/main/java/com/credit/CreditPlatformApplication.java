package com.credit;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan({
        "com.credit.user.mapper",
        "com.credit.agent.mapper",
        "com.credit.agent.idempotent.mapper",
        "com.credit.credit.mapper",
        "com.credit.product.mapper",
        "com.credit.workflow.mapper",
        "com.credit.platformconfig.mapper",
        "com.credit.audit.mapper"
})
@EnableAsync
@EnableScheduling
@EnableAspectJAutoProxy(exposeProxy = true)
@SpringBootApplication
public class CreditPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(CreditPlatformApplication.class, args);
    }
}
