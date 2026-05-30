package com.forgeshift.wso2discovery.config;

import com.mongodb.connection.ConnectionPoolSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MongoPoolConfig {

    @Bean
    MongoClientSettingsBuilderCustomizer mongoPoolCustomizer(
            @Value("${forgeshift.mongodb.pool.max-size:100}") int maxPoolSize,
            @Value("${forgeshift.mongodb.pool.min-size:0}") int minPoolSize) {

        return builder -> builder.applyToConnectionPoolSettings(pool -> applyPoolSizes(
                pool,
                maxPoolSize,
                minPoolSize));
    }

    private static void applyPoolSizes(ConnectionPoolSettings.Builder pool,
                                       int maxPoolSize,
                                       int minPoolSize) {
        if (maxPoolSize < 1) {
            throw new IllegalArgumentException("MongoDB max pool size must be at least 1");
        }
        if (minPoolSize < 0) {
            throw new IllegalArgumentException("MongoDB min pool size must be at least 0");
        }
        if (minPoolSize > maxPoolSize) {
            throw new IllegalArgumentException("MongoDB min pool size cannot exceed max pool size");
        }

        pool.maxSize(maxPoolSize).minSize(minPoolSize);
    }
}
