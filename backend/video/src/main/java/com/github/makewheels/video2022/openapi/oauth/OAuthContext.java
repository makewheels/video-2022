package com.github.makewheels.video2022.openapi.oauth;

import com.github.makewheels.video2022.openapi.oauth.entity.OAuthApp;

import java.util.List;

public class OAuthContext {
    private static final ThreadLocal<OAuthApp> currentApp = new ThreadLocal<>();
    private static final ThreadLocal<String> currentUserId = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> currentScopes = new ThreadLocal<>();

    public static void setCurrentApp(OAuthApp app) {
        currentApp.set(app);
    }

    public static OAuthApp getCurrentApp() {
        return currentApp.get();
    }

    public static void setCurrentUserId(String userId) {
        currentUserId.set(userId);
    }

    public static String getCurrentUserId() {
        return currentUserId.get();
    }

    public static void setCurrentScopes(List<String> scopes) {
        currentScopes.set(scopes);
    }

    public static List<String> getCurrentScopes() {
        return currentScopes.get();
    }

    // Convenience methods for backward compatibility
    public static String getClientId() {
        OAuthApp app = currentApp.get();
        return app != null ? app.getClientId() : null;
    }

    public static String getTier() {
        OAuthApp app = currentApp.get();
        return app != null ? app.getRateLimitTier() : null;
    }

    public static void remove() {
        currentApp.remove();
        currentUserId.remove();
        currentScopes.remove();
    }
}
