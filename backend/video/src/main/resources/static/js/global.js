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

    // ===== Toast Notifications =====

    function toast(message, type) {
        type = type || 'info';
        var container = document.getElementById('toast-container');
        if (!container) {
            container = document.createElement('div');
            container.id = 'toast-container';
            container.className = 'toast-container';
            document.body.appendChild(container);
        }
        var el = document.createElement('div');
        el.className = 'toast toast-' + type;
        el.textContent = message;
        container.appendChild(el);
        setTimeout(function () {
            el.style.animation = 'toast-out 0.3s ease forwards';
            setTimeout(function () {
                el.remove();
            }, 300);
        }, 2000);
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

    // ===== Nav Menu (Mobile) =====

    function initNavMenu() {
        var btn = document.getElementById('mobileMenuBtn');
        var menu = document.getElementById('navMenu');
        if (!btn || !menu) return;

        btn.addEventListener('click', function () {
            menu.classList.toggle('open');
            btn.textContent = menu.classList.contains('open') ? '✕' : '☰';
        });

        // Close menu when clicking a nav link
        var links = menu.querySelectorAll('.nav-link');
        for (var i = 0; i < links.length; i++) {
            links[i].addEventListener('click', function () {
                menu.classList.remove('open');
                btn.textContent = '☰';
            });
        }

        // Set active link based on current path
        var path = window.location.pathname;
        for (var j = 0; j < links.length; j++) {
            var href = links[j].getAttribute('href');
            links[j].classList.remove('active');
            if (path === href || (path === '/' && href === '/') ||
                (path === '/index.html' && href === '/')) {
                links[j].classList.add('active');
            }
        }
    }

    // ===== Header Auth =====

    function initHeaderAuth() {
        var container = document.getElementById('headerAuth');
        if (!container) return;

        if (getToken()) {
            container.innerHTML = '<span class="user-icon" title="已登录">👤</span>';
        } else {
            container.innerHTML = '<a href="/login.html">登录</a>';
        }
    }

    // ===== Init =====

    document.addEventListener('DOMContentLoaded', function () {
        initTheme();

        var toggle = document.getElementById('theme-toggle');
        if (toggle) {
            toggle.addEventListener('click', toggleTheme);
        }

        initNavMenu();
        initHeaderAuth();
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
        authHeaders: authHeaders,
        toast: toast,
        initNavMenu: initNavMenu,
        initHeaderAuth: initHeaderAuth
    };
})();
