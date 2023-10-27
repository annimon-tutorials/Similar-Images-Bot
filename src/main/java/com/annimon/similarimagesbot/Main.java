package com.annimon.similarimagesbot;

import java.util.Optional;
import com.annimon.similarimagesbot.data.Settings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main {

    private static final Logger LOGGER = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        final String botToken = stringProp("BOT_TOKEN")
                .orElseThrow(() -> new IllegalArgumentException("BOT_TOKEN is required"));
        final long adminId = longProp("ADMIN_ID").orElse(0L);
        final boolean isOnceMode = isOnceMode() || (args.length == 1 && args[0].equalsIgnoreCase("once"));
        final boolean autoRemove = stringProp("AUTO_REMOVE")
                .map(s -> s.equalsIgnoreCase("true"))
                .orElse(false);

        final var settings = new Settings(botToken, adminId, isOnceMode, autoRemove);
        final var indexer = new ImageIndexer();
        final var handler = new BotHandler(settings, indexer);
        if (isOnceMode) {
            LOGGER.info("Started in once mode");
            handler.runOnce();
        } else {
            LOGGER.info("Started in listen mode");
            handler.run();
        }
    }

    private static boolean isOnceMode() {
        return stringProp("MODE")
                .map(s -> s.equalsIgnoreCase("once"))
                .orElse(true);
    }

    private static Optional<String> stringProp(String name) {
        return Optional.ofNullable(System.getenv(name))
                .or(() -> Optional.ofNullable(System.getProperty(name)));
    }

    private static Optional<Long> longProp(String name) {
        return stringProp(name).map(Long::parseLong);
    }
}
