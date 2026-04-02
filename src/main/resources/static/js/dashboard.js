/**
 * Padel Core - Dashboard JavaScript
 */

document.addEventListener('DOMContentLoaded', function() {
    'use strict';

    // ===== БУРГЕР-МЕНЮ =====
    const navbarToggler = document.getElementById('navbarToggler');
    const navbarNav = document.getElementById('navbarNav');

    if (navbarToggler && navbarNav) {
        console.log('✅ Dashboard: найдены элементы меню');

        // Убираем старые обработчики
        const newToggler = navbarToggler.cloneNode(true);
        navbarToggler.parentNode.replaceChild(newToggler, navbarToggler);

        // Добавляем обработчик
        newToggler.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();

            navbarNav.classList.toggle('show');
            console.log('🍔 Меню дашборда:', navbarNav.classList.contains('show') ? 'открыто' : 'закрыто');
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
            setTimeout(() => {
                const modal = document.getElementById('partnerModal');
                console.log('🔍 Проверка модального окна после загрузки:', modal ? '✅ найдено' : '❌ не найдено');
                if (modal) {
                    console.log('📌 Классы модального окна:', modal.className);
                    console.log('📌 Стили модального окна:', modal.style.display);
                }
            }, 1000);
        });
    }

    // Маппинги для отображения значений
    const displayMaps = {
        nivel: {
            // Новые уровни
            'SUMA_15': 'Suma 15+',
            'SUMA_13': 'Suma 13+',
            'D7': 'D7',
            'D8': 'D8',
            'D7_D8': 'D7/D8',
            'D6': 'D6',
            'C7_C6': 'C7/C6',
            // Существующие уровни
            'C9': 'C9 (Principiante)',
            'C8': 'C8 (Intermedio)',
            'C7': 'C7 (Avanzado)',
            'C6': 'C6 (Profesional)',
            'C5': 'C5 (Élite)',
            'PRINCIPIANTES': 'Principiante'
        },
        tipo: {
            'KING_OF_COURT': 'King of Court',
            'AMERICANA': 'Americana'
        },
        estado: {
            'REGISTRO_ABIERTO': 'Inscripción abierta',
            'PUBLICADO': 'Próximamente',
            'BORRADOR': 'Borrador',
            'CERRADO': 'Cerrado',
            'FINALIZADO': 'Finalizado',
            'CANCELADO': 'Cancelado'
        },
        genero: {
            'MASCULINO': 'Masculino',
            'FEMENINO': 'Femenino',
            'MIXTO': 'Mixto'
        },
        modalidad: {
            'INDIVIDUAL': 'Individual',
            'DOBLES': 'Dobles'
        }
    };

    // Элементы фильтров
    const toggleFilters = document.getElementById('toggleFilters');
    const filtersPanel = document.getElementById('filtersPanel');
    const applyFilters = document.getElementById('applyDashboardFilters');
    const clearFilters = document.getElementById('clearDashboardFilters');
    const generoSelect = document.getElementById('dashboardGenero');
    const nivelSelect = document.getElementById('dashboardNivel');
    const tipoSelect = document.getElementById('dashboardTipo');
    const myTournamentsCheckbox = document.getElementById('myTournamentsOnly');
    const myTournamentsCount = document.getElementById('myTournamentsCount');
    const torneosGrid = document.getElementById('dashboardTorneosGrid');
    const visibleCount = document.getElementById('visibleCount');
    const totalCount = document.getElementById('totalCount');
    const noTournamentsMessage = document.getElementById('noTournamentsMessage');
    const resultsCounter = document.getElementById('resultsCounter');
    const modalidadSelect = document.getElementById('dashboardModalidad');

    // Данные турниров
    const tournaments = window.tournamentData || [];
    const myTournamentIds = new Set(window.myTournamentIds || []);

    console.log('🏆 Турниров загружено:', tournaments.length);
    console.log('📋 Мои турниры:', myTournamentIds.size, 'ID:', [...myTournamentIds]);

    // Логируем уникальные значения для отладки
    const uniqueNiveles = [...new Set(tournaments.map(t => t.categoriaNivel))];
    console.log('📊 Уникальные значения уровней:', uniqueNiveles);

    // Обновляем счетчик моих турниров
    if (myTournamentsCount) {
        myTournamentsCount.textContent = `(${myTournamentIds.size})`;
    }
    // Обновляем кнопку Inscriptos в welcome-card
    updateMyTournamentsBtn();

    // Текущие отфильтрованные турниры
    let filteredTournaments = [...tournaments];

    // Показать/скрыть панель фильтров
    if (toggleFilters) {
        toggleFilters.addEventListener('click', () => {
            const isHidden = filtersPanel.style.display === 'none' || filtersPanel.style.display === '';
            filtersPanel.style.display = isHidden ? 'block' : 'none';
            toggleFilters.innerHTML = isHidden ?
                '<i class="fas fa-times"></i> Cerrar filtros' :
                '<i class="fas fa-sliders-h"></i> Filtrar';
        });
    }

    // Применить фильтры
    if (applyFilters) {
        applyFilters.addEventListener('click', applyFiltersFunction);
    }

    // Очистить фильтры
    if (clearFilters) {
        clearFilters.addEventListener('click', () => {
            if (generoSelect) generoSelect.value = 'todos';
            if (nivelSelect) nivelSelect.value = 'todos';
            if (tipoSelect) tipoSelect.value = 'todos';
            if (modalidadSelect) modalidadSelect.value = 'todos';
            if (myTournamentsCheckbox) myTournamentsCheckbox.checked = false;
            applyFiltersFunction();
        });
    }

    // Функция применения фильтров
    function applyFiltersFunction() {
        const filters = {
            genero: generoSelect ? generoSelect.value : 'todos',
            nivel: nivelSelect ? nivelSelect.value : 'todos',
            tipo: tipoSelect ? tipoSelect.value : 'todos',
            modalidad: modalidadSelect ? modalidadSelect.value : 'todos',
            myTournamentsOnly: myTournamentsCheckbox ? myTournamentsCheckbox.checked : false
        };

        console.log('🔍 Применяем фильтры:', filters);

        filteredTournaments = tournaments.filter(tournament => {
            // Фильтр по полу - сравниваем напрямую с enum значениями
            if (filters.genero !== 'todos') {
                if (tournament.generoFormato !== filters.genero) {
                    return false;
                }
            }

            // ИСПРАВЛЕНО: Фильтр по уровню с маппингом значений
            if (filters.nivel !== 'todos') {
                // Маппинг значений фильтра на значения в БД
                const nivelMap = {
                    // Новые уровни
                    'SUMA_15': 'SUMA_15',
                    'SUMA_13': 'SUMA_13',
                    'D7': 'D7',
                    'D8': 'D8',
                    'D7_D8': 'D7_D8',
                    'D6': 'D6',
                    'C7_C6': 'C7_C6',
                    // Существующие уровни
                    'C9': 'C9',
                    'C8': 'C8',
                    'C7': 'C7',
                    'C6': 'C6',
                    'C5': 'C5',
                    'Principiante': 'PRINCIPIANTES'
                };
                const dbValue = nivelMap[filters.nivel];
                if (tournament.categoriaNivel !== dbValue) {
                    return false;
                }
            }

            // Фильтр по типу
            if (filters.tipo !== 'todos') {
                if (tournament.tipo !== filters.tipo) {
                    return false;
                }
            }

            // ✅ НОВЫЙ ФИЛЬТР: по модальности (SINGLES/DOBLES)
            if (filters.modalidad !== 'todos') {
                if (tournament.modalidad !== filters.modalidad) {
                    return false;
                }
            }

            // Фильтр "Мои турниры"
            if (filters.myTournamentsOnly && !myTournamentIds.has(tournament.id)) {
                return false;
            }
            updateMyTournamentsBtn()

            return true;
        });

        console.log('✅ Найдено турниров:', filteredTournaments.length);

        // Логируем распределение по уровням после фильтрации
        const nivelCounts = {};
        filteredTournaments.forEach(t => {
            const nivel = t.categoriaNivel || 'undefined';
            nivelCounts[nivel] = (nivelCounts[nivel] || 0) + 1;
        });
        console.log('📊 Распределение по уровням после фильтрации:', nivelCounts);

        renderTournaments();
    }

    function updateMyTournamentsBtn() {
        const btn = document.getElementById('myTournamentsBtn');
        const btnText = document.getElementById('myTournamentsBtnText');
        if (!btn || !btnText) return;

        const count = myTournamentIds.size;

        // Всегда показываем кнопку
        btn.style.display = 'flex';

        // Текст зависит от количества регистраций
        if (count > 0) {
            btnText.textContent = `Inscriptos (${count})`;
        } else {
            btnText.textContent = 'Inscriptos';
        }
    }

    // Фильтрует по "Mis torneos" — вызывается по кнопке Inscriptos
    window.filterMyTournaments = function() {
        const btn = document.getElementById('myTournamentsBtn');
        const checkbox = document.getElementById('myTournamentsOnly');

        // Переключаем фильтр
        const isActive = btn.classList.contains('active');

        if (isActive) {
            // Снимаем фильтр
            btn.classList.remove('active');
            if (checkbox) checkbox.checked = false;
        } else {
            // Включаем фильтр
            btn.classList.add('active');
            if (checkbox) checkbox.checked = true;
            // Раскрываем панель фильтров если она скрыта
            const filtersPanel = document.getElementById('filtersPanel');
            if (filtersPanel) filtersPanel.style.display = 'block';
        }

        applyFiltersFunction();

        // Прокручиваем к списку турниров
        const grid = document.getElementById('dashboardTorneosGrid');
        if (grid) grid.scrollIntoView({ behavior: 'smooth', block: 'start' });
    };

    // Отрисовка турниров
    function renderTournaments() {
        if (!torneosGrid) return;

        // Обновляем счетчики
        if (visibleCount) visibleCount.textContent = filteredTournaments.length;
        if (totalCount) totalCount.textContent = tournaments.length;

        if (filteredTournaments.length === 0) {
            torneosGrid.innerHTML = '';
            if (noTournamentsMessage) noTournamentsMessage.style.display = 'block';
            if (resultsCounter) resultsCounter.style.display = 'none';
            return;
        }

        if (noTournamentsMessage) noTournamentsMessage.style.display = 'none';
        if (resultsCounter) resultsCounter.style.display = 'block';

        // Очищаем сетку
        torneosGrid.innerHTML = '';

        // Создаем карточки для каждого турнира
        filteredTournaments.forEach(tournament => {
            const card = createTournamentCardElement(tournament);
            torneosGrid.appendChild(card);
        });
    }

    // Прикрепляем обработчики к кнопкам регистрации
    function attachRegistrationHandlers() {
        document.querySelectorAll('.btn-register').forEach(button => {
            button.addEventListener('click', handleRegistration);
        });

        document.querySelectorAll('.btn-cancel').forEach(button => {
            button.addEventListener('click', handleCancellation);
        });
    }

    // Обработчик регистрации
    async function handleRegistration(event) {
        const button = event.currentTarget;
        const tournamentId   = button.dataset.tournamentId;
        const tournamentName = button.dataset.tournamentName;

        const tournament = tournaments.find(t => t.id === parseInt(tournamentId));

        ContactCheck.beforeRegister(tournamentId, tournamentName, async () => {

            if (tournament && tournament.modalidad === 'DOBLES') {
                // ContactCheck уже проверил и при необходимости сохранил контакты.
                // Теперь можно открывать форму регистрации пары.
                openPartnerModal(tournamentId, tournamentName, tournament);
                return;
            }

            showConfirmModal(
                'Confirmar inscripción',
                `¿Deseas inscribirte en el torneo "${tournamentName}"?`,
                async () => {
                    button.disabled = true;
                    button.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Procesando...';

                    try {
                        const response = await fetch(`/players/tournaments/${tournamentId}/register`, {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' }
                        });

                        const data = await response.json();

                        if (data.success) {
                            showResultModal('success', data.message);
                            myTournamentIds.add(parseInt(tournamentId));
                            if (myTournamentsCount) {
                                myTournamentsCount.textContent = `(${myTournamentIds.size})`;
                            }
                            updateMyTournamentsBtn()
                            applyFiltersFunction();
                        } else {
                            showResultModal('error', 'Error: ' + data.message);
                            button.disabled = false;
                            button.innerHTML = '<i class="fas fa-plus-circle"></i> Registrarse';
                        }
                    } catch (error) {
                        console.error('Error registering:', error);
                        showResultModal('error', 'Error al procesar la solicitud');
                        button.disabled = false;
                        button.innerHTML = '<i class="fas fa-plus-circle"></i> Registrarse';
                    }
                }
            );

        });
    }

    // Функция открытия модального окна для парной регистрации
    function openPartnerModal(tournamentId, tournamentName, tournament) {
        console.log('🔍 Ищем модальное окно с ID: partnerModal');

        const modal = document.getElementById('partnerModal');
        console.log('📌 Результат поиска:', modal);

        if (!modal) {
            console.error('❌ Модальное окно не найдено в DOM');
            console.log('📋 Доступные модальные окна:', document.querySelectorAll('.modal'));
            return;
        }

        console.log('✅ Модальное окно найдено, открываем...');

        // Заполняем данные
        const modalTitle = document.getElementById('modalTournamentName');
        const modalId = document.getElementById('modalTournamentId');

        if (modalTitle) modalTitle.textContent = tournamentName;
        if (modalId) modalId.value = tournamentId;

        // Очищаем форму
        const firstName = document.getElementById('partnerFirstName');
        const lastName = document.getElementById('partnerLastName');
        const phone = document.getElementById('partnerPhone');
        const email = document.getElementById('partnerEmail');

        if (firstName) firstName.value = '';
        if (lastName) lastName.value = '';
        if (phone) phone.value = '';
        if (email) email.value = '';

        // Убираем старые обработчики и добавляем новый
        const submitBtn = document.getElementById('submitPartnerBtn');
        if (submitBtn) {
            const newSubmitBtn = submitBtn.cloneNode(true);
            submitBtn.parentNode.replaceChild(newSubmitBtn, submitBtn);
            newSubmitBtn.addEventListener('click', () => submitPartnerRegistration(tournamentId));
        }

        const closeButtons = modal.querySelectorAll('[data-dismiss="modal"]');
        closeButtons.forEach(button => {
            // Убираем старые обработчики
            const newButton = button.cloneNode(true);
            button.parentNode.replaceChild(newButton, button);

            // Добавляем новый обработчик
            newButton.addEventListener('click', function(e) {
                e.preventDefault();
                modal.style.display = 'none';
                modal.classList.remove('show');
                toggleModalAriaHidden(modal, false);
                console.log('✅ Модальное окно закрыто');
            });
        });

        // Показываем модальное окно
        modal.style.display = 'block';
        modal.classList.add('show');
        toggleModalAriaHidden(modal, true);
    }

    // Отправка данных парной регистрации
    async function submitPartnerRegistration(tournamentId) {
        // Валидация
        const firstName = document.getElementById('partnerFirstName')?.value.trim();
        const lastName = document.getElementById('partnerLastName')?.value.trim();
        const phone = document.getElementById('partnerPhone')?.value.trim();

        if (!firstName || !lastName || !phone) {
            alert('Por favor completa todos los campos obligatorios');
            return;
        }

        const submitBtn = document.getElementById('submitPartnerBtn');
        if (submitBtn) {
            submitBtn.disabled = true;
            submitBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Procesando...';
        }

        try {
            const partnerData = {
                nombre: firstName,
                apellido: lastName,
                telefono: phone,
                email: document.getElementById('partnerEmail')?.value.trim() || null
            };

            const response = await fetch(`/api/tournaments/double/${tournamentId}/register`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(partnerData)
            });

            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.message || 'Error en el registro');
            }

            const data = await response.json();

            // Закрываем модальное окно
            const modal = document.getElementById('partnerModal');
            if (modal) {
                modal.style.display = 'none';
                modal.classList.remove('show');
            }

            // ✅ ПОЛУЧАЕМ tournamentId ИЗ ОТВЕТА
            const registeredTournamentId = data.tournamentId || tournamentId;

            myTournamentIds.add(parseInt(registeredTournamentId));
            if (myTournamentsCount) {
                myTournamentsCount.textContent = `(${myTournamentIds.size})`;
            }
            updateMyTournamentsBtn()

            // Показываем сообщение об успехе
            showResultModal('success', '¡Registro completado! Se ha enviado un email a tu compañero.');

            // Перерисовываем
            applyFiltersFunction();

        } catch (error) {
            console.error('Error:', error);
            showResultModal('error', error.message || 'Error al procesar la solicitud');

            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.innerHTML = '<i class="fas fa-check-circle"></i> Registrar pareja';
            }
        }
    }

    // Показать модальное окно с результатом (успех/ошибка)
    function showResultModal(type, message) {
        const modal = document.getElementById('resultModal');
        if (!modal) return;

        const modalBody = document.getElementById('resultModalBody');
        const icon = type === 'success' ? 'fa-check-circle' : 'fa-exclamation-circle';
        const color = type === 'success' ? '#28a745' : '#dc3545';

        modalBody.innerHTML = `
        <i class="fas ${icon}" style="font-size: 48px; color: ${color}; margin-bottom: 15px;"></i>
        <p style="font-size: 16px; margin: 0;">${message}</p>
    `;

        modal.style.display = 'block';
        modal.classList.add('show');
        toggleModalAriaHidden(modal, true);

        // Автоматически закрываем через 3 секунды
        setTimeout(() => {
            toggleModalAriaHidden(modal, false);
            modal.style.display = 'none';
            modal.classList.remove('show');
        }, 3000);
    }

// Показать confirmation modal
    function showConfirmModal(title, message, onConfirm, onCancel) {
        const modal = document.getElementById('confirmModal');
        if (!modal) return;

        document.getElementById('confirmModalTitle').textContent = title;
        document.getElementById('confirmModalMessage').textContent = message;

        const confirmBtn = document.getElementById('confirmModalBtn');
        const cancelBtn = document.getElementById('confirmModalCancelBtn');

        // Убираем старые обработчики
        const newConfirmBtn = confirmBtn.cloneNode(true);
        const newCancelBtn = cancelBtn.cloneNode(true);
        confirmBtn.parentNode.replaceChild(newConfirmBtn, confirmBtn);
        cancelBtn.parentNode.replaceChild(newCancelBtn, cancelBtn);

        // Добавляем новые
        newConfirmBtn.addEventListener('click', () => {
            toggleModalAriaHidden(modal, false);
            modal.style.display = 'none';
            modal.classList.remove('show');
            if (onConfirm) onConfirm();
        });

        newCancelBtn.addEventListener('click', () => {
            toggleModalAriaHidden(modal, false);
            modal.style.display = 'none';
            modal.classList.remove('show');
            if (onCancel) onCancel();
        });

        modal.style.display = 'block';
        modal.classList.add('show');
        toggleModalAriaHidden(modal, true);
    }

    // Показать модальное окно отмены для парного турнира
    async function showDoubleCancellationModal(tournamentId, tournamentName, registrationId) {
        const modal = document.getElementById('doubleCancelModal');
        if (!modal) return;

        // Проверяем существование всех необходимых элементов
        const tournamentNameSpan = document.getElementById('doubleCancelTournamentName');
        const tournamentIdInput = document.getElementById('doubleCancelTournamentId');
        const registrationIdInput = document.getElementById('doubleCancelRegistrationId');

        if (!tournamentNameSpan || !tournamentIdInput || !registrationIdInput) {
            console.error('Не найдены необходимые элементы в модальном окне');
            return;
        }

        // Проверяем, зарегистрирован ли партнер
        const isPartnerRegistered = await checkIfPartnerIsRegistered(tournamentId);

        tournamentNameSpan.textContent = tournamentName;
        tournamentIdInput.value = tournamentId;
        registrationIdInput.value = registrationId || '';

        // Находим все опции с проверкой на существование
        const selfRadio = document.querySelector('input[name="cancelOption"][value="self"]');
        const fullRadio = document.querySelector('input[name="cancelOption"][value="full"]');
        const replaceCheckbox = document.getElementById('cancelOptionReplace');

        const selfOption = selfRadio?.closest('.radio-label');
        const fullOption = fullRadio?.closest('.radio-label');
        const replaceOption = replaceCheckbox?.closest('.radio-label');

        // Удаляем существующее сообщение, если оно есть
        const existingMessage = modal.querySelector('.partner-not-registered-message');
        if (existingMessage) {
            existingMessage.remove();
        }

        if (!isPartnerRegistered) {
            // Если партнер не зарегистрирован - отключаем опции self и replace
            if (selfOption) {
                selfOption.style.opacity = '0.5';
                selfOption.style.pointerEvents = 'none';
                selfOption.title = 'No disponible: tu compañero no está registrado en el sistema';
            }

            if (replaceOption) {
                replaceOption.style.opacity = '0.5';
                replaceOption.style.pointerEvents = 'none';
                replaceOption.title = 'No disponible: tu compañero no está registrado en el sistema';
            }

            if (fullOption) {
                fullOption.style.opacity = '1';
                fullOption.style.pointerEvents = 'auto';
                fullOption.title = '';
            }

            // Показываем сообщение
            const messageDiv = document.createElement('div');
            messageDiv.className = 'alert alert-warning partner-not-registered-message';
            messageDiv.innerHTML = '<i class="fas fa-exclamation-triangle"></i> Tu compañero no está registrado en el sistema. Solo puedes cancelar toda la pareja.';

            // Добавляем сообщение в модальное окно
            const modalBody = modal.querySelector('.modal-body');
            const radioGroup = modal.querySelector('.radio-group');

            if (modalBody) {
                if (radioGroup && radioGroup.parentNode === modalBody) {
                    // Вставляем перед radio-group
                    modalBody.insertBefore(messageDiv, radioGroup);
                } else {
                    // Добавляем в начало modal-body
                    modalBody.insertBefore(messageDiv, modalBody.firstChild);
                }
            }

            // Выбираем опцию full по умолчанию
            if (fullRadio) fullRadio.checked = true;
            if (replaceCheckbox) replaceCheckbox.checked = false;

            // Скрываем поля замены
            const replaceFields = document.getElementById('replacePlayerFields');
            if (replaceFields) replaceFields.style.display = 'none';

        } else {
            // Если оба зарегистрированы - все опции доступны
            if (selfOption) {
                selfOption.style.opacity = '1';
                selfOption.style.pointerEvents = 'auto';
                selfOption.title = '';
            }

            if (replaceOption) {
                replaceOption.style.opacity = '1';
                replaceOption.style.pointerEvents = 'auto';
                replaceOption.title = '';
            }

            if (fullOption) {
                fullOption.style.opacity = '1';
                fullOption.style.pointerEvents = 'auto';
                fullOption.title = '';
            }
        }

        // Показываем/скрываем поле для замены
        const replaceOptionCheckbox = document.getElementById('cancelOptionReplace');
        const replaceFields = document.getElementById('replacePlayerFields');

        if (replaceOptionCheckbox && replaceFields) {
            // Убираем старые обработчики
            const newReplaceOption = replaceOptionCheckbox.cloneNode(true);
            replaceOptionCheckbox.parentNode.replaceChild(newReplaceOption, replaceOptionCheckbox);

            newReplaceOption.addEventListener('change', function() {
                replaceFields.style.display = this.checked ? 'block' : 'none';
            });
        }

        // Очищаем поля
        const replaceFirstName = document.getElementById('replaceFirstName');
        const replaceLastName = document.getElementById('replaceLastName');
        const replacePhone = document.getElementById('replacePhone');
        const replaceEmail = document.getElementById('replaceEmail');

        if (replaceFirstName) replaceFirstName.value = '';
        if (replaceLastName) replaceLastName.value = '';
        if (replacePhone) replacePhone.value = '';
        if (replaceEmail) replaceEmail.value = '';

        // Обработчики кнопок
        const confirmBtn = document.getElementById('doubleCancelConfirmBtn');
        const cancelBtn = document.getElementById('doubleCancelCancelBtn');

        if (confirmBtn && cancelBtn) {
            const newConfirmBtn = confirmBtn.cloneNode(true);
            const newCancelBtn = cancelBtn.cloneNode(true);
            confirmBtn.parentNode.replaceChild(newConfirmBtn, confirmBtn);
            cancelBtn.parentNode.replaceChild(newCancelBtn, cancelBtn);

            newConfirmBtn.addEventListener('click', () => handleDoubleCancellation(tournamentId));
            newCancelBtn.addEventListener('click', () => {
                toggleModalAriaHidden(modal, false);
                modal.style.display = 'none';
                modal.classList.remove('show');
            });
        }

        modal.style.display = 'block';
        modal.classList.add('show');
        toggleModalAriaHidden(modal, true);
    }

    // Функция для проверки, зарегистрирован ли партнер
    function checkIfPartnerIsRegistered(tournamentId) {
        // Находим текущего игрока в данных
        const playerId = window.currentPlayerId; // Нужно добавить это в HTML

        // Ищем регистрацию текущего игрока в этом турнире
        // В данных турниров у нас нет информации о регистрациях, поэтому делаем запрос к серверу
        return new Promise((resolve, reject) => {
            fetch(`/players/tournaments/${tournamentId}/my-registration`)
                .then(response => response.json())
                .then(data => {
                    // Если есть partnerId - партнер зарегистрирован
                    // Если есть partnerFirstName но нет partnerId - партнер не зарегистрирован
                    const isRegistered = data.partnerId != null;
                    console.log('🔍 Проверка партнера:', isRegistered ? 'зарегистрирован' : 'не зарегистрирован');
                    resolve(isRegistered);
                })
                .catch(error => {
                    console.error('Ошибка при проверке партнера:', error);
                    resolve(false); // По умолчанию считаем что не зарегистрирован
                });
        });
    }

    // Обработка отмены в парном турнире
    async function handleDoubleCancellation(tournamentId) {
        const cancelOptionElement = document.querySelector('input[name="cancelOption"]:checked');
        if (!cancelOptionElement) {
            showResultModal('error', 'Por favor selecciona una opción');
            return;
        }

        const cancelOption = cancelOptionElement.value;
        const registrationId = document.getElementById('doubleCancelRegistrationId').value;
        const reason = document.getElementById('cancelReason')?.value || '';

        const button = document.querySelector(`.btn-cancel[data-tournament-id="${tournamentId}"]`);
        if (button) {
            button.disabled = true;
            button.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Procesando...';
        }

        try {
            let url = `/players/tournaments/${tournamentId}/cancel-double?option=${cancelOption}`;
            if (reason) url += `&reason=${encodeURIComponent(reason)}`;

            // Если выбрана замена, добавляем данные нового игрока
            if (cancelOption === 'replace') {
                const replaceData = {
                    firstName: document.getElementById('replaceFirstName').value.trim(),
                    lastName: document.getElementById('replaceLastName').value.trim(),
                    phone: document.getElementById('replacePhone').value.trim(),
                    email: document.getElementById('replaceEmail').value.trim() || null
                };

                if (!replaceData.firstName || !replaceData.lastName || !replaceData.phone) {
                    showResultModal('error', 'Por favor completa los datos del nuevo jugador');
                    if (button) {
                        button.disabled = false;
                        button.innerHTML = '<i class="fas fa-times-circle"></i> Cancelar';
                    }
                    return;
                }

                url += `&replaceData=${encodeURIComponent(JSON.stringify(replaceData))}`;
            }

            const response = await fetch(url, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                }
            });

            const data = await response.json();

            if (data.success) {
                // Закрываем модальное окно
                const modal = document.getElementById('doubleCancelModal');
                if (modal) {
                    modal.style.display = 'none';
                    modal.classList.remove('show');
                }

                // Показываем сообщение об успехе
                showResultModal('success', data.message);

                // ИСПРАВЛЕНО: Принудительная перезагрузка страницы через 1 секунду
                setTimeout(() => {
                    window.location.reload();
                }, 1000);
            } else {
                showResultModal('error', 'Error: ' + data.message);
                if (button) {
                    button.disabled = false;
                    button.innerHTML = '<i class="fas fa-times-circle"></i> Cancelar';
                }
            }
        } catch (error) {
            console.error('Error cancelling:', error);
            showResultModal('error', 'Error al procesar la solicitud');
            if (button) {
                button.disabled = false;
                button.innerHTML = '<i class="fas fa-times-circle"></i> Cancelar';
            }
        }
    }

    // Функция для создания элемента карточки турнира
    function createTournamentCardElement(tournament) {
        const card = document.createElement('div');
        card.className = `torneo-card ${myTournamentIds.has(tournament.id) ? 'my-tournament' : ''}`;
        card.dataset.tournamentId = tournament.id;

        const isMyTournament = myTournamentIds.has(tournament.id);
        const myTournamentBadge = isMyTournament ?
            '<span class="my-tournament-badge"><i class="fas fa-check-circle"></i> Inscrito</span>' : '';

        // Форматируем дату
        const fechaArray = tournament.fechaInicio;
        const fechaStr = Array.isArray(fechaArray) ?
            `${fechaArray[2]}/${fechaArray[1]}/${fechaArray[0]}` :
            tournament.fechaInicio || '';

        // Форматируем время
        const horaArray = tournament.horaInicio;
        const horaStr = Array.isArray(horaArray) ?
            `${horaArray[0]}:${horaArray[1].toString().padStart(2, '0')}` :
            tournament.horaInicio || '';

        // Определяем статус регистрации
        const registrationText = tournament.inscritosActuales >= tournament.cupoMax ?
            'Lista de espera' : 'Registrarse';

        // Получаем отображаемые тексты
        const generoDisplay = displayMaps.genero[tournament.generoFormato] || tournament.generoFormato || 'N/A';
        const nivelDisplay = tournament.categoriaNivel || 'N/A';
        const tipoDisplay = displayMaps.tipo[tournament.tipo] || tournament.tipo || 'N/A';

        // Определяем текст для количества участников
        const participantsText = tournament.modalidad === 'DOBLES' ? 'parejas' : 'jugadores';

        // Формируем адрес клуба, если он есть
        const clubAddress = tournament.clubDireccion ?
            `<span class="club-address">${escapeHtml(tournament.clubDireccion)}</span>` : '';

        card.innerHTML = `
        <div class="torneo-card-header">
            <span class="torneo-badge">${generoDisplay}</span>
            <span class="torneo-badge torneo-badge-level">${nivelDisplay}</span>
        </div>
        <div class="torneo-card-body">
            <h3 class="torneo-title">${escapeHtml(tournament.nombre || '')}</h3>
            ${myTournamentBadge}
            <div class="torneo-info">
                <div class="torneo-info-item">
                    <i class="fas fa-calendar-alt"></i>
                    <span>${fechaStr} ${horaStr}</span>
                </div>
                <div class="torneo-info-item">
                    <i class="fas fa-map-marker-alt"></i>
                    <div class="club-info">
                        <span class="club-name">${escapeHtml(tournament.clubNombre || 'Club por definir')}</span>
                        ${clubAddress}
                    </div>
                </div>
                <div class="torneo-info-item">
                    <i class="fas fa-trophy"></i>
                    <span>${tipoDisplay}</span>
                </div>
                <div class="torneo-info-item">
                    <i class="fas fa-users"></i>
                    <span>${tournament.inscritosActuales || 0}/${tournament.cupoMax || 0} ${participantsText}</span>
                </div>
                <div class="torneo-info-item">
                    <i class="fas fa-tag"></i>
                    <span>${tournament.precio || 0} ${tournament.moneda || ''}</span>
                </div>
            </div>
            <div class="torneo-footer">
${!isMyTournament ? `
    <button class="btn btn-primary btn-small btn-register" 
            data-tournament-id="${tournament.id}"
            data-tournament-name="${escapeHtml(tournament.nombre)}">
        <i class="fas fa-plus-circle"></i> 
        <span>${registrationText}</span>
    </button>
` : `
    <button class="btn btn-outline btn-small btn-cancel" 
            data-tournament-id="${tournament.id}"
            data-tournament-name="${escapeHtml(tournament.nombre)}">
        <i class="fas fa-times-circle"></i> Cancelar
    </button>
`}
    <a href="/torneo/${tournament.id}" class="btn btn-outline btn-small">
        <i class="fas fa-info-circle"></i> Detalles
    </a>
            </div>
        </div>
    `;

        // Добавляем обработчики для кнопок
        const registerBtn = card.querySelector('.btn-register');
        if (registerBtn) {
            registerBtn.addEventListener('click', handleRegistration);
        }

        const cancelBtn = card.querySelector('.btn-cancel');
        if (cancelBtn) {
            cancelBtn.addEventListener('click', handleCancellation);
        }

        return card;
    }

    // Функция для обновления данных турниров с сервера
    async function refreshTournamentData() {
        try {
            console.log('🔄 Обновляем данные турниров с сервера...');
            const response = await fetch('/api/tournaments/active');
            if (response.ok) {
                const data = await response.json();

                // Обновляем глобальные данные
                window.tournamentData = data;

                // Обновляем локальные переменные
                tournaments.length = 0;
                tournaments.push(...data);

                // Обновляем уникальные значения для отладки
                const uniqueNiveles = [...new Set(tournaments.map(t => t.categoriaNivel))];
                console.log('📊 Обновленные уникальные значения уровней:', uniqueNiveles);

                console.log('✅ Данные турниров обновлены:', tournaments.length);
            } else {
                console.error('❌ Ошибка при обновлении данных:', response.status);
            }
        } catch (error) {
            console.error('❌ Ошибка при обновлении данных:', error);
        }
    }

    // Функция для правильного управления aria-hidden при открытии/закрытии модалок
    function toggleModalAriaHidden(modal, isOpen) {
        if (!modal) return;

        if (isOpen) {
            // При открытии - убираем aria-hidden
            modal.removeAttribute('aria-hidden');
            // Не устанавливаем фокус на кнопку, чтобы избежать конфликтов
        } else {
            // При закрытии - добавляем aria-hidden обратно
            modal.setAttribute('aria-hidden', 'true');
        }
    }


    // Обработчик отмены регистрации
    async function handleCancellation(event) {
        const button = event.currentTarget;
        const tournamentId = button.dataset.tournamentId;
        const tournamentName = button.dataset.tournamentName;

        // Находим турнир в данных
        const tournament = tournaments.find(t => t.id === parseInt(tournamentId));
        const isDoubles = tournament?.modalidad === 'DOBLES';

        if (isDoubles) {
            // Для парных турниров - специальное модальное окно
            showDoubleCancellationModal(tournamentId, tournamentName, null);
            return;
        }

        // Для одиночных - простое подтверждение
        showConfirmModal(
            'Cancelar inscripción',
            `¿Estás seguro de cancelar tu inscripción en "${tournamentName}"?`,
            async () => {
                button.disabled = true;
                button.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Procesando...';

                try {
                    const response = await fetch(`/players/tournaments/${tournamentId}/cancel`, {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json',
                        }
                    });

                    const data = await response.json();

                    if (data.success) {
                        showResultModal('success', data.message);

                        // ИСПРАВЛЕНО: Принудительная перезагрузка страницы через 1 секунду
                        setTimeout(() => {
                            window.location.reload();
                        }, 1000);
                    } else {
                        showResultModal('error', 'Error: ' + data.message);
                        button.disabled = false;
                        button.innerHTML = '<i class="fas fa-times-circle"></i> Cancelar';
                    }
                } catch (error) {
                    console.error('Error cancelling:', error);
                    showResultModal('error', 'Error al procesar la solicitud');
                    button.disabled = false;
                    button.innerHTML = '<i class="fas fa-times-circle"></i> Cancelar';
                }
            }
        );
    }

    // Функция для безопасного экранирования HTML
    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // Инициализация - показываем все турниры
    renderTournaments();

    // Автоматически обновляем список каждые 30 секунд (опционально)
    setInterval(() => {
        // Можно добавить логику для обновления данных с сервера
        console.log('Auto-refresh not implemented');
    }, 30000);
});