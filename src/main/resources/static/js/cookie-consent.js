/**
 * Cookie Consent Manager
 * Управляет отображением и поведением плашки согласия на cookies
 */

(function() {
    'use strict';

    // Проверяем, есть ли уже согласие
    function hasCookieConsent() {
        return document.cookie.split(';').some((item) => item.trim().startsWith('cookieConsent='));
    }

    // Получаем значение cookie
    function getCookie(name) {
        const value = `; ${document.cookie}`;
        const parts = value.split(`; ${name}=`);
        if (parts.length === 2) return parts.pop().split(';').shift();
    }

    // Показываем плашку
    function showCookieBanner() {
        // Удаляем существующий баннер если есть
        const existingBanner = document.getElementById('cookieConsentBanner');
        if (existingBanner) {
            existingBanner.remove();
        }

        const bannerHTML = `
            <div id="cookieConsentBanner" class="cookie-banner">
                <div class="cookie-banner-content">
                    <div class="cookie-banner-text">
                        <h3>🍪 Uso de cookies</h3>
                        <p>Utilizamos cookies propias y de terceros para mejorar tu experiencia en nuestra plataforma, personalizar contenido y analizar nuestro tráfico. Puedes aceptar todas las cookies, rechazarlas o personalizar tu configuración.</p>
                        <p class="cookie-links">
                            <a href="/cookies" target="_blank">Más información sobre cookies</a> | 
                            <a href="/privacidad" target="_blank">Política de privacidad</a>
                        </p>
                    </div>
                    <div class="cookie-banner-buttons">
                        <button class="cookie-btn cookie-btn-reject" onclick="rejectCookies()">
                            Rechazar todas
                        </button>
                        <button class="cookie-btn cookie-btn-customize" onclick="showCookieSettings()">
                            Personalizar
                        </button>
                        <button class="cookie-btn cookie-btn-accept" onclick="acceptCookies()">
                            Aceptar todas
                        </button>
                    </div>
                </div>
            </div>
        `;

        document.body.insertAdjacentHTML('beforeend', bannerHTML);
    }

    // Показываем настройки cookies
    window.showCookieSettings = function() {
        const banner = document.getElementById('cookieConsentBanner');
        if (!banner) return;

        banner.innerHTML = `
            <div class="cookie-banner-content cookie-settings">
                <div class="cookie-banner-text">
                    <h3>⚙️ Personalizar cookies</h3>
                    <p>Selecciona qué tipos de cookies aceptas:</p>
                </div>
                <div class="cookie-options">
                    <div class="cookie-option">
                        <div class="cookie-option-header">
                            <span class="cookie-option-title">Cookies necesarias</span>
                            <span class="cookie-option-badge">Siempre activas</span>
                        </div>
                        <p class="cookie-option-description">Estas cookies son esenciales para el funcionamiento básico del sitio. No pueden ser desactivadas.</p>
                    </div>
                    
                    <div class="cookie-option">
                        <div class="cookie-option-header">
                            <span class="cookie-option-title">Cookies de análisis</span>
                            <label class="cookie-switch">
                                <input type="checkbox" id="analyticsCookies" checked>
                                <span class="cookie-slider"></span>
                            </label>
                        </div>
                        <p class="cookie-option-description">Nos ayudan a entender cómo los visitantes interactúan con el sitio, permitiéndonos mejorar tu experiencia.</p>
                    </div>
                    
                    <div class="cookie-option">
                        <div class="cookie-option-header">
                            <span class="cookie-option-title">Cookies de marketing</span>
                            <label class="cookie-switch">
                                <input type="checkbox" id="marketingCookies">
                                <span class="cookie-slider"></span>
                            </label>
                        </div>
                        <p class="cookie-option-description">Se utilizan para mostrar anuncios relevantes y medir la efectividad de nuestras campañas.</p>
                    </div>
                </div>
                
                <div class="cookie-banner-buttons">
                    <button class="cookie-btn cookie-btn-outline" onclick="showCookieBannerSimple()">
                        ← Volver
                    </button>
                    <button class="cookie-btn cookie-btn-accept" onclick="saveCookieSettings()">
                        Guardar configuración
                    </button>
                </div>
            </div>
        `;
    };

    // Возврат к простому баннеру
    window.showCookieBannerSimple = function() {
        showCookieBanner();
    };

    // Принять все cookies
    window.acceptCookies = function() {
        fetch('/api/cookies/accept', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        })
            .then(() => {
                const banner = document.getElementById('cookieConsentBanner');
                if (banner) {
                    banner.style.animation = 'slideOut 0.3s ease';
                    setTimeout(() => banner.remove(), 300);
                }
                console.log('Cookies aceptadas');
            })
            .catch(error => console.error('Error:', error));
    };

    // Отклонить все cookies
    window.rejectCookies = function() {
        fetch('/api/cookies/reject', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        })
            .then(() => {
                const banner = document.getElementById('cookieConsentBanner');
                if (banner) {
                    banner.style.animation = 'slideOut 0.3s ease';
                    setTimeout(() => banner.remove(), 300);
                }
                console.log('Cookies rechazadas');
            })
            .catch(error => console.error('Error:', error));
    };

    // Сохранить настройки cookies
    window.saveCookieSettings = function() {
        const analytics = document.getElementById('analyticsCookies')?.checked || false;
        const marketing = document.getElementById('marketingCookies')?.checked || false;

        fetch(`/api/cookies/customize?analytics=${analytics}&marketing=${marketing}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        })
            .then(() => {
                const banner = document.getElementById('cookieConsentBanner');
                if (banner) {
                    banner.style.animation = 'slideOut 0.3s ease';
                    setTimeout(() => banner.remove(), 300);
                }
                console.log('Configuración de cookies guardada');
            })
            .catch(error => console.error('Error:', error));
    };

    // Инициализация при загрузке страницы
    document.addEventListener('DOMContentLoaded', function() {
        if (!hasCookieConsent()) {
            setTimeout(() => showCookieBanner(), 1000);
        }
    });
})();