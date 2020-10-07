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

public abstract class BaseBotHandler {

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
        bot.setUpdatesListener(updates -> {
            final var filteredUpdates = updates.stream()
                    .filter(u -> u.updateId() > oldLastUpdateId)
                    .collect(Collectors.toList());
            handleUpdates(filteredUpdates);
            int newLastUpdateId = getLastUpdateIdFromUpdatesList(updates, oldLastUpdateId);
            writeLastUpdateId(newLastUpdateId + 1);
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
    }

    public void runOnce() {
        int oldLastUpdateId = readLastUpdateId();
        final var updates = bot.execute(new GetUpdates().offset(oldLastUpdateId)).updates();
        handleUpdates(updates);
        int newLastUpdateId = getLastUpdateIdFromUpdatesList(updates, oldLastUpdateId);
        writeLastUpdateId(newLastUpdateId + 1);
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

    private int getLastUpdateIdFromUpdatesList(List<Update> updates, int previousUpdateId) {
        return updates.stream()
            .mapToInt(Update::updateId)
            .max()
            .orElse(previousUpdateId);
    }

    private int readLastUpdateId() {
        try {
            return Integer.parseInt(Files.readString(uniqueIdPath));
        } catch (IOException ioe) {
            return 0;
        }
    }

    private void writeLastUpdateId(int updateId) {
        try {
            Files.writeString(uniqueIdPath, Integer.toString(updateId));
        } catch (IOException ignore) {}
    }}
