package io.github.martinwitt.todoapp.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "telegram")
public record TelegramProperties(Bot bot, User user) {
    public record Bot(String token, String username) {}

    public record User(long chatId) {}
}
