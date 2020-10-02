package com.annimon.similarimagesbot.data;

import java.util.Objects;

public class Post {

    private final Long channelId;
    private final Integer messageId;

    public Post(Long channelId, Integer messageId) {
        this.channelId = channelId;
        this.messageId = messageId;
    }

    public Long getChannelId() {
        return channelId;
    }

    public Integer getMessageId() {
        return messageId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Post post = (Post) o;
        return Objects.equals(channelId, post.channelId) &&
                Objects.equals(messageId, post.messageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channelId, messageId);
    }

    @Override
    public String toString() {
        return "{" + channelId + ":" + messageId + "}";
    }
}
