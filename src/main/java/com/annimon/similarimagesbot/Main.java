package com.annimon.similarimagesbot;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main {

    private static final Logger LOGGER = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        final String botToken = stringProp("BOT_TOKEN")
                .orElseThrow(() -> new IllegalArgumentException("BOT_TOKEN is required"));
        final ImageIndexer indexer = new ImageIndexer();
        final var handler = new BotHandler(botToken, indexer);
        handler.setAdminId(longProp("ADMIN_ID").orElse(0L));
        if (isOnceMode() || (args.length == 1 && args[0].equalsIgnoreCase("once"))) {
            LOGGER.info("Started in once mode");
            handler.runOnce();
        } else {
            LOGGER.info("Started in listen mode");
            handler.run();
        }
    }

    private static boolean isOnceMode() {
        final var mode = stringProp("MODE").orElse("once");
        return mode.equalsIgnoreCase("once");
    }

    private static Optional<String> stringProp(String name) {
        return Optional.ofNullable(System.getenv(name))
                .or(() -> Optional.ofNullable(System.getProperty(name)));
    }

    private static Optional<Long> longProp(String name) {
        return stringProp(name).map(Long::parseLong);
    }
}
