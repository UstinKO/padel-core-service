/**
 * Payment Management - E-Padel Admin
 */

document.addEventListener('DOMContentLoaded', function() {
    'use strict';

    // ===== БУРГЕР-МЕНЮ =====
    initBurgerMenu();

    // ===== АВТОМАТИЧЕСКОЕ ОБНОВЛЕНИЕ СТАТУСА =====
    const amountInputs = document.querySelectorAll('input[name^="amounts"]');
    amountInputs.forEach(input => {
        input.addEventListener('input', function() {
            const row = this.closest('tr');
            const statusSelect = row.querySelector('select[name^="paymentStatuses"]');
            if (this.value && parseFloat(this.value) > 0 && !statusSelect.value) {
                statusSelect.value = 'PAID';
            }
        });
    });

    // ===== ПОДТВЕРЖДЕНИЕ ПЕРЕД ОТПРАВКОЙ =====
    const form = document.getElementById('paymentForm');
    if (form) {
        form.addEventListener('submit', function(e) {
            if (!confirm('¿Guardar los cambios de pagos y asistencia?')) {
                e.preventDefault();
            }
        });
    }

    // ===== ФУНКЦИЯ ДЛЯ БУРГЕР-МЕНЮ =====
    function initBurgerMenu() {
        const navbarToggler = document.getElementById('navbarToggler');
        const navbarNav = document.getElementById('navbarNav');

        if (navbarToggler && navbarNav) {
            console.log('✅ Payment page: найдены элементы меню');

            // Убираем старые обработчики
            const newToggler = navbarToggler.cloneNode(true);
            navbarToggler.parentNode.replaceChild(newToggler, navbarToggler);

            // Добавляем обработчик
            newToggler.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();

                navbarNav.classList.toggle('show');
                newToggler.classList.toggle('active');

                console.log('🍔 Меню:', navbarNav.classList.contains('show') ? 'abierto' : 'cerrado');
            });

            // Закрываем меню при клике на ссылку
            navbarNav.querySelectorAll('a').forEach(link => {
                link.addEventListener('click', () => {
                    navbarNav.classList.remove('show');
                    newToggler.classList.remove('active');
                });
            });

            // Закрываем меню при клике вне его
            document.addEventListener('click', (e) => {
                if (navbarNav.classList.contains('show') &&
                    !navbarNav.contains(e.target) &&
                    !newToggler.contains(e.target)) {
                    navbarNav.classList.remove('show');
                    newToggler.classList.remove('active');
                }
            });
        } else {
            console.warn('⚠️ Payment page: элементы меню не найдены');
        }
    }
});