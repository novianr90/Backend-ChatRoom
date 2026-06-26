package com.chatLMS.ChatRoom.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "chatTaskExecutor")
    public Executor threadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5); // 5 pekerja standby
        executor.setMaxPoolSize(15); // Maksimal 15 pekerja saat ramai
        executor.setQueueCapacity(200); // Batas antrean RAM
        executor.setThreadNamePrefix("LMSChatWorker-");
        executor.initialize();
        return executor;
    }
}