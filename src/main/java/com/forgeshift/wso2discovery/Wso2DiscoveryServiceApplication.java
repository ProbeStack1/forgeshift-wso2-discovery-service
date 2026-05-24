package com.forgeshift.wso2discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@ConfigurationPropertiesScan("com.forgeshift.wso2discovery.config")
@EnableAsync
public class Wso2DiscoveryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(Wso2DiscoveryServiceApplication.class, args);
    }
}
