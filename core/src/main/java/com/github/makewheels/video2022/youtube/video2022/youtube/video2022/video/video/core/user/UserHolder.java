package com.github.makewheels.video2022.youtube.video2022.youtube.video2022.video.video.core.user;

import com.github.makewheels.video2022.user.bean.User;

public class UserHolder {
    private static final ThreadLocal<User> THREAD_LOCAL = new ThreadLocal<>();

    public static void set(User user) {
        THREAD_LOCAL.set(user);
    }

    public static User get() {
        return THREAD_LOCAL.get();
    }

    public static String getUserId() {
        return get().getId();
    }

    public static void remove() {
        THREAD_LOCAL.remove();
    }
}
