# Этап 8: Инфраструктура для email i18n

## Проблема

Email отправляется асинхронно — часто через действие **администратора**, а не самого игрока.  
В момент отправки нет HTTP-запроса игрока → нет `Accept-Language` → нельзя определить язык.

**Решение**: хранить `preferredLocale` в профиле игрока.  
Устанавливается автоматически при регистрации, изменяется в профиле.

---

## 8.1 Новое поле в `PlayerPadel`

```java
// PlayerPadel.java — добавить поле
@Column(name = "preferred_locale", length = 5)
private String preferredLocale = "es";  // default: испанский

// Вспомогательный метод
public Locale getLocale() {
    return Locale.forLanguageTag(preferredLocale != null ? preferredLocale : "es");
}
```

---

## 8.2 Liquibase миграция

Создать файл: `src/main/resources/db/changelog/versions/v1.31-add-preferred-locale.yaml`

```yaml
databaseChangeLog:
  - changeSet:
      id: v1.31-add-preferred-locale
      author: i18n
      changes:
        - addColumn:
            tableName: player_padel_db
            columns:
              - column:
                  name: preferred_locale
                  type: VARCHAR(5)
                  defaultValue: 'es'
                  constraints:
                    nullable: false
```

Добавить `include` в конец `changelog-master.yaml`:
```yaml
- include:
    file: db/changelog/versions/v1.31-add-preferred-locale.yaml
```

---

## 8.3 Авто-определение языка при регистрации

### В `PlayerPadelController.registrarJugador()`

Считать `Accept-Language` из запроса и сохранить:

```java
@PostMapping("/registro")
public String registrarJugador(
        ...,
        HttpServletRequest httpRequest) {

    // Определяем предпочтительный язык из браузера
    String preferredLocale = resolvePreferredLocale(httpRequest);
    request.setPreferredLocale(preferredLocale);

    // ... остальная логика регистрации
}

private String resolvePreferredLocale(HttpServletRequest request) {
    // Используем тот же AcceptHeaderLocaleResolver что настроен глобально
    Locale locale = localeResolver.resolveLocale(request);
    String lang = locale.getLanguage();
    // Поддерживаем только es, ru, en — иначе es
    return Set.of("es", "ru", "en").contains(lang) ? lang : "es";
}
```

Инжектировать `localeResolver` через `@Autowired LocaleResolver localeResolver`.

### В `RegistroRequestDto`

Добавить поле:
```java
private String preferredLocale = "es";
```

### В `PlayerService.crearJugador()`

Убедиться что `preferredLocale` сохраняется в сущность при создании.

### Google OAuth2 (CustomOAuth2UserService)

При создании нового игрока через OAuth2 тоже нужно записать locale.  
Здесь HTTP-запрос доступен через `OAuth2AuthorizationRequest`:

```java
// В CustomOAuth2UserService или OAuth2AuthenticationSuccessHandler
// установить preferredLocale из request.getLocale()
```

---

## 8.4 Изменение языка из профиля

### В `players/perfil.html` (этап 5 уже добавит этот блок)

```html
<div class="form-group">
    <label class="form-label" th:text="#{perfil.language.label}">Idioma</label>
    <select name="preferredLocale" class="form-control">
        <option value="es" th:selected="${player.preferredLocale == 'es'}">🇦🇷 Español</option>
        <option value="ru" th:selected="${player.preferredLocale == 'ru'}">🇷🇺 Русский</option>
        <option value="en" th:selected="${player.preferredLocale == 'en'}">🇺🇸 English</option>
    </select>
</div>
```

### В `RegistroRequestDto` / `PlayerUpdateDto`

Добавить `preferredLocale` в DTO обновления профиля.  
Валидация: допустимые значения `es`, `ru`, `en`.

---

## 8.5 Изменения в `EmailService`

Каждый метод отправки использует `new Context()` → заменить на `new Context(player.getLocale())`.

### Текущий код (пример):
```java
Context context = new Context();
context.setVariable("nombre", nombre);
// ...
String htmlContent = templateEngine.process("email/confirmacion", context);
```

### Новый код:
```java
Context context = new Context(player.getLocale());  // ← язык из профиля
context.setVariable("nombre", nombre);
// ...
String htmlContent = templateEngine.process("email/confirmacion", context);
```

### Методы EmailService для обновления (31 метод, проверить каждый):

Идентифицировать какой `PlayerPadel` является **получателем** письма и передать его `getLocale()`.

| Метод | Получатель |
|-------|-----------|
| `sendConfirmationEmail(...)` | новый игрок при регистрации |
| `sendWelcomeEmail(...)` | новый игрок |
| `sendTournamentConfirmation(...)` | игрок, которого подтвердили |
| `sendPartnerInvitation(...)` | второй игрок в паре |
| `sendPartnerDataReminder(...)` | игрок |
| `sendVacancyInvitation(...)` | игрок из waitlist |
| `sendWaitlistNotification(...)` | игрок попавший в waitlist |
| `sendNoSpotsLeft(...)` | игрок |
| `sendPasswordReset(...)` | игрок |
| `sendPairProposal(...)` | игрок |
| `sendLookingForPartnerNotification(...)` | игрок |
| `sendAdminSoloRegistration(...)` | **администратор** (не переводить, оставить es) |

**Важно**: `admin-solo-registration.html` идёт администратору — его не переводим, оставляем испанский.

---

## 8.6 Передача locale в шаблоны email

Когда `Context` создан с `Locale`, Thymeleaf автоматически:
- Резолвит `#{key}` из `messages_ru.properties` если locale = `ru`
- Устанавливает `#locale` объект для условной логики в шаблоне

Дополнительно: передать locale как переменную для условного форматирования дат:

```java
context.setVariable("locale", player.getLocale());
```

В шаблоне:
```html
<!-- Форматирование даты под locale -->
<span th:text="${#temporals.format(tournamentDate, 'dd/MM/yyyy', locale)}">01/01/2026</span>
```

---

## Чеклист этапа 8

- [ ] Поле `preferredLocale` добавлено в `PlayerPadel` с дефолтом `"es"`
- [ ] Создана Liquibase миграция `v1.31-add-preferred-locale.yaml`
- [ ] Миграция добавлена в `changelog-master.yaml`
- [ ] Метод `getLocale()` добавлен в `PlayerPadel`
- [ ] `PlayerPadelController` читает `Accept-Language` и сохраняет locale при регистрации
- [ ] `CustomOAuth2UserService` тоже сохраняет locale для Google-регистраций
- [ ] `perfil.html` содержит селектор языка (3 опции: es/ru/en)
- [ ] `PlayerService` сохраняет `preferredLocale` при обновлении профиля
- [ ] Все методы `EmailService` используют `new Context(player.getLocale())` вместо `new Context()`
- [ ] `admin-solo-registration.html` остаётся на испанском (`new Context(Locale.forLanguageTag("es"))`)
- [ ] Приложение компилируется и стартует без ошибок
- [ ] При регистрации русскоязычного пользователя `preferred_locale = 'ru'` сохраняется в БД
