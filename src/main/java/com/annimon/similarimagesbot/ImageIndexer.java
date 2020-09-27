package com.annimon.similarimagesbot;

import com.annimon.similarimagesbot.data.ImageResult;
import com.annimon.similarimagesbot.data.Post;
import com.annimon.similarimagesbot.data.SimilarImagesInfo;
import com.github.kilianB.hashAlgorithms.DifferenceHash;
import com.github.kilianB.hashAlgorithms.PerceptiveHash;
import com.github.kilianB.matcher.persistent.database.H2DatabaseImageMatcher;
import java.awt.image.BufferedImage;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ImageIndexer {

    private final Map<Long, H2DatabaseImageMatcher> databases;

    public ImageIndexer() {
        databases = new HashMap<>(5);
    }

    public SimilarImagesInfo processImage(Post originalPost, BufferedImage image)
            throws SQLException {
        final Long channelId = originalPost.getChannelId();
        final var db = getDatabaseForChannel(channelId);
        final List<ImageResult> results = db.getMatchingImages(image)
                .stream()
                .map(r -> {
                    final var similarPost = new Post(channelId, Integer.parseInt(r.value));
                    return new ImageResult(similarPost, r.distance);
                })
                .filter(r -> !r.isSamePost(originalPost))
                .collect(Collectors.toList());
        db.addImage(originalPost.getMessageId().toString(), image);
        return new SimilarImagesInfo(originalPost, results);
    }

    private H2DatabaseImageMatcher getDatabaseForChannel(Long channelId) throws SQLException {
        var db = databases.get(channelId);
        if (db != null) {
            return db;
        }
        var jdbcUrl = "jdbc:h2:./imagesdb_" + channelId;
        var conn = DriverManager.getConnection(jdbcUrl, "root", "");
        db = new H2DatabaseImageMatcher(conn);
        db.addHashingAlgorithm(new DifferenceHash(32, DifferenceHash.Precision.Double), .4);
        db.addHashingAlgorithm(new PerceptiveHash(32), .2);
        databases.put(channelId, db);
        return db;
    }
}
