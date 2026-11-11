# LFPT-0908: Feature frontend UI v2 — вход, регистрация, восстановление и профиль

## Статус
done

## Источник
[Issue #908](https://github.com/UstinKO/padel-core-service/issues/908), напрямую из issue. Зависит от backend #899 и влитого фундамента #827.

## Контекст / зачем
Заменить demo auth/profile из UI 0.7 реальными React-формами на same-origin Spring session + CSRF. Существующие production auth-страницы продолжают работать.

## Связь с MASTER.md
Контакт — телефон +54 ИЛИ Telegram, email подтверждается автоматически как сейчас. Клиент не вводит новых условий входа или участия; токены JWT/refresh и auth state не попадают в localStorage. Google без контакта может войти и завершить профиль по действующим серверным требованиям.

## Требования
- Добавить закрытые React-маршруты входа, регистрации, запроса/проверки/reset пароля и профиля под `/preview/**`; точные suffix соответствуют существующим доменным URL, без demo hash routes.
- Login через прежний POST `/login` с form-urlencoded, JSON Accept и CSRF; logout через CSRF-защищённый POST. Профиль и регистрация используют типизированные API #899.
- Cookie `lang` — общий ручной выбор языка с сервером; переключение синхронизируется через `?lang=` и не теряет безопасные поля формы. preferredLocale писем меняется только как настройка профиля.
- Отображать все fieldErrors рядом с полями и в доступном резюме; фокусировать ошибку/резюме. Безопасные поля сохранять в памяти при ошибке и смене языка. Пароль и CAPTCHA не персистить и не возвращать из API.
- CAPTCHA использует только публичную конфигурацию сервера; duplicate/domain/name/captcha/rate-limit/expired/session состояния имеют ES/RU/EN.
- Повторный submit блокировать; не повторять мутации автоматически после timeout. При неопределённом login/profile исходе перечитать session/profile, а не дублировать POST.
- Сохранять только проверенный сервером event/destination. Google — действительная навигация на серверную OAuth-точку; completion не имитирует вход и не создаёт demo users.
- Если безопасный отдельный Google callback для тестового контура отсутствует, Google в нём отключён; внешняя проверка явно не считается выполненной.
- Парольные поля совместимы с password manager/autocomplete; mobile keyboard, клавиатура, screen-reader labels, focus, reduced motion, 320/390/1440 px.

## Изменения в системе
### API
Контракты #899: сессия, JSON-login/logout, регистрация, config, self-profile/password и reset. Общий API client сохраняет CSRF и Accept-Language, не выводит техническое тело ошибки пользователю.
### БД
Не меняется.
### UI
Auth/profile страницы, типы API, session hooks/guards, форма/резюме ошибок, синхронизация локали. Используются токены/компоненты UI 0.7; тексты сразу ES/RU/EN.
### Документация
`frontend/README.md`, screen/API contracts, план React и `.agents/PROJECT.md`. Уточнить ADR-0009: действующая серверная cookie имеет приоритет, независимое новое localStorage-предпочтение не создаётся.

## Вне скоупа
Запись/пары/кабинет мероприятий, новые правила контакта и email, JWT для веба, перенос production auth URL, публичное раскрытие preview и настройка production OAuth provider.

## Критерии приёмки
- [ ] Login/logout/register/reset/profile проходят с реальным API в изолированной локальной среде; CSRF/session сохраняют поведение после refresh и между вкладками.
- [ ] Обычная ошибка формы не очищает безопасные поля и не создаёт блокировку IP; 401/422/429/expired token имеют понятные состояния.
- [ ] Event/destination сохраняются без open redirect, неизвестный исход мутации не повторяется автоматически.
- [ ] Контакт ИЛИ и preferredLocale писем соответствуют MASTER/серверу, UI locale и cookie lang согласованы.
- [ ] Нет demo auth, токенов/паролей/CAPTCHA в localStorage, логах или URL.
- [ ] ES/RU/EN полны, pnpm check и Playwright проходят на desktop/mobile; keyboard/focus/password manager проверены.
- [ ] Production routes не переключены, legacy и `/app/**` сохранены; PR содержит один собственный коммит и Depends-On на backend.

## Edge cases
Сессия истекла в другой вкладке, Owner вместо Player в self-profile, несколько ошибок полей, смена языка при несохранённой форме, CAPTCHA refresh, disabled Google, reused reset token, timeout после успешной операции, readonly email/служебные поля и недоступный backend.

## Открытые вопросы
Технический план подтверждён пользователем. Production `/preview/**` остаётся SUPER_ADMIN; guest/player auth проверяется только через доступную с loopback локальную тестовую сборку и отдельную БД, без ослабления production guard. Развёртываемый закрытый контур #904 остаётся отдельной задачей.

## Результаты проверки
`pnpm check`: форматирование, линтер, типизация, 20 unit-тестов, production build и размер проходят. Playwright: 26 тестов на desktop/mobile. Отдельная браузерная проверка с реальным backend, тестовой PostgreSQL и локальным SMTP: регистрация → сессионный вход → профиль/save/reload → выход во второй вкладке → reset по письму → вход с новым паролем. Ширины 1440/390/320 без горизонтального переполнения, скриншоты просмотрены. Google-провайдер во внешней сети не проверялся, в изолированном контуре отключён через публичную настройку. Production preview по-прежнему закрыт.
