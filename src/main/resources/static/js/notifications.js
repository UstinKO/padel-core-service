// notifications.js - единая система уведомлений для всего проекта

const NotificationSystem = {
    // Контейнер для уведомлений
    container: null,

    init() {
        // Создаем контейнер при инициализации
        if (!this.container) {
            this.container = document.createElement('div');
            this.container.id = 'notification-container';
            this.container.style.cssText = `
                position: fixed;
                top: 20px;
                right: 20px;
                z-index: 9999;
                display: flex;
                flex-direction: column;
                gap: 10px;
                pointer-events: none;
            `;
            document.body.appendChild(this.container);
        }

        // Добавляем стили для анимаций
        const style = document.createElement('style');
        style.textContent = `
            @keyframes notificationSlideIn {
                from {
                    transform: translateX(100%);
                    opacity: 0;
                }
                to {
                    transform: translateX(0);
                    opacity: 1;
                }
            }
            
            @keyframes notificationSlideOut {
                from {
                    transform: translateX(0);
                    opacity: 1;
                }
                to {
                    transform: translateX(100%);
                    opacity: 0;
                }
            }
            
            .notification {
                pointer-events: auto;
                min-width: 300px;
                max-width: 400px;
                padding: 16px 20px;
                border-radius: 8px;
                color: white;
                font-size: 14px;
                font-weight: 500;
                display: flex;
                align-items: center;
                gap: 12px;
                box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
                animation: notificationSlideIn 0.3s ease;
                cursor: pointer;
            }
            
            .notification:hover {
                filter: brightness(0.95);
            }
            
            .notification i {
                font-size: 20px;
            }
            
            .notification-content {
                flex: 1;
            }
            
            .notification-close {
                opacity: 0.7;
                transition: opacity 0.2s;
            }
            
            .notification-close:hover {
                opacity: 1;
            }
            
            .notification-success {
                background: linear-gradient(135deg, #10b981, #059669);
            }
            
            .notification-error {
                background: linear-gradient(135deg, #ef4444, #dc2626);
            }
            
            .notification-warning {
                background: linear-gradient(135deg, #f59e0b, #d97706);
            }
            
            .notification-info {
                background: linear-gradient(135deg, #3b82f6, #2563eb);
            }
        `;
        document.head.appendChild(style);
    },

    show(message, type = 'info', duration = 3000) {
        this.init();

        const notification = document.createElement('div');
        notification.className = `notification notification-${type}`;
        notification.onclick = () => this.close(notification);

        // Иконки для разных типов
        const icons = {
            success: 'fa-check-circle',
            error: 'fa-exclamation-circle',
            warning: 'fa-exclamation-triangle',
            info: 'fa-info-circle'
        };

        notification.innerHTML = `
            <i class="fas ${icons[type] || icons.info}"></i>
            <span class="notification-content">${message}</span>
            <i class="fas fa-times notification-close"></i>
        `;

        this.container.appendChild(notification);

        // Автоматическое закрытие
        if (duration > 0) {
            setTimeout(() => this.close(notification), duration);
        }

        return notification;
    },

    close(notification) {
        notification.style.animation = 'notificationSlideOut 0.3s ease';
        setTimeout(() => {
            if (notification.parentNode) {
                notification.remove();
            }
        }, 300);
    },

    // Удобные методы для разных типов
    success(message, duration) {
        return this.show(message, 'success', duration);
    },

    error(message, duration) {
        return this.show(message, 'error', duration);
    },

    warning(message, duration) {
        return this.show(message, 'warning', duration);
    },

    info(message, duration) {
        return this.show(message, 'info', duration);
    }
};

// Глобальная функция для обратной совместимости
function showNotification(message, type = 'info') {
    return NotificationSystem.show(message, type);
}

// Инициализация при загрузке
document.addEventListener('DOMContentLoaded', () => {
    NotificationSystem.init();
});