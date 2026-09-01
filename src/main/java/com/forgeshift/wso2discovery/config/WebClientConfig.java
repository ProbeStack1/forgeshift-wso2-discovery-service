package com.forgeshift.wso2discovery.config;

import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;
import java.time.Duration;

/**
 * WebClient used to call the WSO2 management plane.
 *
 * Single qualifier {@code wso2WebClient} so other components don't accidentally
 * pull the wrong instance once we add more downstream callers.
 */
@Slf4j
@Configuration
public class WebClientConfig {

    @Bean(name = "wso2WebClient")
    public WebClient wso2WebClient(Wso2Properties props) throws SSLException {
        HttpClient http = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(props.getRequestTimeoutSeconds()));

        if (props.isTrustSelfSigned()) {
            log.warn("WSO2 WebClient configured to trust all TLS certs. "
                    + "This is acceptable for local dev only.");
            http = http.secure(spec -> {
                try {
                    spec.sslContext(SslContextBuilder.forClient()
                            .trustManager(InsecureTrustManagerFactory.INSTANCE)
                            .build());
                } catch (SSLException e) {
                    throw new IllegalStateException("Failed to build insecure SSL context", e);
                }
            });
        }

        return WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(http))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    /**
     * WebClient for the Kong Konnect management API.
     *
     * <p>Deliberately has no {@code baseUrl}: the Konnect host and control
     * plane come from the per-company profile at request time, so callers pass
     * absolute URLs. It also never trusts self-signed certs -- Konnect is a
     * public endpoint with a valid chain, and the WSO2 client's dev-only
     * insecure mode must not leak onto it.
     */
    @Bean(name = "konnectWebClient")
    public WebClient konnectWebClient(Wso2Properties props) {
        HttpClient http = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(props.getKong().getRequestTimeoutSeconds()));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(http))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }
}
