/**
 * Admin Panel - Language Switcher
 */

document.addEventListener('DOMContentLoaded', function() {
    'use strict';

    document.querySelectorAll('.lang-switcher-option').forEach(function(button) {
        button.addEventListener('click', function() {
            var lang = button.getAttribute('data-lang');
            var params = new URLSearchParams(window.location.search);
            params.set('lang', lang);
            window.location.href = window.location.pathname + '?' + params.toString();
        });
    });
});
