package com.annimon.similarimagesbot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.PhotoSize;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.GetUpdates;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class BaseBotHandler {

    protected static final Logger LOGGER = LogManager.getLogger(BaseBotHandler.class);

    private final Comparator<PhotoSize> photoSizeComparator = Comparator
            .comparingInt(ps -> ps.width() * ps.height());

    private final Path uniqueIdPath = Paths.get("uniqueId.dat");

    protected final TelegramBot bot;

    public BaseBotHandler(String botToken) {
        bot = new TelegramBot.Builder(botToken)
                .updateListenerSleep(20_000L)
                .build();
    }

    public void run() {
        int oldLastUpdateId = readLastUpdateId();
        LOGGER.debug("Start updates listener from {}", oldLastUpdateId);
        bot.setUpdatesListener(updates -> {
            final var filteredUpdates = updates.stream()
                    .filter(u -> u.updateId() > oldLastUpdateId)
                    .collect(Collectors.toList());
            handleUpdates(filteredUpdates);
            int nextLastUpdateId = geNextUpdateId(updates, oldLastUpdateId);
            writeNextUpdateId(nextLastUpdateId);
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
    }

    public void runOnce() {
        int oldLastUpdateId = readLastUpdateId();
        LOGGER.debug("Get updates from {}", oldLastUpdateId);
        final var updates = bot.execute(new GetUpdates().offset(oldLastUpdateId)).updates();
        LOGGER.debug("Handle {} updates", updates.size());
        handleUpdates(updates);
        int newLastUpdateId = geNextUpdateId(updates, oldLastUpdateId);
        writeNextUpdateId(newLastUpdateId);
    }

    protected abstract void handleUpdates(List<Update> updates);

    protected List<Message> getChannelPostsWithPhotos(List<Update> updates) {
        return updates.stream()
                .map(Update::channelPost)
                .filter(Objects::nonNull)
                .filter(msg -> msg.photo() != null)
                .collect(Collectors.toList());
    }

    protected PhotoSize getSmallestPhoto(PhotoSize[] photoSizes) {
        return Arrays.stream(photoSizes)
                .min(photoSizeComparator)
                .orElse(photoSizes[0]);
    }

    protected PhotoSize getBiggestPhoto(PhotoSize[] photoSizes) {
        return Arrays.stream(photoSizes)
                .max(photoSizeComparator)
                .orElse(photoSizes[0]);
    }

    private int geNextUpdateId(List<Update> updates, int previousUpdateId) {
        final int lastUpdateId = updates.stream()
            .mapToInt(Update::updateId)
            .max()
            .orElse(previousUpdateId);
        final int nextUpdateId; 
        if (lastUpdateId != previousUpdateId) {
            nextUpdateId = lastUpdateId + 1;
        } else {
            nextUpdateId = lastUpdateId;
        }
        return nextUpdateId;
    }

    private int readLastUpdateId() {
        try {
            final String content = Files.readString(uniqueIdPath);
            return Integer.parseInt(content.trim());
        } catch (IOException ioe) {
            LOGGER.error("readLastUpdateId", ioe);
            return 0;
        }
    }

    private void writeNextUpdateId(int updateId) {
        try {
            Files.writeString(uniqueIdPath, Integer.toString(updateId));
        } catch (IOException ioe) {
            LOGGER.error("writeLastUpdateId", ioe);
        }
    }}
