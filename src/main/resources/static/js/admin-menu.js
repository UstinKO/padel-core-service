/**
 * Admin Panel - Burger Menu
 */

document.addEventListener('DOMContentLoaded', function() {
    'use strict';

    // Бургер-меню для админ-панели
    const navbarToggler = document.getElementById('navbarToggler');
    const navbarNav = document.getElementById('navbarNav');

    if (navbarToggler && navbarNav) {
        console.log('✅ Admin: найдены элементы меню');

        // Убираем старые обработчики
        const newToggler = navbarToggler.cloneNode(true);
        navbarToggler.parentNode.replaceChild(newToggler, navbarToggler);

        // Добавляем обработчик
        newToggler.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();

            navbarNav.classList.toggle('show');
            console.log('🍔 Admin menu:', navbarNav.classList.contains('show') ? 'abierto' : 'cerrado');
        });

        // Закрываем меню при клике на ссылку
        navbarNav.querySelectorAll('a').forEach(link => {
            link.addEventListener('click', () => {
                navbarNav.classList.remove('show');
            });
        });

        // Закрываем меню при клике вне его
        document.addEventListener('click', (e) => {
            if (navbarNav.classList.contains('show') &&
                !navbarNav.contains(e.target) &&
                !newToggler.contains(e.target)) {
                navbarNav.classList.remove('show');
            }
        });
    }
});