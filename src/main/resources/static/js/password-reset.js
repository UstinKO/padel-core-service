/**
 * Password Reset Module
 * Maneja la recuperación de contraseña con modales
 */

(function() {
    'use strict';

    // Variables
    let passwordResetModal = null;
    let newPasswordModal = null;
    let currentToken = null;

    // Inicialización cuando el DOM está listo
    document.addEventListener('DOMContentLoaded', function() {
        console.log('Password reset module initialized');
        initPasswordReset();
        checkForTokenInUrl();
    });

    /**
     * Inicializa los eventos para recuperación de contraseña
     */
    function initPasswordReset() {
        const forgotLink = document.querySelector('.forgot-password');
        if (forgotLink) {
            forgotLink.addEventListener('click', function(e) {
                e.preventDefault();
                showPasswordResetModal();
            });
        }
    }

    /**
     * Muestra el modal para solicitar reset de contraseña
     */
    function showPasswordResetModal() {
        // Crear modal si no existe
        if (!passwordResetModal) {
            createPasswordResetModal();
        }

        // Mostrar modal
        passwordResetModal.style.display = 'flex';

        // Resetear formulario
        const form = document.getElementById('passwordResetForm');
        if (form) form.reset();

        // Quitar mensajes anteriores
        const messageDiv = document.getElementById('resetMessage');
        if (messageDiv) {
            messageDiv.style.display = 'none';
            messageDiv.className = 'modal-message';
        }
    }

    /**
     * Crea el modal HTML para solicitar email
     */
    function createPasswordResetModal() {
        const modalHTML = `
            <div id="passwordResetModal" class="modal" style="display: none;">
                <div class="modal-content" style="max-width: 400px;">
                    <div class="modal-header">
                        <h3>Recuperar contraseña</h3>
                        <button type="button" class="modal-close" onclick="hidePasswordResetModal()">&times;</button>
                    </div>
                    <div class="modal-body">
                        <p>Introduce tu email y te enviaremos instrucciones para recuperar tu contraseña.</p>
                        <form id="passwordResetForm">
                            <div class="form-group">
                                <label for="resetEmail" class="form-label">
                                    <i class="fas fa-envelope"></i> Email
                                </label>
                                <input type="email" id="resetEmail" name="email" class="form-control" 
                                       placeholder="tu@email.com" required>
                            </div>
                            <div id="resetMessage" class="modal-message" style="display: none;"></div>
                        </form>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-outline" onclick="hidePasswordResetModal()">Cancelar</button>
                        <button type="button" class="btn btn-primary" onclick="sendPasswordResetRequest()">
                            <i class="fas fa-paper-plane"></i> Enviar
                        </button>
                    </div>
                </div>
            </div>
        `;

        document.body.insertAdjacentHTML('beforeend', modalHTML);
        passwordResetModal = document.getElementById('passwordResetModal');

        // Cerrar al hacer click fuera
        passwordResetModal.addEventListener('click', function(e) {
            if (e.target === passwordResetModal) {
                hidePasswordResetModal();
            }
        });
    }

    /**
     * Oculta el modal de solicitud
     */
    window.hidePasswordResetModal = function() {
        if (passwordResetModal) {
            passwordResetModal.style.display = 'none';
        }
    };

    /**
     * Envía la solicitud de reset de contraseña
     */
    window.sendPasswordResetRequest = function() {
        const email = document.getElementById('resetEmail').value;
        if (!email) {
            showResetMessage('Por favor, introduce tu email', 'error');
            return;
        }

        const button = event.target.closest('button');
        const originalText = button.innerHTML;
        button.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Enviando...';
        button.disabled = true;

        fetch('/recuperar-password/solicitar', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ email: email })
        })
            .then(response => {
                if (!response.ok) {
                    return response.text().then(text => {
                        throw new Error(text);
                    });
                }
                return response.json();
            })
            .then(data => {
                if (data.success) {
                    showResetMessage(data.message, 'success');
                    setTimeout(() => {
                        hidePasswordResetModal();
                    }, 3000);
                } else {
                    showResetMessage(data.message, 'error');
                }
            })
            .catch(error => {
                console.error('Error:', error);
                showResetMessage('Error de conexión. Intenta de nuevo más tarde.', 'error');
            })
            .finally(() => {
                button.innerHTML = originalText;
                button.disabled = false;
            });
    };

    /**
     * Muestra mensaje en el modal
     */
    function showResetMessage(message, type) {
        const messageDiv = document.getElementById('resetMessage');
        if (messageDiv) {
            messageDiv.textContent = message;
            messageDiv.className = 'modal-message ' + type;
            messageDiv.style.display = 'block';
        }
    }

    /**
     * Verifica si hay token en la URL (para mostrar modal de nuevo password)
     */
    function checkForTokenInUrl() {
        const urlParams = new URLSearchParams(window.location.search);
        const token = urlParams.get('token');

        console.log('Checking for token in URL:', token);
        console.log('Current pathname:', window.location.pathname);

        // Проверяем наличие токена на главной странице
        if (token && (window.location.pathname === '/' ||
            window.location.pathname === '/index' ||
            window.location.pathname === '')) {
            console.log('✅ Token encontrado en URL:', token);
            currentToken = token;
            showNewPasswordModal(); // Эта функция должна быть доступна

            // Очищаем токен из URL без перезагрузки
            const url = new URL(window.location);
            url.searchParams.delete('token');
            window.history.replaceState({}, '', url);
        } else {
            console.log('❌ No se encontró token o no es la página principal');
        }
    }

    /**
     * Модальное окно для нового пароля
     */
    function showNewPasswordModal() {
        console.log('Mostrando modal para nuevo password');
        if (!newPasswordModal) {
            createNewPasswordModal();
        }

        newPasswordModal.style.display = 'flex';
    }

    /**
     * Создает модальное окно для нового пароля
     */
    function createNewPasswordModal() {
        const modalHTML = `
            <div id="newPasswordModal" class="modal" style="display: none;">
                <div class="modal-content" style="max-width: 400px;">
                    <div class="modal-header">
                        <h3>Nueva contraseña</h3>
                        <button type="button" class="modal-close" onclick="hideNewPasswordModal()">&times;</button>
                    </div>
                    <div class="modal-body">
                        <p>Introduce tu nueva contraseña</p>
                        <form id="newPasswordForm">
                            <div class="form-group">
                                <label for="newPassword" class="form-label">
                                    <i class="fas fa-lock"></i> Nueva contraseña
                                </label>
                                <div class="input-with-icon">
                                    <input type="password" id="newPassword" name="newPassword" class="form-control" 
                                           placeholder="Mínimo 6 caracteres" required minlength="6">
                                    <i class="fas fa-lock input-icon"></i>
                                    <button type="button" class="toggle-password" data-target="newPassword">
                                        <i class="fas fa-eye"></i>
                                    </button>
                                </div>
                            </div>
                            <div class="form-group">
                                <label for="confirmPassword" class="form-label">
                                    <i class="fas fa-lock"></i> Confirmar contraseña
                                </label>
                                <div class="input-with-icon">
                                    <input type="password" id="confirmPassword" name="confirmPassword" class="form-control" 
                                           placeholder="Repite tu contraseña" required>
                                    <i class="fas fa-lock input-icon"></i>
                                    <button type="button" class="toggle-password" data-target="confirmPassword">
                                        <i class="fas fa-eye"></i>
                                    </button>
                                </div>
                            </div>
                            <div id="newPasswordMessage" class="modal-message" style="display: none;"></div>
                        </form>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-outline" onclick="hideNewPasswordModal()">Cancelar</button>
                        <button type="button" class="btn btn-primary" onclick="sendNewPassword()">
                            <i class="fas fa-sign-in-alt"></i> Cambiar y entrar
                        </button>
                    </div>
                </div>
            </div>
        `;

        document.body.insertAdjacentHTML('beforeend', modalHTML);
        newPasswordModal = document.getElementById('newPasswordModal');

        // Cerrar al hacer click fuera
        newPasswordModal.addEventListener('click', function(e) {
            if (e.target === newPasswordModal) {
                hideNewPasswordModal();
            }
        });

        // Inicializar toggle password
        initializeTogglePassword();
    }

    /**
     * Скрыть модальное окно нового пароля
     */
    window.hideNewPasswordModal = function() {
        if (newPasswordModal) {
            newPasswordModal.style.display = 'none';
        }
    };

    /**
     * Отправляет новый пароль
     */
    window.sendNewPassword = function() {
        const newPassword = document.getElementById('newPassword').value;
        const confirmPassword = document.getElementById('confirmPassword').value;

        if (!newPassword || !confirmPassword) {
            showNewPasswordMessage('Por favor, completa todos los campos', 'error');
            return;
        }

        if (newPassword.length < 6) {
            showNewPasswordMessage('La contraseña debe tener al menos 6 caracteres', 'error');
            return;
        }

        if (newPassword !== confirmPassword) {
            showNewPasswordMessage('Las contraseñas no coinciden', 'error');
            return;
        }

        const button = event.target.closest('button');
        const originalText = button.innerHTML;
        button.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Procesando...';
        button.disabled = true;

        fetch('/recuperar-password/confirmar', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                token: currentToken,
                newPassword: newPassword,
                confirmPassword: confirmPassword
            })
        })
            .then(response => {
                if (!response.ok) {
                    return response.text().then(text => {
                        throw new Error(text);
                    });
                }
                return response.json();
            })
            .then(data => {
                if (data.success) {
                    showNewPasswordMessage(data.message, 'success');
                    setTimeout(() => {
                        window.location.href = '/login?reset=success';
                    }, 2000);
                } else {
                    showNewPasswordMessage(data.message, 'error');
                    button.innerHTML = originalText;
                    button.disabled = false;
                }
            })
            .catch(error => {
                console.error('Error:', error);
                showNewPasswordMessage('Error de conexión. Intenta de nuevo más tarde.', 'error');
                button.innerHTML = originalText;
                button.disabled = false;
            });
    };

    /**
     * Показывает сообщение в модальном окне нового пароля
     */
    function showNewPasswordMessage(message, type) {
        const messageDiv = document.getElementById('newPasswordMessage');
        if (messageDiv) {
            messageDiv.textContent = message;
            messageDiv.className = 'modal-message ' + type;
            messageDiv.style.display = 'block';
        }
    }

    /**
     * Инициализирует кнопки показа/скрытия пароля
     */
    function initializeTogglePassword() {
        document.querySelectorAll('.toggle-password').forEach(button => {
            button.addEventListener('click', function() {
                const targetId = this.getAttribute('data-target');
                const input = document.getElementById(targetId);
                const icon = this.querySelector('i');

                if (input.type === 'password') {
                    input.type = 'text';
                    icon.classList.remove('fa-eye');
                    icon.classList.add('fa-eye-slash');
                } else {
                    input.type = 'password';
                    icon.classList.remove('fa-eye-slash');
                    icon.classList.add('fa-eye');
                }
            });
        });
    }
})();