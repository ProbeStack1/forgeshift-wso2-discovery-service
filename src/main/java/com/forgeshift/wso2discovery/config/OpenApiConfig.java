package com.forgeshift.wso2discovery.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiDocs() {
        return new OpenAPI().info(new Info()
                .title("Forgeshift WSO2 Discovery Service")
                .version("0.1.0")
                .description("Discovers WSO2 API Manager artifacts and stages them in GCS "
                        + "for downstream migration to Kong Konnect."));
    }
}
