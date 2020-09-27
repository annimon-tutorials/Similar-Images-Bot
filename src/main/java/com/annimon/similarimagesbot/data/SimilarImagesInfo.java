package com.annimon.similarimagesbot.data;

import java.util.List;

public class SimilarImagesInfo {

    private final Post originalPost;
    private final List<ImageResult> results;

    public SimilarImagesInfo(Post originalPost, List<ImageResult> results) {
        this.originalPost = originalPost;
        this.results = results;
    }

    public boolean hasResults() {
        return !results.isEmpty();
    }

    public Post getOriginalPost() {
        return originalPost;
    }

    public List<ImageResult> getResults() {
        return results;
    }
}
