package com.forgeshift.wso2discovery.config;

import com.forgeshift.wso2discovery.client.KonnectIdentityClient;
import com.forgeshift.wso2discovery.client.Wso2Client;
import com.forgeshift.wso2discovery.client.Wso2UserStoreSoapClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the wiring of the two {@link org.springframework.web.reactive.function.client.WebClient}
 * beans.
 *
 * <p>Every collaborator that takes a WebClient must name which one it wants.
 * While only {@code wso2WebClient} existed an unqualified injection resolved
 * fine, so a missing qualifier was invisible; adding {@code konnectWebClient}
 * turned that into a NoUniqueBeanDefinitionException that fails the whole
 * context at startup, which in the cluster shows up only as a rollout that
 * never becomes ready.
 *
 * <p>Runs without Docker or Mongo, unlike the full application context test,
 * so this class of breakage is caught by the normal build.
 */
class WebClientWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(Wso2Properties.class)
            .withUserConfiguration(WebClientConfig.class)
            .withBean(Wso2Client.class)
            .withBean(Wso2UserStoreSoapClient.class)
            .withBean(KonnectIdentityClient.class);

    @Test
    void everyWebClientCollaboratorResolvesToExactlyOneBean() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("wso2WebClient");
            assertThat(context).hasBean("konnectWebClient");
            assertThat(context).hasSingleBean(Wso2Client.class);
            assertThat(context).hasSingleBean(Wso2UserStoreSoapClient.class);
            assertThat(context).hasSingleBean(KonnectIdentityClient.class);
        });
    }
}
