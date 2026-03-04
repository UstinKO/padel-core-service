/**
 * Padel Core - Admin Panel JavaScript
 */

class AdminPanel {
    constructor() {
        this.initializeEventListeners();
        this.loadDashboardData();
    }

    initializeEventListeners() {
        // Удаление элементов с подтверждением
        document.querySelectorAll('.delete-confirm').forEach(button => {
            button.addEventListener('click', (e) => this.handleDelete(e));
        });

        // Изменение статуса
        document.querySelectorAll('.status-change').forEach(select => {
            select.addEventListener('change', (e) => this.handleStatusChange(e));
        });

        // Фильтры
        const filterForm = document.getElementById('adminFilters');
        if (filterForm) {
            filterForm.addEventListener('submit', (e) => {
                e.preventDefault();
                this.applyFilters();
            });
        }
    }

    handleDelete(event) {
        event.preventDefault();
        const button = event.currentTarget;
        const url = button.dataset.url;
        const name = button.dataset.name || 'elemento';

        if (confirm(`¿Estás seguro de eliminar ${name}?`)) {
            this.deleteItem(url, button);
        }
    }

    async deleteItem(url, button) {
        const originalText = button.innerHTML;
        button.disabled = true;
        button.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';

        try {
            const response = await fetch(url, {
                method: 'DELETE',
                headers: {
                    'Content-Type': 'application/json',
                }
            });

            if (response.ok) {
                this.showNotification('Elemento eliminado correctamente', 'success');
                // Удаляем строку из таблицы или перезагружаем
                const row = button.closest('tr');
                if (row) {
                    row.remove();
                } else {
                    window.location.reload();
                }
            } else {
                const data = await response.json();
                this.showNotification(data.message || 'Error al eliminar', 'error');
                button.disabled = false;
                button.innerHTML = originalText;
            }
        } catch (error) {
            console.error('Error:', error);
            this.showNotification('Error de conexión', 'error');
            button.disabled = false;
            button.innerHTML = originalText;
        }
    }

    async handleStatusChange(event) {
        const select = event.target;
        const url = select.dataset.url;
        const status = select.value;

        select.disabled = true;

        try {
            const response = await fetch(url, {
                method: 'PATCH',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ status })
            });

            if (response.ok) {
                this.showNotification('Estado actualizado correctamente', 'success');
            } else {
                const data = await response.json();
                this.showNotification(data.message || 'Error al actualizar estado', 'error');
                // Возвращаем предыдущее значение
                select.value = select.dataset.previousValue;
            }
        } catch (error) {
            console.error('Error:', error);
            this.showNotification('Error de conexión', 'error');
            select.value = select.dataset.previousValue;
        } finally {
            select.disabled = false;
            select.dataset.previousValue = select.value;
        }
    }

    applyFilters() {
        const form = document.getElementById('adminFilters');
        const formData = new FormData(form);
        const params = new URLSearchParams(formData).toString();
        window.location.href = `${window.location.pathname}?${params}`;
    }

    showNotification(message, type = 'info') {
        // Проверяем, существует ли контейнер для уведомлений
        let container = document.getElementById('notificationContainer');

        if (!container) {
            container = document.createElement('div');
            container.id = 'notificationContainer';
            container.style.cssText = `
                position: fixed;
                top: 20px;
                right: 20px;
                z-index: 9999;
            `;
            document.body.appendChild(container);
        }

        const notification = document.createElement('div');
        notification.style.cssText = `
            background: ${type === 'success' ? '#10b981' : type === 'error' ? '#ef4444' : '#3b82f6'};
            color: white;
            padding: 12px 24px;
            border-radius: 8px;
            margin-bottom: 10px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.15);
            animation: slideIn 0.3s ease;
            display: flex;
            align-items: center;
            gap: 8px;
        `;

        const icon = document.createElement('i');
        icon.className = `fas ${type === 'success' ? 'fa-check-circle' : type === 'error' ? 'fa-exclamation-circle' : 'fa-info-circle'}`;
        notification.appendChild(icon);

        const text = document.createElement('span');
        text.textContent = message;
        notification.appendChild(text);

        container.appendChild(notification);

        // Удаляем уведомление через 3 секунды
        setTimeout(() => {
            notification.style.animation = 'slideOut 0.3s ease';
            setTimeout(() => {
                notification.remove();
                if (container.children.length === 0) {
                    container.remove();
                }
            }, 300);
        }, 3000);
    }

    async loadDashboardData() {
        // Загружаем данные для дашборда асинхронно
        const statsContainer = document.getElementById('dashboardStats');
        if (!statsContainer) return;

        try {
            const response = await fetch('/api/admin/stats');
            const data = await response.json();

            // Обновляем статистику на странице
            Object.keys(data).forEach(key => {
                const element = document.getElementById(`stat-${key}`);
                if (element) {
                    element.textContent = data[key];
                }
            });
        } catch (error) {
            console.error('Error loading dashboard data:', error);
        }
    }
}

// Инициализация при загрузке страницы
document.addEventListener('DOMContentLoaded', () => {
    window.adminPanel = new AdminPanel();
});

// Добавляем стили для анимаций
const style = document.createElement('style');
style.textContent = `
    @keyframes slideIn {
        from {
            transform: translateX(100%);
            opacity: 0;
        }
        to {
            transform: translateX(0);
            opacity: 1;
        }
    }
    
    @keyframes slideOut {
        from {
            transform: translateX(0);
            opacity: 1;
        }
        to {
            transform: translateX(100%);
            opacity: 0;
        }
    }
`;
document.head.appendChild(style);