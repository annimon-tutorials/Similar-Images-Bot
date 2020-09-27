package com.annimon.similarimagesbot.data;

public class ImageResult {

    private final Post post;
    private final double distance;

    public ImageResult(Post post, double distance) {
        this.post = post;
        this.distance = distance;
    }

    public Post getPost() {
        return post;
    }

    public double getDistance() {
        return distance;
    }

    public boolean isSamePost(Post other) {
        return post.equals(other);
    }
}
