# Этап 5: Интеграция — замена строк в HTML шаблонах

## Цель

Заменить хардкоженные испанские строки в 17 шаблонах на `#{key}`.  
Все ключи уже определены в этапах 2-4.

---

## Thymeleaf синтаксис i18n

```html
<!-- Текстовое содержимое тега -->
<h2 th:text="#{login.title}">Iniciar sesión</h2>

<!-- Текст с HTML (когда в переводе есть <strong>, <a>) -->
<div th:utext="#{login.error.rate_limit.text}">...</div>

<!-- Атрибут placeholder -->
<input th:placeholder="#{login.email.placeholder}">

<!-- Атрибут title / aria-label -->
<button th:title="#{common.btn.close}">×</button>

<!-- Строка с переменной -->
<p th:text="#{dashboard.welcome(${playerName})}">Bienvenido</p>

<!-- Условный текст -->
<span th:text="${isBlocked} ? #{error.rate_limit.title.blocked} : #{error.rate_limit.title.limit}">...</span>

<!-- В атрибуте th:attr (для нескольких атрибутов) -->
<input th:attr="placeholder=#{login.email.placeholder}, title=#{login.email.label}">

<!-- В JavaScript inline (ТОЛЬКО для статических строк, не для динамических) -->
<script th:inline="javascript">
    const welcomeMsg = /*[[#{dashboard.title}]]*/ 'Mi Dashboard';
</script>
```

---

## Подключение JS i18n в каждый шаблон

В `<head>` каждого шаблона добавить **перед** закрывающим `</head>`:

```html
<!-- i18n scripts: детектируем язык из браузера, грузим нужный файл -->
<script th:src="@{/js/i18n/t.js}"></script>
<script th:with="lang=${#locale.language == 'ru' ? 'ru' : (#locale.language == 'en' ? 'en' : 'es')}"
        th:src="@{'/js/i18n/messages-' + ${lang} + '.js'}"></script>
```

Также добавить `th:lang` в `<html>`:
```html
<html lang="es" th:lang="${#locale.language}" xmlns:th="http://www.thymeleaf.org">
```

---

## Шаблон за шаблоном

### ✅ Порядок выполнения (от простых к сложным)

1. `error/rate-limit.html` — ~5 строк, хорошая точка проверки инфраструктуры
2. `accept-pair.html` — ~3 строки, простой
3. `waitlist-confirmation.html` — ~9 строк
4. `login.html` — ~15 строк, важный
5. `registro.html` — ~22 строки
6. `clubs/list.html` — ~7 строк
7. `clubs/view.html` — ~9 строк
8. `king-of-court-view.html` — ~16 строк
9. `ranking.html` — ~25 строк
10. `players/perfil.html` — ~24 строки
11. `torneos.html` — ~29 строк
12. `tournaments/americano/view.html` — ~23 строки
13. `tournaments/team-americano/view.html` — ~27 строк
14. `tournaments/team-playoff/view.html` — ~36 строк
15. `players/dashboard.html` — ~57 строк (сложный)
16. `tournament-details.html` — ~69 строк (сложный)
17. `index.html` — ~69 строк (самый сложный, много динамики)

---

## Типичные паттерны замены

### Простой текст
```html
<!-- До -->
<h2 class="auth-card-title">Iniciar sesión</h2>

<!-- После -->
<h2 class="auth-card-title" th:text="#{login.title}">Iniciar sesión</h2>
```

### Ссылка с текстом
```html
<!-- До -->
<a href="/players/registro" class="auth-link">Regístrate gratis</a>

<!-- После -->
<a href="/players/registro" class="auth-link" th:text="#{login.subtitle.link}">Regístrate gratis</a>
```

### Текст с HTML (rate-limit ошибка)
```html
<!-- До -->
<div>...espera <strong>1 minuto</strong>...</div>

<!-- После: th:utext! -->
<div th:utext="#{login.error.rate_limit.text}">...</div>
```

### Placeholder в input
```html
<!-- До -->
<input type="email" placeholder="tu@email.com">

<!-- После -->
<input type="email" th:placeholder="#{login.email.placeholder}">
```

### Строка с переменной
```html
<!-- До -->
<p>Bienvenido, [[${playerName}]]!</p>

<!-- После -->
<p th:text="#{dashboard.welcome(${playerName})}">Bienvenido</p>
```

### aria-label
```html
<!-- До -->
<button aria-label="Cerrar">×</button>

<!-- После -->
<button th:attr="aria-label=#{common.btn.close}">×</button>
```

---

## Подводные камни

### 1. `th:text` vs `th:utext`
- `th:text` — экранирует HTML (безопасно, использовать по умолчанию)
- `th:utext` — рендерит HTML как есть (только если в переводе есть `<strong>`, `<a>` и т.д.)
- Проверь: в каких ключах `messages_es.properties` есть HTML-теги → они требуют `th:utext`

### 2. Thymeleaf inline expression в атрибутах
Некоторые строки в шаблонах живут внутри `th:if`, `th:classappend` и т.д. — там нельзя просто добавить `th:text`. Пример:
```html
<!-- Было: текст внутри th:if блока -->
<div th:if="${isBlocked}">Acceso bloqueado</div>

<!-- Становится: -->
<div th:if="${isBlocked}" th:text="#{error.rate_limit.title.blocked}">Acceso bloqueado</div>
```

### 3. Строки в `<title>`
```html
<!-- До -->
<title>1-Padel - Iniciar sesión</title>

<!-- После -->
<title th:text="'1-Padel - ' + #{login.title}">1-Padel - Iniciar sesión</title>
```

### 4. Кнопки типа submit
```html
<!-- До -->
<button type="submit">Iniciar sesión</button>

<!-- После -->
<button type="submit" th:text="#{login.submit}">Iniciar sesión</button>
```

### 5. Оригинальный текст как fallback
Thymeleaf рекомендует оставлять статический текст внутри тегов как fallback при рендеринге без Spring (например, статический HTML preview). Это не обязательно, но хорошая практика.

---

## Правило для inline JS в шаблонах

Если в шаблоне есть `<script th:inline="javascript">` с испанскими строками — эти строки **переносить в messages-es.js** (этап 6), а не в messages.properties. Из шаблона убираем строку, в JS используем `t('key')`.

**Исключение**: строки, которые нужны как Thymeleaf-переменные для JS (например, имя игрока из модели) — передаём через `th:inline="javascript"` как раньше, но без перевода (это данные, не UI).

---

## Чеклист этапа 5

Для каждого шаблона:
- [ ] `<html>` имеет `th:lang="${#locale.language}"`
- [ ] В `<head>` добавлены `t.js` и `messages-xx.js` скрипты
- [ ] Все пользовательски видимые строки заменены на `#{key}`
- [ ] Placeholders заменены через `th:placeholder="#{key}"`
- [ ] HTML-содержащие переводы используют `th:utext`
- [ ] Строки с переменными используют правильный синтаксис `#{key(var)}`
- [ ] `<title>` страниц обновлён
- [ ] Страница открывается без ошибок в браузере
- [ ] При `Accept-Language: ru` на странице отображается русский текст
- [ ] При `Accept-Language: es` отображается испанский (без регрессий)
