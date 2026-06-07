package io.github.martinwitt.todoapp.telegram;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

class TelegramRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        MemberCategory[] all = MemberCategory.values();

        // Telegram API objects — deserialized from JSON by Jackson
        hints.reflection()
                .registerType(Update.class, all)
                .registerType(Message.class, all)
                .registerType(CallbackQuery.class, all)
                .registerType(SendMessage.class, all)
                .registerType(EditMessageReplyMarkup.class, all)
                .registerType(InlineKeyboardMarkup.class, all)
                .registerType(InlineKeyboardButton.class, all)
                .registerType(InlineKeyboardRow.class, all);

        // Config binding
        hints.reflection()
                .registerType(TelegramProperties.class, all)
                .registerType(TelegramProperties.Bot.class, all)
                .registerType(TelegramProperties.User.class, all);

        // Event
        hints.reflection().registerType(SendTodosNowEvent.class, all);
    }
}
