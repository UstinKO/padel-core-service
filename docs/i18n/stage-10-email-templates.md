# Этап 10: Интеграция — замена строк в email шаблонах

## Цель

Заменить хардкоженные строки в 10 email-шаблонах на `#{key}`.  
Email-шаблоны — standalone HTML (без CSS-файлов, без JS), поэтому JS-перевод не нужен.

---

## Особенности email шаблонов

- **Нет JS** — только Thymeleaf + HTML + inline CSS
- **Standalone HTML** — нельзя использовать `th:replace` для общего header/footer
- **Email-клиенты**: многие не поддерживают современный CSS — HTML простой, изменения минимальные
- **Emojis в тексте** — оставляем как есть, они универсальны
- **CSS-стили** — не трогаем, только текстовые строки

---

## Thymeleaf синтаксис в email

Тот же что и для UI шаблонов, но с учётом строгого HTML email-клиентов:

```html
<!-- Простой текст -->
<h1 th:text="#{email.bienvenida.title}">¡Bienvenido a 1-Padel!</h1>

<!-- Текст с переменной-игроком (из Context) -->
<p th:text="#{email.common.greeting(${nombre})}">Hola Jugador!</p>

<!-- Текст с несколькими переменными -->
<p th:text="#{email.vacancy.text(${tournamentName}, ${minutes})}">...</p>

<!-- Ссылка: href из переменной, текст из #{key} -->
<a th:href="${dashboardUrl}" th:text="#{email.bienvenida.btn}">Ir al Dashboard</a>

<!-- Год из переменной (не переводим) -->
<span th:text="${year}">2026</span>

<!-- Текст + переменная внутри (NE использовать конкатенацию, лучше #{key(var)}) -->
<!-- НЕ так: -->
<p th:text="'Hola ' + ${nombre} + '!'">Hola!</p>
<!-- А так: -->
<p th:text="#{email.common.greeting(${nombre})}">Hola!</p>
```

---

## Шаблон за шаблоном (порядок выполнения)

### 1. `recuperar-password.html` (~5 строк — самый простой)

```html
<!-- До -->
<h1>Recuperación de contraseña 🔐</h1>
<p>Recibimos una solicitud para restablecer tu contraseña...</p>
<a href="...">🔐 Restablecer contraseña</a>
<p>Este enlace expirará en 24 horas.</p>
<p>Si no solicitaste este cambio, ignora este correo.</p>

<!-- После -->
<h1 th:text="#{email.password_reset.title}">Recuperación de contraseña 🔐</h1>
<p th:text="#{email.password_reset.text}">Recibimos una solicitud...</p>
<a th:href="${resetUrl}" th:text="#{email.password_reset.btn}">🔐 Restablecer contraseña</a>
<p th:text="#{email.password_reset.expiry(24)}">Este enlace expirará en 24 horas.</p>
<p th:text="#{email.password_reset.ignore}">Si no solicitaste...</p>
```

### 2. `confirmacion.html` (~10 строк)

```html
<!-- До -->
<h1>Confirma tu dirección de email</h1>
<p>Hola <span th:text="${nombre}">Jugador</span>, gracias por registrarte...</p>

<!-- После -->
<h1 th:text="#{email.confirmacion.title}">Confirma tu dirección de email</h1>
<p th:text="#{email.confirmacion.greeting(${nombre})}">Hola Jugador, gracias...</p>
```

### 3. `bienvenida.html` (~14 строк)

```html
<!-- До -->
<h1>🎾 ¡Bienvenido a 1-Padel!</h1>
<p class="greeting" th:text="'Hola ' + ${nombre} + '!'">Hola Jugador!</p>
<p>Tu email ha sido confirmado exitosamente...</p>
<span class="feature-text">Inscribirte en torneos de tu nivel</span>

<!-- После -->
<h1 th:text="#{email.bienvenida.title}">🎾 ¡Bienvenido a 1-Padel!</h1>
<p class="greeting" th:text="#{email.common.greeting(${nombre})}">Hola Jugador!</p>
<p th:text="#{email.bienvenida.confirmed}">Tu email ha sido confirmado...</p>
<span class="feature-text" th:text="#{email.bienvenida.feature.tournaments}">Inscribirte en torneos</span>
```

### 4. `waitlist-notification.html` (~9 строк)

```html
<h1 th:text="#{email.waitlist.title}">Estás en lista de espera 📋</h1>
<p th:text="#{email.waitlist.text(${tournamentName})}">El torneo X está completo...</p>
<p th:text="#{email.waitlist.explanation}">Si alguien cancela...</p>
<p th:text="#{email.waitlist.position(${position})}">Tu posición: #3</p>
```

### 5. `no-spots-left.html` (~11 строк)

```html
<h1 th:text="#{email.no_spots.title}">Torneo completo 😔</h1>
<p th:text="#{email.no_spots.text(${tournamentName})}">Lo sentimos...</p>
<p th:text="#{email.no_spots.suggestion}">Te avisaremos...</p>
<a th:href="${tourneosUrl}" th:text="#{email.no_spots.btn}">Ver otros torneos</a>
```

### 6. `tournament-confirmation.html` (~13 строк)

```html
<h1 th:text="#{email.tournament_confirm.title}">¡Tu inscripción está confirmada! 🎾</h1>
<p th:text="#{email.tournament_confirm.greeting(${playerName})}">¡Hola Jugador!</p>
<p th:text="#{email.tournament_confirm.text}">Te has inscrito exitosamente...</p>
<!-- Лейблы таблицы с деталями -->
<td th:text="#{email.tournament_confirm.label.tournament}">Torneo</td>
<td th:text="#{email.tournament_confirm.label.club}">Club</td>
<!-- Значения — из переменных Context, не переводим -->
<td th:text="${tournamentName}">Copa de Verano</td>
```

### 7. `new-partner-invitation.html` (~12 строк)

```html
<h1 th:text="#{email.partner_invitation.title}">¡Tienes una invitación de pareja! 🤝</h1>
<p th:text="#{email.partner_invitation.text(${mainPlayerName})}">Juan te ha invitado...</p>
<td th:text="#{email.partner_invitation.label.partner}">Tu compañero/a</td>
<p th:text="#{email.partner_invitation.expiry(${hoursLeft})}">Tienes 24 horas...</p>
<a th:href="${acceptUrl}" th:text="#{email.partner_invitation.btn}">✅ Aceptar</a>
```

### 8. `vacancy-invitation.html` (~9 строк)

```html
<h1 th:text="#{email.vacancy.title}">¡Un lugar se liberó! 🎉</h1>
<p th:text="#{email.vacancy.text(${tournamentName}, ${minutes})}">Se abrió un lugar...</p>
<p th:text="#{email.vacancy.deadline(${deadlineTime})}">⏰ Tiempo límite: 15:30</p>
<a th:href="${confirmUrl}" th:text="#{email.vacancy.btn}">✅ Confirmar mi lugar</a>
<p th:text="#{email.vacancy.warning}">Si no confirmas...</p>
```

### 9. `pair-proposal.html` и `partner-data-reminder.html` и `looking-for-partner-notification.html`

Аналогично по образцу выше — выписать ключи, заменить строки.

---

## Footer (общий для всех шаблонов)

Каждый шаблон содержит одинаковый footer — заменить во всех 10:

```html
<!-- До -->
<p>© <span th:text="${year}">2026</span> 1-Padel. Todos los derechos reservados.</p>
<a th:href="${terminosUrl}">Términos</a>
<a th:href="${privacidadUrl}">Privacidad</a>
<p>¿No quieres recibir más correos?
   <a th:href="${unsubscribeUrl}">Cancela tu suscripción</a>
</p>

<!-- После -->
<p>© <span th:text="${year}">2026</span>
   <th:block th:text="#{email.footer.rights(${year})}"> 1-Padel. Todos los derechos reservados.</th:block>
</p>
<a th:href="${terminosUrl}" th:text="#{email.footer.terms}">Términos</a>
<a th:href="${privacidadUrl}" th:text="#{email.footer.privacy}">Privacidad</a>
<p th:text="#{email.footer.unsubscribe.text}">¿No quieres recibir más correos?</p>
<a th:href="${unsubscribeUrl}" th:text="#{email.footer.unsubscribe.link}">Cancela tu suscripción</a>
```

---

## `admin-solo-registration.html` — НЕ трогаем

Этот шаблон идёт администратору, не игроку. Оставить на испанском.

---

## Тестирование email переводов

### Через тест регистрации

1. Зарегистрироваться с браузером на языке `ru` → получить письмо подтверждения на русском
2. Зарегистрироваться с `es` → письмо на испанском
3. Зарегистрироваться с `en` → письмо на английском

### Через тест смены языка в профиле

1. Войти → профиль → сменить язык на `ru` → сохранить
2. Записаться на турнир → письмо-подтверждение должно прийти на русском

### Через MailHog (если настроен локально)

```bash
# Запустить MailHog для перехвата писем
docker run -p 1025:1025 -p 8025:8025 mailhog/mailhog

# В application-dev.yml:
# spring.mail.host=localhost
# spring.mail.port=1025
```

Открыть `http://localhost:8025` → просмотреть письма.

---

## Чеклист этапа 10

- [ ] `recuperar-password.html` — все строки заменены на `#{key}`
- [ ] `confirmacion.html` — все строки заменены
- [ ] `bienvenida.html` — все строки заменены
- [ ] `waitlist-notification.html` — все строки заменены
- [ ] `no-spots-left.html` — все строки заменены
- [ ] `tournament-confirmation.html` — все строки заменены
- [ ] `new-partner-invitation.html` — все строки заменены
- [ ] `vacancy-invitation.html` — все строки заменены
- [ ] `pair-proposal.html` — все строки заменены
- [ ] `partner-data-reminder.html` — все строки заменены
- [ ] `looking-for-partner-notification.html` — все строки заменены
- [ ] Footer переведён во всех 10 шаблонах
- [ ] `admin-solo-registration.html` — **не тронут**
- [ ] Приложение компилируется без ошибок
- [ ] При тесте регистрации с `Accept-Language: ru` письмо приходит на русском
- [ ] При тесте со сменой языка в профиле — новые письма на новом языке
- [ ] HTML в письмах не сломан (проверить в MailHog или реальном клиенте)
- [ ] Тема письма (subject) тоже переведена
