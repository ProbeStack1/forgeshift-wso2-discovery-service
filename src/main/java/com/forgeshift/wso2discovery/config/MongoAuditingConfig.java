package com.forgeshift.wso2discovery.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * Enables population of @CreatedDate and @LastModifiedDate on the domain
 * documents.
 */
@Configuration
@EnableMongoAuditing
public class MongoAuditingConfig {
}
