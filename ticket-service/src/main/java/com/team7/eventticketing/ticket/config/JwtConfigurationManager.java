package com.team7.eventticketing.ticket.config;

public class JwtConfigurationManager {

    private static volatile JwtConfigurationManager instance;

    private final String secret;
    private final long expirationMs;

    private JwtConfigurationManager() {
        String envSecret = System.getenv("JWT_SECRET");
        String envExpiry = System.getenv("JWT_EXPIRATION_MS");

        this.secret = (envSecret != null && !envSecret.isBlank())
                ? envSecret
                : "XTAA/1w8x3EU1gymE8NZpczBPdPcPeT3UpsSA2I+Utw=";

        this.expirationMs = (envExpiry != null && !envExpiry.isBlank())
                ? Long.parseLong(envExpiry)
                : 86400000L;
    }

    // Double-checked locking — thread-safe lazy init
    public static JwtConfigurationManager getInstance() {
        if (instance == null) {
            synchronized (JwtConfigurationManager.class) {
                if (instance == null) {
                    instance = new JwtConfigurationManager();
                }
            }
        }
        return instance;
    }

    public String getSecret() {
        return secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}