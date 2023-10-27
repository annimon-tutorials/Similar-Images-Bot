package com.annimon.similarimagesbot.data;

public class Settings {
    private final String botToken;
    private final long adminId;
    private final boolean onceMode;
    private final boolean autoRemove;

    public Settings(String botToken, long adminId, boolean onceMode, boolean autoRemove) {
        this.botToken = botToken;;
        this.adminId = adminId;
        this.onceMode = onceMode;
        this.autoRemove = autoRemove;
    }

    public String getBotToken() {
        return botToken;
    }

    public long getAdminId() {
        return adminId;
    }

    public boolean isOnceMode() {
        return onceMode;
    }

    public boolean isAutoRemove() {
        return autoRemove;
    }
}
