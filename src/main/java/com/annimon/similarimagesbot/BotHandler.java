package com.annimon.similarimagesbot;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import com.annimon.similarimagesbot.data.ImageResult;
import com.annimon.similarimagesbot.data.Post;
import com.annimon.similarimagesbot.data.SimilarImagesInfo;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.PhotoSize;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InputMediaPhoto;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.DeleteMessage;
import com.pengrad.telegrambot.request.ForwardMessage;
import com.pengrad.telegrambot.request.GetFile;
import com.pengrad.telegrambot.request.SendMediaGroup;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;

public class BotHandler extends BaseBotHandler {

    private final Pattern delPattern = Pattern.compile("/del(\\d+)m(\\d+)");
    private final Pattern comparePattern = Pattern.compile("/compare(\\d+)m(\\d+)x(\\d+)");

    private final ImageIndexer indexer;
    private long adminId;

    public BotHandler(String botToken, ImageIndexer indexer) {
        super(botToken);
        this.indexer = indexer;
    }

    public void setAdminId(long adminId) {
        this.adminId = adminId;
    }

    protected void handleUpdates(List<Update> updates) {
        final var removedPosts = processAdminCommands(updates);
        processUpdates(updates, removedPosts);
    }

    private Set<Post> processAdminCommands(List<Update> updates) {
        return updates.stream()
                .map(Update::message)
                .filter(Objects::nonNull)
                .filter(msg -> msg.chat().id() == adminId)
                .map(Message::text)
                .filter(Objects::nonNull)
                .map(command -> Optional.<Post>empty()
                        .or(() -> processDelCommand(delPattern.matcher(command)))
                        .or(() -> processCompareCommand(comparePattern.matcher(command)))
                        .orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Optional<Post> processDelCommand(Matcher m) {
        if (!m.find()) {
            return Optional.empty();
        }
        final var channelId = Long.parseLong("-100" + m.group(1));
        final var messageId = Integer.parseInt(m.group(2));
        LOGGER.debug("Delete message {} in {}", messageId, channelId);
        bot.execute(new DeleteMessage(channelId, messageId));
        try {
            indexer.deleteImage(channelId, messageId);
        } catch (SQLException ex) {
            LOGGER.error("Cannot delete image in db", ex);
        }
        return Optional.of(new Post(channelId, messageId));
    }

    private Optional<Post> processCompareCommand(Matcher m) {
        if (!m.find()) {
            return Optional.empty();
        }
        final var channelId = Long.parseLong("-100" + m.group(1));
        final var messageA = Integer.parseInt(m.group(2));
        final var messageB = Integer.parseInt(m.group(3));
        LOGGER.debug("Compare messages {} and {} in {}", messageA, messageB, channelId);

        // Forward and get photo to compare
        var sentA = bot.execute(new ForwardMessage(adminId, channelId, messageA));
        var sentB = bot.execute(new ForwardMessage(adminId, channelId, messageB));
        final Predicate<SendResponse> hasPhoto = (r) -> r.isOk() && r.message().photo() != null;
        if (hasPhoto.test(sentA) && hasPhoto.test(sentB)) {
            final var photoA = getBiggestPhoto(sentA.message().photo());
            final var photoB = getBiggestPhoto(sentB.message().photo());
            bot.execute(new SendMediaGroup(adminId,
                    new InputMediaPhoto(photoA.fileId()).caption("Post " + messageA),
                    new InputMediaPhoto(photoB.fileId()).caption("Post " + messageB) ));
        }
        // Clean up if one of the images already deleted
        if (sentA.message() != null) {
            bot.execute(new DeleteMessage(adminId, sentA.message().messageId()));
        }
        if (sentB.message() != null) {
            bot.execute(new DeleteMessage(adminId, sentB.message().messageId()));
        }
        return Optional.empty();
    }

    private void processUpdates(List<Update> updates, Set<Post> ignoredPosts) {
        final List<Message> channelPosts = getChannelPostsWithPhotos(updates);
        final var similarImagesInfos = new ArrayList<SimilarImagesInfo>();
        for (var post : channelPosts) {
            final var originalPost = new Post(post.chat().id(), post.messageId());
            if (ignoredPosts.contains(originalPost)) continue;

            final PhotoSize photo = getSmallestPhoto(post.photo());
            try {
                final var tgFile = bot.execute(new GetFile(photo.fileId())).file();
                final var url = new URL(bot.getFullFilePath(tgFile));
                final BufferedImage image = ImageIO.read(url);
                final SimilarImagesInfo info = indexer.processImage(originalPost, image);
                if (info.hasResults()) {
                    similarImagesInfos.add(info);
                }
            } catch (IOException | SQLException e) {
                LOGGER.error("Error while processing photo in {}", linkToMessage(post));
            }
        }
        if (!similarImagesInfos.isEmpty()) {
            sendReport(similarImagesInfos);
        }
    }

    private void sendReport(List<SimilarImagesInfo> infos) {
        String report = infos.stream().map(info -> {
            final var post = info.getOriginalPost();
            final var channelId = post.getChannelId().toString().replace("-100", "");
            String text = "For post " + formatPostLink(post) + " found:\n";
            // Matching results
            text += info.getResults().stream()
                    .map(r -> String.format("  %s, dst: %.2f", formatPostLink(r.getPost()), r.getDistance()))
                    .collect(Collectors.joining("\n"));
            // /compare command
            text += info.getResults().stream()
                    .map(ImageResult::getPost)
                    .map(p -> String.format("%n/compare%sm%dx%d",
                            channelId, post.getMessageId(), p.getMessageId()))
                    .collect(Collectors.joining());
            // /del command
            text += String.format("%n/del%sm%d", channelId, post.getMessageId());
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
}
