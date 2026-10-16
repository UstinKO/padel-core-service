# Этап 4: Переводы (ru + en)

## Цель

Заполнить `messages_ru.properties`, `messages_en.properties`, `messages-ru.js`, `messages-en.js`.  
К началу этапа `messages_es.properties` и `messages-es.js` уже полностью заполнены (этапы 2-3).

---

## Процесс

1. Скопировать `messages_es.properties` → `messages_ru.properties`
2. Перевести значения построчно
3. То же для `messages_en.properties`
4. То же для JS файлов

---

## Ключевые правила перевода

### Испанский (оставляем as-is)
```properties
nav.inicio=Inicio
login.title=Iniciar sesión
```

### Русский
```properties
nav.inicio=Главная
login.title=Войти
```

### Английский
```properties
nav.inicio=Home
login.title=Sign in
```

---

## Спорные случаи и решения

| Ключ | Проблема | Решение |
|------|----------|---------|
| `common.format.americano` | Americano — имя собственное | Не переводить, оставить `Americano` во всех языках |
| `common.format.king_of_court` | Имя собственное | Не переводить |
| Уровни игры (C9, C8...) | Категории федерации | Не переводить |
| Имена собственные (клубы, города) | — | Не переводить |
| HTML в значениях (`<strong>`, `<a>`) | Нужен `th:utext` | Сохранять HTML-теги в переводах, не убирать |

---

## Шаблон секции messages_ru.properties

```properties
# === COMMON ===
common.btn.save=Сохранить
common.btn.cancel=Отмена
common.btn.back=Назад
common.btn.close=Закрыть
common.btn.confirm=Подтвердить
common.btn.delete=Удалить
common.btn.edit=Редактировать
common.btn.register=Зарегистрироваться
common.btn.login=Войти
common.btn.retry=Попробовать снова
common.btn.home=Главная

common.label.optional=(необязательно)
common.label.required=Обязательное поле
common.label.loading=Загрузка...
common.label.nodata=Нет данных
common.label.error=Ошибка
common.label.success=Успешно

common.status.upcoming=Предстоящий
common.status.active=Активный
common.status.finished=Завершён
common.status.cancelled=Отменён

common.format.americano=Americano
common.format.king_of_court=King of Court
common.format.playoff=Плей-офф
common.format.team_americano=Team Americano

# === NAV ===
nav.inicio=Главная
nav.torneos=Турниры
nav.ranking=Рейтинг
nav.clubes=Клубы
nav.mi_cuenta=Мой аккаунт
nav.dashboard=Личный кабинет
nav.perfil=Профиль
nav.cerrar_sesion=Выйти
nav.iniciar_sesion=Войти
nav.registrarse=Регистрация

# === LOGIN ===
login.title=Войти
login.subtitle=Нет аккаунта?
login.subtitle.link=Зарегистрироваться бесплатно
login.email.label=Email
login.email.placeholder=ваш@email.com
login.password.label=Пароль
login.password.placeholder=Ваш пароль
login.remember=Запомнить меня
login.forgot=Забыли пароль?
login.submit=Войти
login.or=или войдите через
login.google=Google
login.safe=Ваши данные в безопасности
login.logout_success=Вы успешно вышли из системы
login.registered_success=Регистрация прошла успешно! Войдите в аккаунт.
login.confirmed_success=Email подтверждён! Теперь можно войти.
login.error.rate_limit.title=Слишком много попыток
login.error.rate_limit.text=Превышен лимит попыток входа. Подождите <strong>1 минуту</strong> и попробуйте снова.
login.error.not_confirmed.title=Email не подтверждён
login.error.not_confirmed.text=Откройте почту и перейдите по ссылке подтверждения.
login.error.not_confirmed.spam=Если письма нет, проверьте папку <strong>Спам</strong>.
login.error.bad_credentials=Неверный email или пароль

# === REGISTRO ===
registro.title=Создать аккаунт
registro.subtitle=Уже есть аккаунт?
registro.subtitle.link=Войти
registro.google=Google
registro.google.safe=Ваши данные защищены через Google
registro.divider.email=или создайте аккаунт с email
registro.nombre.label=Имя
registro.nombre.placeholder=Ваше имя
registro.apellido.label=Фамилия
registro.apellido.placeholder=Ваша фамилия
registro.email.label=Email
registro.email.placeholder=ваш@email.com
registro.telefono.label=Телефон
registro.telefono.placeholder=+54 9 11 0000 0000
registro.nivel.label=Уровень игры
registro.nivel.placeholder=Выберите уровень
registro.password.label=Пароль
registro.password.placeholder=Минимум 8 символов
registro.password.hint=Минимум 8 символов: 1 заглавная, 1 строчная, 1 цифра, 1 спецсимвол
registro.confirm_password.label=Подтвердите пароль
registro.confirm_password.placeholder=Повторите пароль
registro.terms=Принимаю <a href="/terminos" target="_blank">Условия использования</a> и <a href="/privacidad" target="_blank">Политику конфиденциальности</a>
registro.submit=Зарегистрироваться
registro.error.rate_limit.title=Слишком много запросов
registro.error.rate_limit.text=Превышен лимит попыток. Подождите <strong>1 минуту</strong> и попробуйте снова.

# (остальные секции по аналогии...)
```

---

## Шаблон messages-ru.js (JS переводы)

```javascript
window.APP_MESSAGES = {
    'common.loading':           'Загрузка...',
    'common.error.generic':     'Произошла ошибка. Попробуйте снова.',
    'common.error.network':     'Ошибка соединения. Проверьте интернет.',
    'common.copied':            'Скопировано в буфер обмена',

    'auth.password.mismatch':       'Пароли не совпадают',
    'auth.password.weak':           'Пароль должен содержать: 1 заглавную, 1 строчную, 1 цифру и 1 спецсимвол',
    'auth.terms.required':          'Необходимо принять условия использования',
    'auth.captcha.required':        'Пройдите проверку безопасности',
    'auth.register.loading':        'Регистрация...',
    'auth.register.success':        'Регистрация прошла успешно! Проверьте email для подтверждения.',
    'auth.register.error.email':    'Этот email уже зарегистрирован',
    'auth.register.error.generic':  'Ошибка регистрации. Попробуйте снова.',

    'password_reset.email.sent':        'Если email существует, вы получите инструкции',
    'password_reset.success':           'Пароль обновлён! Теперь можно войти.',
    'password_reset.error.expired':     'Ссылка устарела. Запросите новую.',
    'password_reset.password.mismatch': 'Пароли не совпадают',
    'password_reset.loading':           'Обновление...',

    'dashboard.register.confirm':       'Записаться на турнир "{0}"?',
    'dashboard.register.success':       'Вы записаны на турнир {0}',
    'dashboard.register.error.contact': 'Укажите телефон или Telegram для записи на турниры',
    'dashboard.register.error.full':    'Турнир заполнен',
    'dashboard.cancel.confirm':         'Отменить запись на "{0}"?',
    'dashboard.cancel.success':         'Запись отменена',

    'profile.save.success':     'Профиль сохранён',
    'profile.save.error':       'Ошибка при сохранении',
    'profile.phone.invalid':    'Введите корректный номер телефона с форматом +54...',
    'profile.telegram.invalid': 'Имя пользователя Telegram должно начинаться с @',

    'tournament.register.success':       'Вы успешно записались',
    'tournament.register.waitlist':      'Добавлены в список ожидания',
    'tournament.register.error.login':   'Войдите в аккаунт для записи',
    'tournament.cancel.confirm':         'Отменить запись?',
    'tournament.cancel.success':         'Запись отменена',

    'torneos.loading':          'Загрузка турниров...',
    'torneos.empty':            'Турниры с выбранными фильтрами не найдены',

    'home.loading':             'Загрузка...',
    'home.empty':               'Нет предстоящих турниров',

    'koc.connecting':           'Подключение...',
    'koc.disconnected':         'Соединение потеряно. Переподключение...',
    'koc.reconnected':          'Соединение восстановлено',
    'koc.waiting':              'Ожидание начала турнира...',
    'koc.finished':             'Турнир завершён',

    'ranking.loading':          'Загрузка рейтинга...',
    'ranking.empty':            'Данные рейтинга недоступны',

    'cookie.saved':             'Настройки cookie сохранены',
    'share.copied':             'Ссылка скопирована',
    'share.error':              'Не удалось скопировать ссылку',
};
```

---

## Чеклист этапа 4

### messages_ru.properties
- [ ] `common.*` — все общие ключи переведены
- [ ] `nav.*` — навигация
- [ ] `login.*` — страница логина (включая HTML-теги в значениях)
- [ ] `registro.*` — регистрация
- [ ] `home.*` — главная
- [ ] `torneos.*` — список турниров
- [ ] `tournament.*` — детали турнира
- [ ] `dashboard.*` — дашборд
- [ ] `perfil.*` — профиль
- [ ] `ranking.*` — рейтинг
- [ ] `koc.*` — king of court
- [ ] `americano.*`, `team_americano.*`, `playoff.*`
- [ ] `clubs.*`
- [ ] `waitlist.*`, `pair.*`
- [ ] `error.*`
- [ ] `cookie.*`

### messages_en.properties
- [ ] Все те же секции на английском

### messages-ru.js
- [ ] Все ключи из messages-es.js переведены на русский

### messages-en.js
- [ ] Все ключи из messages-es.js переведены на английский

### Проверка
- [ ] Нет ключей с пустым значением в ru/en (или явно обозначены как TODO)
- [ ] HTML-теги в значениях сохранены (`<strong>`, `<a>`)
- [ ] Строки с `{0}` не потеряли placeholder при переводе
