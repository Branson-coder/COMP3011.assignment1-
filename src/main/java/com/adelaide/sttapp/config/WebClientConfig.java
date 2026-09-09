package com.adelaide.sttapp.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

/**
 * Central WebClient used to call the third-party OpenAI Cloud STT API.
 *
 * WebClient is built on Reactor Netty and issues requests without blocking
 * a servlet/container thread while waiting on the network round trip. This
 * is what lets us accept > 200 concurrent uploads on a small Tomcat thread
 * pool without those threads sitting idle blocked on I/O: each incoming
 * request hands its outbound call to this client and the servlet thread is
 * released back to the pool until the response arrives.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient openAiWebClient() {
        ConnectionProvider provider = ConnectionProvider.builder("openai-stt-pool")
                .maxConnections(250)
                .pendingAcquireMaxCount(500)
                .pendingAcquireTimeout(Duration.ofSeconds(10))
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
                .responseTimeout(Duration.ofSeconds(15));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB, room for short audio clips
                .build();
    }
}
