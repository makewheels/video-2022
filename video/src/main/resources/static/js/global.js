(function () {
    'use strict';

    // ===== Theme Management =====

    function getStoredTheme() {
        return localStorage.getItem('theme');
    }

    function getSystemTheme() {
        return window.matchMedia('(prefers-color-scheme: dark)').matches
            ? 'dark'
            : 'light';
    }

    function applyTheme(theme) {
        document.documentElement.setAttribute('data-theme', theme);
        localStorage.setItem('theme', theme);
        updateToggleIcon(theme);
    }

    function updateToggleIcon(theme) {
        var toggle = document.getElementById('theme-toggle');
        if (toggle) {
            toggle.textContent = theme === 'dark' ? '☀️' : '🌙';
        }
    }

    function initTheme() {
        var stored = getStoredTheme();
        if (stored) {
            applyTheme(stored);
        } else {
            // Follow system preference without setting data-theme
            updateToggleIcon(getSystemTheme());
        }

        // Listen for system theme changes
        window
            .matchMedia('(prefers-color-scheme: dark)')
            .addEventListener('change', function (e) {
                if (!getStoredTheme()) {
                    updateToggleIcon(e.matches ? 'dark' : 'light');
                }
            });
    }

    function toggleTheme() {
        var current =
            document.documentElement.getAttribute('data-theme') ||
            getSystemTheme();
        var next = current === 'dark' ? 'light' : 'dark';
        applyTheme(next);
    }

    // ===== URL Helpers =====

    function getUrlVariable(key) {
        var params = new URLSearchParams(window.location.search);
        return params.get(key);
    }

    function isPC() {
        var ua = navigator.userAgent;
        return !/Android|iPhone/i.test(ua);
    }

    // ===== Auth =====

    function getToken() {
        return localStorage.getItem('token');
    }

    function requireAuth() {
        if (!getToken()) {
            jumpToLogin();
            return false;
        }
        return true;
    }

    function jumpToLogin() {
        var target = encodeURIComponent(window.location.href);
        window.location.href = '/login.html?target=' + target;
    }

    function authHeaders() {
        return { token: getToken() };
    }

    // ===== Init =====

    document.addEventListener('DOMContentLoaded', function () {
        initTheme();

        var toggle = document.getElementById('theme-toggle');
        if (toggle) {
            toggle.addEventListener('click', toggleTheme);
        }
    });

    // Export as window.VideoApp
    window.VideoApp = {
        getStoredTheme: getStoredTheme,
        getSystemTheme: getSystemTheme,
        applyTheme: applyTheme,
        toggleTheme: toggleTheme,
        getUrlVariable: getUrlVariable,
        isPC: isPC,
        getToken: getToken,
        requireAuth: requireAuth,
        jumpToLogin: jumpToLogin,
        authHeaders: authHeaders
    };
})();
