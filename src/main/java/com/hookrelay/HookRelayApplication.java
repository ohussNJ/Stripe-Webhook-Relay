package com.hookrelay;

import com.hookrelay.config.HookRelayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(HookRelayProperties.class)
public class HookRelayApplication {

    public static void main(String[] args) {
        SpringApplication.run(HookRelayApplication.class, args);
    }
}
