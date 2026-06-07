package io.github.martinwitt.todoapp.telegram;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(TelegramProperties.class)
@ImportRuntimeHints(TelegramRuntimeHints.class)
class TelegramConfig {

    @Bean
    TelegramClient telegramClient(TelegramProperties props) {
        return new OkHttpTelegramClient(props.bot().token());
    }
}
