package com.flyaway.spawnerchance;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class LanguageManager {

    private final SpawnerChance plugin;
    private final PlainTextComponentSerializer plainSerializer = PlainTextComponentSerializer.plainText();
    private ResourceBundle bundle;

    public LanguageManager(SpawnerChance plugin) {
        this.plugin = plugin;
    }

    public void load() {
        try {
            // Попытка загрузить файл перевода для русской локали
            bundle = ResourceBundle.getBundle("lang.ru_ru");

            plugin.getLogger().info("Загружен русский перевод");

        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка загрузки переводов: " + e.getMessage());
        }
    }

    // Перевод через ResourceBundle
    public Component translate(@NotNull TranslatableComponent key, @NotNull Locale locale) {
        if (locale.toString().toLowerCase().contains("ru") && bundle != null) {
            // Получаем перевод по ключу
            try {
                String result = bundle.getString(key.key());
                return Component.text(result);
            } catch (MissingResourceException e) {
                // Если ключ не найден, возвращаем исходный ключ
                plugin.getLogger().warning("Не найден перевод для ключа: " + key.key());
            }
        }

        // Если не найден перевод для русской локали, используем fallback
        return GlobalTranslator.render(key, locale);
    }

    public String translateToString(@NotNull TranslatableComponent key, @NotNull Locale locale) {
        return plainSerializer.serialize(translate(key, locale));
    }
}
