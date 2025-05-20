package com.erp.aierpbackend.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.resolver.DefaultAddressResolverGroup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Configuration class for WebClient with custom DNS resolver and timeout settings.
 * This helps resolve DNS resolution issues and configure appropriate timeouts.
 */
@Configuration
@Slf4j
public class WebClientConfig {

    /**
     * Creates a WebClient.Builder bean with custom DNS resolver and timeout settings.
     * 
     * @return A configured WebClient.Builder
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        log.info("Configuring WebClient with custom DNS resolver and timeout settings");
        
        HttpClient httpClient = HttpClient.create()
                // Use DefaultAddressResolverGroup to fix DNS resolution issues
                .resolver(DefaultAddressResolverGroup.INSTANCE)
                // Set connection timeout to 10 seconds
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                // Set response timeout to 30 seconds
                .responseTimeout(Duration.ofSeconds(30))
                // Add handlers for read and write timeouts
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024));
    }
}
