# Этап 16: QA — тестирование admin i18n

## Цель

Убедиться что i18n работает корректно для всех трёх языков на всех admin страницах, без регрессий в функционале.

---

## Особенность admin QA

Admin страницы требуют аутентификации (`ROLE_OWNER` / `ROLE_SUPER_ADMIN`). Для curl-тестирования нужна авторизация.

```bash
# Вариант 1: через session cookie (после логина в браузере)
curl -s -H "Accept-Language: ru" \
     -H "Cookie: JSESSIONID=<session>" \
     http://localhost:8080/admin | grep -o '<h1[^>]*>.*</h1>'

# Вариант 2: через BasicAuth (если включён)
curl -s -u admin@email.com:password \
     -H "Accept-Language: ru" \
     http://localhost:8080/admin

# Вариант 3 (рекомендуется): браузер + ModHeader (расширение Chrome/Firefox)
# Добавить header Accept-Language: ru → зайти под admin аккаунтом
```

---

## Матрица тестирования — HTML шаблоны

| Страница | URL | es | ru | en |
|----------|-----|----|----|-----|
| `admin/panel.html` | `/admin` | ☐ | ☐ | ☐ |
| `admin/tournaments/list.html` | `/admin/tournaments` | ☐ | ☐ | ☐ |
| `admin/tournaments/form.html` (create) | `/admin/tournaments/new` | ☐ | ☐ | ☐ |
| `admin/tournaments/form.html` (edit) | `/admin/tournaments/{id}/edit` | ☐ | ☐ | ☐ |
| `admin/tournaments/details.html` | `/admin/tournaments/{id}` | ☐ | ☐ | ☐ |
| `admin/tournaments/king-of-court.html` | `/admin/tournaments/king-of-court/{id}` | ☐ | ☐ | ☐ |
| `admin/tournaments/payments.html` | `/payments/{id}` | ☐ | ☐ | ☐ |
| `admin/americano/tournament.html` | `/admin/americano/{id}` | ☐ | ☐ | ☐ |
| `admin/americano/tournament-double.html` | `/admin/americano/{id}/double` | ☐ | ☐ | ☐ |
| `admin/americano/tournament-playoff.html` | `/admin/americano/{id}/playoff` | ☐ | ☐ | ☐ |
| `admin/americano/preview-rounds.html` | `/admin/americano/{id}/preview-rounds` | ☐ | ☐ | ☐ |
| `admin/americano/preview-double-rounds.html` | `/admin/americano/{id}/preview-double-rounds` | ☐ | ☐ | ☐ |
| `admin/clubs/list.html` | `/admin/clubs` | ☐ | ☐ | ☐ |
| `admin/clubs/form.html` (create) | `/admin/clubs/new` | ☐ | ☐ | ☐ |
| `admin/clubs/details.html` | `/admin/clubs/{id}` | ☐ | ☐ | ☐ |
| `admin/players/list.html` | `/admin/players` | ☐ | ☐ | ☐ |
| `admin/players/details.html` | `/admin/players/{id}` | ☐ | ☐ | ☐ |

---

## Матрица тестирования — JS функционал

| Действие | Файл | es | ru | en |
|----------|------|----|----|-----|
| Удаление элемента (успех/ошибка) | `admin.js` | ☐ | ☐ | ☐ |
| Изменение статуса (успех/ошибка) | `admin.js` | ☐ | ☐ | ☐ |
| Статусы матчей Americano (Pendiente/En curso/...) | `admin-americano.js` | ☐ | ☐ | ☐ |
| Начать раунд Americano | `admin-americano.js` | ☐ | ☐ | ☐ |
| Завершить раунд Americano | `admin-americano.js` | ☐ | ☐ | ☐ |
| Сохранить результат Americano | `admin-americano.js` | ☐ | ☐ | ☐ |
| Confirm: инициализация KoC | `king-of-court.js` | ☐ | ☐ | ☐ |
| Статус корта (EN JUEGO / COMPLETADO) | `king-of-court.js` | ☐ | ☐ | ☐ |
| Имя неизвестного игрока в KoC | `king-of-court.js` | ☐ | ☐ | ☐ |
| Victory/Defeat в истории KoC | `king-of-court.js` | ☐ | ☐ | ☐ |
| Сохранить/обновить результат KoC | `king-of-court.js` | ☐ | ☐ | ☐ |
| Следующий раунд KoC | `king-of-court.js` | ☐ | ☐ | ☐ |
| Завершить KoC (успех/ошибка) | `king-of-court.js` | ☐ | ☐ | ☐ |
| YouTube ссылка KoC | `king-of-court.js` | ☐ | ☐ | ☐ |
| Ошибки валидации результата KoC | `king-of-court.js` | ☐ | ☐ | ☐ |

---

## Регрессионное тестирование функционала

После перевода убедиться что основной функционал не сломан:

- [ ] Создание турнира (форма сабмитится, данные сохраняются)
- [ ] Редактирование турнира (данные предзаполнены)
- [ ] Изменение статуса турнира (UPCOMING → ACTIVE → FINISHED)
- [ ] Перенос игрока в waitlist / из waitlist
- [ ] Инициализация King of Court
- [ ] Следующий раунд / rollback KoC
- [ ] Инициализация Americano
- [ ] Старт/завершение раундов Americano
- [ ] Сохранение результата матча (Americano + KoC)
- [ ] Создание клуба
- [ ] Активация/деактивация игрока
- [ ] Экспорт Excel (`/admin/export`)

---

## Проверка на missing keys

```bash
# В логах Spring (dev mode) НЕ должно быть:
grep "NoSuchMessageException\|No message found" logs/app.log

# В консоли браузера НЕ должно быть строк вида:
# admin.panel.title
# admin.tournaments.form.field.club
# (ключ вместо перевода = t() вернул сам ключ = ключ не найден в messages-*.js)
```

---

## Визуальные проверки

### Нет смешения языков
- [ ] Все элементы admin-панели на одном языке
- [ ] Исключения (нормально): имена турниров/клубов/игроков (это данные из БД, не UI)
- [ ] Исключения: коды валют (`ARS`, `USD`), категории (`C9`, `C8`)
- [ ] Исключения: формат турнира (`Americano`, `King of Court`, `Playoff`)

### Layout не ломается
- [ ] Russian: длинные строки в колонках таблиц не переполняются
- [ ] Особое внимание: кнопки в `<th>` заголовках, кнопки действий в строках таблицы
- [ ] Формы: длинные labels не ломают layout формы

### Форматы title (`<title>`)
- [ ] ru: `Панель администратора - 1-Padel`
- [ ] en: `Administration Panel - 1-Padel`
- [ ] es: `Panel de Administración - 1-Padel`

---

## Проверка fallback

Admin-панель используется только одним языком (русским — владелец платформы). Тем не менее:

```bash
# Неподдерживаемый язык → должен показать испанский (es — default)
curl -s -u ... -H "Accept-Language: fr" http://localhost:8080/admin | grep '<html'
# Ожидаем: <html lang="es" ...>
```

---

## Чеклист этапа 16

- [ ] Все 17 admin-страниц из матрицы проверены в трёх языках
- [ ] Все JS действия из матрицы проверены в трёх языках
- [ ] Нет `NoSuchMessageException` в логах
- [ ] Нет ключей (вместо текста) в консоли браузера
- [ ] Нет смешения языков на страницах
- [ ] Layout не ломается на русском
- [ ] `<html lang="">` обновляется для каждого языка
- [ ] Весь функционал admin-панели работает без регрессий
