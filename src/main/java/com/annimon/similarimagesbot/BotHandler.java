package com.annimon.similarimagesbot;

import com.annimon.similarimagesbot.data.Post;
import com.annimon.similarimagesbot.data.SimilarImagesInfo;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.PhotoSize;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.GetFile;
import com.pengrad.telegrambot.request.SendMessage;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

public class BotHandler {

    private final TelegramBot bot;
    private final ImageIndexer indexer;
    private long adminId;

    public BotHandler(String botToken, ImageIndexer indexer) {
        bot = new TelegramBot.Builder(botToken)
                .updateListenerSleep(20_000L)
                .build();
        this.indexer = indexer;
    }

    public void setAdminId(long adminId) {
        this.adminId = adminId;
    }

    public void run() {
        bot.setUpdatesListener(updates -> {
            final List<Message> channelPosts = updates.stream()
                    .map(Update::channelPost)
                    .filter(Objects::nonNull)
                    .filter(msg -> msg.photo() != null)
                    .collect(Collectors.toList());

            final var similarImagesInfos = new ArrayList<SimilarImagesInfo>();
            for (var post : channelPosts) {
                final PhotoSize photo = getSmallestPhoto(post.photo());
                try {
                    final var tgFile = bot.execute(new GetFile(photo.fileId())).file();
                    final var url = new URL(bot.getFullFilePath(tgFile));
                    final BufferedImage image = ImageIO.read(url);
                    final var originalPost = new Post(post.chat().id(), post.messageId());
                    final SimilarImagesInfo info = indexer.processImage(originalPost, image);
                    if (info.hasResults()) {
                        similarImagesInfos.add(info);
                    }
                } catch (IOException | SQLException e) {
                    System.err.format("Error while processing photo in %s%n", linkToMessage(post));
                }
            }
            if (!similarImagesInfos.isEmpty()) {
                sendReport(similarImagesInfos);
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
    }

    private void sendReport(List<SimilarImagesInfo> infos) {
        String report = infos.stream().map(info -> {
            String text = "For post " + formatPostLink(info.getOriginalPost()) + " found:\n";
            text += info.getResults().stream()
                    .map(r -> String.format("  %s, dst: %.2f", formatPostLink(r.getPost()), r.getDistance()))
                    .collect(Collectors.joining("\n"));
            return text;
        }).collect(Collectors.joining("\n\n"));

        if (adminId == 0) {
            System.out.println(report);
        } else {
            bot.execute(new SendMessage(adminId, report).parseMode(ParseMode.Markdown));
        }
    }

    private String formatPostLink(Post post) {
        String link = linkToMessage(post.getChannelId(), post.getMessageId());
        return String.format("[#%d](%s)", post.getMessageId(), link);
    }

    private String linkToMessage(Message msg) {
        return linkToMessage(msg.chat().id(), msg.messageId());
    }

    private String linkToMessage(Long chatId, Integer messageId) {
        return "https://t.me/c/" + chatId.toString().replace("-100", "") + "/" + messageId;
    }

    private PhotoSize getSmallestPhoto(PhotoSize[] photoSizes) {
        return Arrays.stream(photoSizes)
                .min(Comparator.comparingInt(ps -> ps.width() * ps.height()))
                .orElse(photoSizes[0]);
    }
}
