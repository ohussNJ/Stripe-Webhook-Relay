package com.hookrelay.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class WorkerConfig {

    @Bean
    public HttpClient httpClient(HookRelayProperties props) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.worker().httpTimeoutMs()))
                .build();
    }
}
