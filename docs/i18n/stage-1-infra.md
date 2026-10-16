# Этап 1: Инфраструктура

## Цель

Подключить Spring i18n и JS i18n-механизм. После этапа:
- `AcceptHeaderLocaleResolver` определяет язык из браузерного `Accept-Language`
- Поддерживаемые языки: `es` (default), `ru`, `en` — всё остальное fallback на `es`
- Пустые (stub) файлы переводов созданы и подключены
- JS helper `t(key)` доступен на всех страницах

---

## 1.1 Создать файлы переводов

```
src/main/resources/i18n/
├── messages.properties        ← fallback (копия es)
├── messages_es.properties     ← испанский
├── messages_ru.properties     ← русский
└── messages_en.properties     ← английский
```

На этом этапе файлы могут быть пустыми — главное что путь правильный.

---

## 1.2 Конфигурация Spring (`application.yml`)

```yaml
spring:
  messages:
    basename: i18n/messages
    encoding: UTF-8
    fallback-to-system-locale: false
    use-code-as-default-message: false
```

- `fallback-to-system-locale: false` — если язык не найден, берём messages.properties (es), а не язык сервера
- `use-code-as-default-message: false` — если ключ не найден, бросает исключение (помогает найти пропущенные ключи в dev)

---

## 1.3 Spring Bean конфигурация

Создать новый файл `config/LocaleConfig.java`:

```java
@Configuration
public class LocaleConfig implements WebMvcConfigurer {

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(Locale.forLanguageTag("es"));
        resolver.setSupportedLocales(List.of(
            Locale.forLanguageTag("es"),
            Locale.forLanguageTag("ru"),
            Locale.forLanguageTag("en")
        ));
        return resolver;
    }
}
```

**Важно**: `AcceptHeaderLocaleResolver` НЕ требует `LocaleChangeInterceptor` — он читает заголовок браузера автоматически при каждом запросе. Кнопка переключения не нужна.

**Важно**: удалить/не добавлять `SessionLocaleResolver` или `CookieLocaleResolver` — они переопределят браузерный заголовок.

---

## 1.4 JS i18n инфраструктура

### Создать `static/js/i18n/t.js`

```javascript
// Глобальная функция перевода.
// Возвращает строку по ключу из window.APP_MESSAGES.
// Если ключ не найден — возвращает сам ключ (видно при тестировании).
function t(key) {
    return (window.APP_MESSAGES && window.APP_MESSAGES[key] !== undefined)
        ? window.APP_MESSAGES[key]
        : key;
}
```

### Создать stub-файлы переводов

```
static/js/i18n/
├── t.js               ← helper
├── messages-es.js     ← { } пустой пока
├── messages-ru.js     ← { } пустой пока
└── messages-en.js     ← { } пустой пока
```

Формат каждого файла:
```javascript
// messages-es.js
window.APP_MESSAGES = {
    // ключи добавляются в этапе 3
};
```

---

## 1.5 Добавить i18n скрипты в шаблоны (паттерн)

В `<head>` каждого user-facing шаблона добавить:

```html
<!-- i18n: определяем язык и грузим нужный файл переводов -->
<script th:src="@{/js/i18n/t.js}"></script>
<script th:with="lang=${#locale.language == 'ru' ? 'ru' : (#locale.language == 'en' ? 'en' : 'es')}"
        th:src="@{'/js/i18n/messages-' + ${lang} + '.js'}"></script>
```

Это добавляется на этапе 5 (интеграция шаблонов). Здесь только документируем паттерн.

**Порядок загрузки важен**: `t.js` должен быть до `messages-xx.js`, который должен быть до страничного JS, который использует `t()`.

---

## 1.6 Проверка инфраструктуры

После настройки (ещё без реальных переводов):

```bash
# Тест 1: русский браузер
curl -s -H "Accept-Language: ru,en;q=0.9" http://localhost:8080/ | grep 'lang='
# Ожидаем: <html lang="es"> (пока переводов нет, это ок)

# Тест 2: убедиться что Spring не падает при запуске
./mvnw spring-boot:run
# Открыть http://localhost:8080/ — страница должна открыться нормально
```

После добавления первого ключа (`common.test=Hola`) можно проверить в шаблоне через `#{common.test}`.

---

## Чеклист этапа 1

- [ ] Создана директория `src/main/resources/i18n/`
- [ ] Созданы 4 файла `.properties` (могут быть пустые)
- [ ] `application.yml` — добавлены настройки `spring.messages`
- [ ] Создан `LocaleConfig.java` с `AcceptHeaderLocaleResolver`
- [ ] Создана директория `src/main/resources/static/js/i18n/`
- [ ] Создан `t.js` с функцией `t(key)`
- [ ] Созданы 3 stub JS-файла с `window.APP_MESSAGES = {}`
- [ ] Приложение запускается без ошибок
- [ ] В логах нет `NoSuchMessageException` при открытии страниц
