/**
 * Admin Panel - Language Switcher (LFPT-0542)
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
