# Этап 9: Ключи для email шаблонов → messages_xx.properties

## Цель

Выписать все текстовые строки из 10 email-шаблонов (без admin-solo-registration) как ключи  
в `messages_es.properties` (добавить секцию `email.*`) и перевести в ru + en.

Email-ключи добавляются в **те же** `messages_es.properties` / `messages_ru.properties` / `messages_en.properties`  
что используются для UI. Отдельные файлы не нужны.

---

## Общий footer и header email (повторяется во всех шаблонах)

```properties
# === EMAIL COMMON ===
email.footer.rights=© {0} 1-Padel. Todos los derechos reservados.
email.footer.terms=Términos
email.footer.privacy=Privacidad
email.footer.contact=Contacto
email.footer.unsubscribe.text=¿No quieres recibir más correos?
email.footer.unsubscribe.link=Cancela tu suscripción
email.footer.social.instagram=Instagram
email.footer.social.facebook=Facebook
email.footer.social.twitter=Twitter

email.common.greeting=Hola {0}!
email.common.btn.dashboard=🚀 Ir al Dashboard
email.common.btn.view_tournament=🎾 Ver torneo
email.common.btn.confirm=✅ Confirmar
```

---

## `bienvenida.html` — письмо после подтверждения email

```properties
email.bienvenida.subject=¡Bienvenido a 1-Padel!
email.bienvenida.title=🎾 ¡Bienvenido a 1-Padel!
email.bienvenida.confirmed=Tu email ha sido confirmado exitosamente. Ya puedes disfrutar de todas las funcionalidades:
email.bienvenida.feature.tournaments=Inscribirte en torneos de tu nivel
email.bienvenida.feature.ranking=Ver tu ranking y progreso
email.bienvenida.feature.connect=Conectar con otros jugadores
email.bienvenida.feature.koc=Participar en el King of Court
email.bienvenida.btn=🚀 Ir al Dashboard
```

**Перевод RU:**
```properties
email.bienvenida.subject=Добро пожаловать в 1-Padel!
email.bienvenida.title=🎾 Добро пожаловать в 1-Padel!
email.bienvenida.confirmed=Ваш email подтверждён. Теперь вы можете пользоваться всеми функциями:
email.bienvenida.feature.tournaments=Записывайтесь на турниры вашего уровня
email.bienvenida.feature.ranking=Следите за своим рейтингом и прогрессом
email.bienvenida.feature.connect=Находите партнёров для игры
email.bienvenida.feature.koc=Участвуйте в King of Court
email.bienvenida.btn=🚀 Перейти в личный кабинет
```

**Перевод EN:**
```properties
email.bienvenida.subject=Welcome to 1-Padel!
email.bienvenida.title=🎾 Welcome to 1-Padel!
email.bienvenida.confirmed=Your email has been confirmed. You can now enjoy all features:
email.bienvenida.feature.tournaments=Register for tournaments at your level
email.bienvenida.feature.ranking=Track your ranking and progress
email.bienvenida.feature.connect=Connect with other players
email.bienvenida.feature.koc=Participate in King of Court
email.bienvenida.btn=🚀 Go to Dashboard
```

---

## `confirmacion.html` — подтверждение email при регистрации

```properties
# ES
email.confirmacion.subject=Confirma tu email - 1-Padel
email.confirmacion.title=Confirma tu dirección de email
email.confirmacion.greeting=Hola {0}, gracias por registrarte en 1-Padel.
email.confirmacion.text=Para completar tu registro, confirma tu dirección de email haciendo clic en el botón:
email.confirmacion.btn=✅ Confirmar mi email
email.confirmacion.expiry=Este enlace expirará en {0} horas.
email.confirmacion.ignore=Si no te registraste en 1-Padel, puedes ignorar este mensaje.

# RU
email.confirmacion.subject=Подтвердите email — 1-Padel
email.confirmacion.title=Подтвердите адрес электронной почты
email.confirmacion.greeting=Привет {0}, спасибо за регистрацию в 1-Padel.
email.confirmacion.text=Для завершения регистрации подтвердите email, нажав кнопку:
email.confirmacion.btn=✅ Подтвердить email
email.confirmacion.expiry=Ссылка действительна {0} часов.
email.confirmacion.ignore=Если вы не регистрировались в 1-Padel, проигнорируйте это письмо.

# EN
email.confirmacion.subject=Confirm your email - 1-Padel
email.confirmacion.title=Confirm your email address
email.confirmacion.greeting=Hi {0}, thank you for registering at 1-Padel.
email.confirmacion.text=To complete your registration, confirm your email by clicking the button:
email.confirmacion.btn=✅ Confirm my email
email.confirmacion.expiry=This link will expire in {0} hours.
email.confirmacion.ignore=If you didn't register at 1-Padel, you can ignore this message.
```

---

## `tournament-confirmation.html` — подтверждение регистрации на турнир

```properties
# ES
email.tournament_confirm.subject=Inscripción confirmada - {0}
email.tournament_confirm.title=¡Tu inscripción está confirmada! 🎾
email.tournament_confirm.greeting=¡Hola {0}!
email.tournament_confirm.text=Te has inscrito exitosamente en el torneo:
email.tournament_confirm.label.tournament=Torneo
email.tournament_confirm.label.club=Club
email.tournament_confirm.label.date=Fecha
email.tournament_confirm.label.time=Hora
email.tournament_confirm.label.level=Nivel
email.tournament_confirm.label.format=Formato
email.tournament_confirm.note=Recuerda traer tu raqueta y llegar con 15 minutos de anticipación.
email.tournament_confirm.btn=🎾 Ver detalles del torneo

# RU
email.tournament_confirm.subject=Запись подтверждена — {0}
email.tournament_confirm.title=Вы записаны на турнир! 🎾
email.tournament_confirm.greeting=Привет {0}!
email.tournament_confirm.text=Вы успешно записались на турнир:
email.tournament_confirm.label.tournament=Турнир
email.tournament_confirm.label.club=Клуб
email.tournament_confirm.label.date=Дата
email.tournament_confirm.label.time=Время
email.tournament_confirm.label.level=Уровень
email.tournament_confirm.label.format=Формат
email.tournament_confirm.note=Не забудьте ракетку и приходите за 15 минут до начала.
email.tournament_confirm.btn=🎾 Подробнее о турнире

# EN
email.tournament_confirm.subject=Registration confirmed - {0}
email.tournament_confirm.title=You're registered! 🎾
email.tournament_confirm.greeting=Hi {0}!
email.tournament_confirm.text=You have successfully registered for the tournament:
email.tournament_confirm.label.tournament=Tournament
email.tournament_confirm.label.club=Club
email.tournament_confirm.label.date=Date
email.tournament_confirm.label.time=Time
email.tournament_confirm.label.level=Level
email.tournament_confirm.label.format=Format
email.tournament_confirm.note=Remember to bring your racket and arrive 15 minutes early.
email.tournament_confirm.btn=🎾 View tournament details
```

---

## `new-partner-invitation.html` — приглашение второго игрока в пару

```properties
# ES
email.partner_invitation.subject=Te invitaron a jugar en pareja - {0}
email.partner_invitation.title=¡Tienes una invitación de pareja! 🤝
email.partner_invitation.text={0} te ha invitado a jugar en pareja en:
email.partner_invitation.label.partner=Tu compañero/a
email.partner_invitation.label.tournament=Torneo
email.partner_invitation.label.date=Fecha
email.partner_invitation.expiry=Tienes {0} horas para aceptar. Después la invitación expirará.
email.partner_invitation.btn=✅ Aceptar invitación
email.partner_invitation.decline=Si no quieres participar, simplemente ignora este mensaje.

# RU
email.partner_invitation.subject=Вас приглашают в пару — {0}
email.partner_invitation.title=Приглашение на парный турнир! 🤝
email.partner_invitation.text={0} приглашает вас сыграть в паре на турнире:
email.partner_invitation.label.partner=Ваш партнёр
email.partner_invitation.label.tournament=Турнир
email.partner_invitation.label.date=Дата
email.partner_invitation.expiry=У вас {0} часов, чтобы принять приглашение.
email.partner_invitation.btn=✅ Принять приглашение
email.partner_invitation.decline=Если не хотите участвовать — просто проигнорируйте письмо.

# EN
email.partner_invitation.subject=You've been invited to play doubles - {0}
email.partner_invitation.title=You have a doubles invitation! 🤝
email.partner_invitation.text={0} has invited you to play doubles in:
email.partner_invitation.label.partner=Your partner
email.partner_invitation.label.tournament=Tournament
email.partner_invitation.label.date=Date
email.partner_invitation.expiry=You have {0} hours to accept. After that the invitation will expire.
email.partner_invitation.btn=✅ Accept invitation
email.partner_invitation.decline=If you don't want to participate, simply ignore this message.
```

---

## `vacancy-invitation.html` — приглашение из листа ожидания

```properties
# ES
email.vacancy.subject=¡Hay un lugar disponible para ti! - {0}
email.vacancy.title=¡Un lugar se liberó! 🎉
email.vacancy.text=Se abrió un lugar en el torneo {0}. Tienes {1} minutos para confirmar tu inscripción.
email.vacancy.deadline=⏰ Tiempo límite: {0}
email.vacancy.btn=✅ Confirmar mi lugar
email.vacancy.warning=Si no confirmas en el tiempo indicado, el lugar pasará al siguiente en la lista.

# RU
email.vacancy.subject=Для вас освободилось место! — {0}
email.vacancy.title=Место освободилось! 🎉
email.vacancy.text=В турнире {0} появилось место. У вас {1} минут, чтобы подтвердить участие.
email.vacancy.deadline=⏰ Крайний срок: {0}
email.vacancy.btn=✅ Подтвердить участие
email.vacancy.warning=Если не подтвердите вовремя — место перейдёт следующему в списке ожидания.

# EN
email.vacancy.subject=A spot is available for you! - {0}
email.vacancy.title=A spot opened up! 🎉
email.vacancy.text=A spot opened in tournament {0}. You have {1} minutes to confirm your registration.
email.vacancy.deadline=⏰ Deadline: {0}
email.vacancy.btn=✅ Confirm my spot
email.vacancy.warning=If you don't confirm in time, the spot will go to the next person on the waitlist.
```

---

## `waitlist-notification.html` — уведомление о попадании в лист ожидания

```properties
# ES
email.waitlist.subject=Estás en lista de espera - {0}
email.waitlist.title=Estás en lista de espera 📋
email.waitlist.text=El torneo {0} está completo, pero te añadimos a la lista de espera.
email.waitlist.explanation=Si alguien cancela su inscripción, recibirás una invitación por email.
email.waitlist.position=Tu posición en la lista: #{0}

# RU
email.waitlist.subject=Вы в списке ожидания — {0}
email.waitlist.title=Вы в списке ожидания 📋
email.waitlist.text=Турнир {0} заполнен, но мы добавили вас в список ожидания.
email.waitlist.explanation=Если кто-то отменит участие, вы получите приглашение на email.
email.waitlist.position=Ваша позиция в списке: №{0}

# EN
email.waitlist.subject=You're on the waitlist - {0}
email.waitlist.title=You're on the waitlist 📋
email.waitlist.text=Tournament {0} is full, but we've added you to the waitlist.
email.waitlist.explanation=If someone cancels, you'll receive an invitation by email.
email.waitlist.position=Your position on the list: #{0}
```

---

## `no-spots-left.html` — мест нет, даже в waitlist

```properties
# ES
email.no_spots.subject=No hay plazas disponibles - {0}
email.no_spots.title=Torneo completo 😔
email.no_spots.text=Lo sentimos, el torneo {0} ya no tiene plazas disponibles ni en lista de espera.
email.no_spots.suggestion=Te avisaremos cuando se abran nuevos torneos de tu nivel.
email.no_spots.btn=Ver otros torneos

# RU
email.no_spots.subject=Мест нет — {0}
email.no_spots.title=Турнир заполнен 😔
email.no_spots.text=К сожалению, в турнире {0} не осталось мест, в том числе в списке ожидания.
email.no_spots.suggestion=Мы сообщим, когда появятся новые турниры вашего уровня.
email.no_spots.btn=Смотреть другие турниры

# EN
email.no_spots.subject=No spots available - {0}
email.no_spots.title=Tournament full 😔
email.no_spots.text=Unfortunately, tournament {0} has no spots left, including the waitlist.
email.no_spots.suggestion=We'll notify you when new tournaments at your level open up.
email.no_spots.btn=View other tournaments
```

---

## `recuperar-password.html` — сброс пароля

```properties
# ES
email.password_reset.subject=Recupera tu contraseña - 1-Padel
email.password_reset.title=Recuperación de contraseña 🔐
email.password_reset.text=Recibimos una solicitud para restablecer tu contraseña. Haz clic en el botón:
email.password_reset.btn=🔐 Restablecer contraseña
email.password_reset.expiry=Este enlace expirará en {0} horas.
email.password_reset.ignore=Si no solicitaste este cambio, ignora este correo. Tu contraseña no cambiará.

# RU
email.password_reset.subject=Восстановление пароля — 1-Padel
email.password_reset.title=Сброс пароля 🔐
email.password_reset.text=Мы получили запрос на сброс пароля. Нажмите кнопку:
email.password_reset.btn=🔐 Сбросить пароль
email.password_reset.expiry=Ссылка действительна {0} часов.
email.password_reset.ignore=Если вы не запрашивали сброс пароля — проигнорируйте это письмо.

# EN
email.password_reset.subject=Reset your password - 1-Padel
email.password_reset.title=Password reset 🔐
email.password_reset.text=We received a request to reset your password. Click the button:
email.password_reset.btn=🔐 Reset password
email.password_reset.expiry=This link will expire in {0} hours.
email.password_reset.ignore=If you didn't request a password reset, ignore this email. Your password won't change.
```

---

## `pair-proposal.html` и `partner-data-reminder.html`

Аналогичная структура ключей — выписать по образцу выше при реализации.

```properties
# pair-proposal.html
email.pair_proposal.subject=...
email.pair_proposal.title=...

# partner-data-reminder.html
email.partner_reminder.subject=...
email.partner_reminder.title=...
```

---

## `looking-for-partner-notification.html`

```properties
email.looking_partner.subject=Alguien busca pareja para {0}
email.looking_partner.title=¡Hay un jugador buscando pareja! 🎾
# ... аналогично
```

---

## Тема письма (subject)

`subject` — это не поле шаблона, а параметр `MimeMessage.setSubject()` в `EmailService`.  
Нужно локализовать и его через `MessageSource`:

```java
// В EmailService добавить:
@Autowired
private MessageSource messageSource;

// Использование:
String subject = messageSource.getMessage(
    "email.tournament_confirm.subject",
    new Object[]{tournamentName},
    player.getLocale()
);
message.setSubject(subject, "UTF-8");
```

---

## Чеклист этапа 9

- [ ] В `messages_es.properties` добавлена секция `# === EMAIL ===` со всеми ключами
- [ ] Все ключи `email.*` переведены в `messages_ru.properties`
- [ ] Все ключи `email.*` переведены в `messages_en.properties`
- [ ] `email.subject` ключи тоже добавлены и переведены
- [ ] Строки с `{0}`, `{1}` используют формат MessageFormat
- [ ] `admin-solo-registration.html` — ключи НЕ добавляются (этот шаблон не переводится)
- [ ] `EmailService` обновлён для локализации темы письма через `MessageSource`
