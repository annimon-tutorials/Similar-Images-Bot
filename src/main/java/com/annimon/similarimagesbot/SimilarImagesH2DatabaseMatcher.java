package com.annimon.similarimagesbot;

import com.github.kilianB.matcher.persistent.database.H2DatabaseImageMatcher;
import java.sql.Connection;
import java.sql.SQLException;

public class SimilarImagesH2DatabaseMatcher extends H2DatabaseImageMatcher {

    public SimilarImagesH2DatabaseMatcher(Connection dbConnection) throws SQLException {
        super(dbConnection);
    }

    public void removeImage(String uniqueId) throws SQLException {
        for (var algo : steps.keySet()) {
            try (var stmt = conn
                    .prepareStatement("DELETE FROM " + resolveTableName(algo) + " WHERE URL = ?")) {
                stmt.setString(1, uniqueId);
                stmt.execute();
            }
        }
    }
}
