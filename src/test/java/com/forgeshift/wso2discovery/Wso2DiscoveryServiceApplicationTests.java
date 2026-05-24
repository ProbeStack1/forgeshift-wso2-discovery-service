package com.forgeshift.wso2discovery;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Smoke test: the Spring context starts cleanly with a real MongoDB.
 *
 * The WSO2 client is wired but never invoked here, so no live WSO2 backend
 * is required.
 */
@SpringBootTest
@Testcontainers
class Wso2DiscoveryServiceApplicationTests {

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:6.0");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry r) {
        r.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
    }

    @Test
    void contextLoads() {
        // The assertion is implicit in successful context startup.
    }
}
