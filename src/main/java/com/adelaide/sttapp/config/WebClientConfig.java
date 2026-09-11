package com.adelaide.sttapp.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.transport.ProxyProvider;

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
 *
 * PROXY NOTE: some sandboxed environments (e.g. TITAN) have no direct
 * internet access and require outbound calls to go through an HTTP proxy,
 * advertised via the standard http.proxyHost/https.proxyHost JVM system
 * properties (commonly set via JAVA_TOOL_OPTIONS). Reactor Netty's
 * HttpClient, unlike the JDK's default HttpURLConnection-based client,
 * does NOT auto-detect these properties - it has to be told explicitly.
 * We check for them at startup and configure the proxy only if present, so
 * this remains a no-op on a normal machine with no proxy configured.
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

        httpClient = applyProxyIfConfigured(httpClient);

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB, room for short audio clips
                .build();
    }

    private HttpClient applyProxyIfConfigured(HttpClient httpClient) {
        // We call OpenAI over https, so https.proxyHost/Port take priority;
        // fall back to the http.* ones if that's all that's set.
        String proxyHost = System.getProperty("https.proxyHost", System.getProperty("http.proxyHost"));
        String proxyPortStr = System.getProperty("https.proxyPort", System.getProperty("http.proxyPort"));

        if (proxyHost == null || proxyHost.isBlank()) {
            return httpClient; // no proxy configured - normal local/dev environment
        }

        int proxyPort = 80;
        if (proxyPortStr != null && !proxyPortStr.isBlank()) {
            try {
                proxyPort = Integer.parseInt(proxyPortStr);
            } catch (NumberFormatException ignored) {
                // fall back to default port 80 above
            }
        }

        final String host = proxyHost;
        final int port = proxyPort;
        return httpClient.proxy(spec -> spec.type(ProxyProvider.Proxy.HTTP).host(host).port(port));
    }
}