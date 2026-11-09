# Реестр примеров 0.7

Сгенерирован из src/data/scenarios.ts командой pnpm handoff. Всего: 99.

## Начало

- [Главная · новый посетитель](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/home) — `/home`; демосессия: guest.
- [Главная · игрок](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/home-player) — `/home`; демосессия: account.
- [Все мероприятия](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/catalogue) — `/events`; демосессия: guest.
- [Каталог · событий пока нет](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/catalogue-empty) — `/events?state=empty`; демосессия: guest.
- [Каталог · загрузка](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/catalogue-loading) — `/events?state=loading`; демосессия: guest.
- [Каталог · ошибка с повтором](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/catalogue-error) — `/events?state=error`; демосессия: guest.
- [Фильтры · ничего не найдено](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/filters-empty) — `/events?gender=Мужской&level=D5`; демосессия: guest.

## Мероприятия

- [Король корта](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/event-king) — `/event/king`; демосессия: guest.
- [Американо · индивидуальный, резерв](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/event-americano) — `/event/americano`; демосессия: guest.
- [Американо · постоянные пары](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/event-pairs) — `/event/pairs`; демосессия: guest.
- [Тренировка + игра](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/event-training) — `/event/training`; демосессия: guest.
- [Cancha abierta](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/event-open) — `/event/open`; демосессия: guest.
- [Мест нет](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/event-full) — `/event/king?state=full`; демосессия: guest.
- [Запись закрыта](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/event-closed) — `/event/king?state=closed`; демосессия: guest.
- [Турнир идёт](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/event-live) — `/event/king?state=live`; демосессия: guest.
- [Турнир завершён](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/event-finished) — `/event/king?state=finished`; демосессия: guest.
- [Мероприятие отменено](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/event-cancelled) — `/event/king?state=cancelled`; демосессия: guest.
- [Вы записаны · Король корта](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/event-confirmed) — `/event/king`; демосессия: confirmed.
- [Вы в резерве · Американо](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/event-waiting) — `/event/americano`; демосессия: waiting.

## Аккаунт

- [Ошибка входа · неверные данные](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/login-error) — `/login?state=invalid-credentials`; демосессия: guest.
- [Регистрация · email уже используется](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/register-error) — `/register?state=email-exists`; демосессия: guest.
- [Регистрация · телефон уже используется](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/register-phone-error) — `/register?state=phone-exists`; демосессия: guest.
- [Регистрация · проверка имени](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/register-name-error) — `/register?state=name-rejected`; демосессия: guest.
- [Регистрация · проверка безопасности](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/register-captcha) — `/register?state=captcha`; демосессия: guest.
- [Регистрация · пауза с таймером](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/register-rate-limit) — `/register?state=rate-limit`; демосессия: guest.
- [Вход · Google и email](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/login) — `/login`; демосессия: guest.
- [Регистрация с выбранным турниром](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/register) — `/register?event=king`; демосессия: guest.
- [После Google · обязательные контакты](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/google) — `/complete-profile?event=king`; демосессия: google.
- [Восстановление пароля](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/forgot) — `/forgot-password`; демосессия: guest.
- [Новый пароль](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/reset) — `/reset-password`; демосессия: guest.
- [Ссылка восстановления истекла](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/reset-expired) — `/reset-password`; демосессия: guest.
- [Google · вход не завершён](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/oauth-error) — `/oauth-error?event=king`; демосессия: guest.

## Запись

- [Подтверждение участия · игрок](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/book) — `/book/king`; демосессия: player.
- [Окно · вы записаны](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/success) — `/book/king?show=confirmed`; демосессия: confirmed.
- [Записаться в резерв](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/reserve) — `/book/americano`; демосессия: player.
- [Окно · вы в резерве](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/reserve-success) — `/book/americano?show=waiting`; демосессия: waiting.
- [Последнее место заняли](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/book-conflict) — `/book/king?simulate=conflict`; демосессия: player. Чтобы увидеть ответ, отметьте согласие с условиями и нажмите «Подтвердить запись».
- [Ошибка отправки](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/book-offline) — `/book/king?simulate=offline`; демосессия: player. Чтобы увидеть ответ, отметьте согласие с условиями и нажмите «Подтвердить запись».
- [Неизвестен результат запроса](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/book-unknown) — `/book/king?simulate=unknown`; демосессия: player. Чтобы увидеть ответ, отметьте согласие с условиями и нажмите «Подтвердить запись».

## Пары

- [Три способа регистрации](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/pair-book) — `/book/pairs`; демосессия: player.
- [Срок добавления партнёра прошёл](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/pair-deadline) — `/book/pairs?state=partner-deadline`; демосессия: player.
- [Полный парный турнир](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/pair-full) — `/book/pairs?state=full`; демосессия: player.
- [Ищу партнёра](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/pair-search) — `/partners/pairs`; демосессия: pair-search.
- [Добавить партнёра позже](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/pair-later) — `/partners/pairs?mode=add`; демосессия: pair-later.
- [Предложение отправлено](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/pair-pending) — `/partners/pairs`; демосессия: pair-pending.
- [Резерв · поиск партнёра](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/pair-reserve-search) — `/partners/pairs`; демосессия: pair-reserve-search.
- [Резерв · добавить партнёра](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/pair-reserve-later) — `/partners/pairs?mode=add`; демосессия: pair-reserve-later.
- [Резерв · принять предложение пары](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/pair-reserve-invitation) — `/partner-invitation`; демосессия: pair-reserve-search.
- [Входящее предложение](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/invite-pending) — `/partner-invitation?state=pending`; демосессия: pair-search.
- [Пара подтверждена](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/invite-accepted) — `/partner-invitation?state=accepted`; демосессия: pair-confirmed.
- [Предложение отклонено](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/invite-declined) — `/partner-invitation?state=declined`; демосессия: pair-search.
- [Приглашение истекло](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/invite-expired) — `/partner-invitation?state=expired`; демосессия: pair-search.
- [Категория не подходит](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/invite-incompatible) — `/partner-invitation?state=incompatible`; демосессия: pair-search.

## Кабинет

- [Отмена · ошибка с повтором](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/cancel-error) — `/games?dialog=cancel&event=king&state=cancel-error`; демосессия: confirmed.
- [Окно оплаты · демостатусы](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/payment) — `/games?dialog=payment&event=king`; демосессия: confirmed.
- [Редактирование профиля](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/profile-edit) — `/profile?dialog=edit`; демосессия: account.
- [Профиль · ошибка сохранения](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/profile-error) — `/profile?dialog=edit&state=save-error`; демосессия: account.
- [Мои записи · все статусы](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/games) — `/games`; демосессия: account.
- [Мои записи · пусто](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/games-empty) — `/games`; демосессия: player.
- [История · завершено и отменено](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/games-history) — `/games`; демосессия: history.
- [Переведены в основу · подтвердить участие](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/offer) — `/waitlist-offer`; демосессия: offer.
- [Уведомление · статус записи изменился](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/offer-unavailable) — `/waitlist-offer?event=americano`; демосессия: waiting.
- [Профиль и редактирование](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/profile) — `/profile`; демосессия: account.
- [Готовая пара в кабинете](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/pair-account) — `/games`; демосессия: pair-confirmed.

## Проведение

- [Король корта](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/live-king) — `/live/king`; демосессия: account.
- [Рейтинг · Король корта](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/rank-king) — `/live/king?tab=rank`; демосессия: player.
- [Американо · 19 раундов](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/live-americano) — `/live/americano`; демосессия: account.
- [Рейтинг · Американо](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/rank-americano) — `/live/americano?tab=rank`; демосессия: player.
- [Пары · квалификация и плей-офф](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/live-pairs) — `/live/pairs`; демосессия: account.
- [Рейтинг · Постоянные пары](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/rank-pairs) — `/live/pairs?tab=rank`; демосессия: player.
- [Сетка · Король корта](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/schedule-king) — `/live/king?tab=schedule`; демосессия: account.
- [История матчей · Король корта](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/history-king) — `/live/king?tab=history`; демосессия: account.
- [Зритель · Король корта](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/public-king) — `/live/king?view=public`; демосессия: guest.
- [Сетка · Американо](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/schedule-americano) — `/live/americano?tab=schedule`; демосессия: account.
- [История матчей · Американо](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/history-americano) — `/live/americano?tab=history`; демосессия: account.
- [Зритель · Американо](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/public-americano) — `/live/americano?view=public`; демосессия: guest.
- [Сетка · Постоянные пары](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/schedule-pairs) — `/live/pairs?tab=schedule`; демосессия: account.
- [История матчей · Постоянные пары](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/history-pairs) — `/live/pairs?tab=history`; демосессия: account.
- [Зритель · Постоянные пары](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/public-pairs) — `/live/pairs?view=public`; демосессия: guest.
- [Пары · этап плей-офф](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/live-playoff) — `/live/pairs?state=playoff`; демосессия: account.
- [Последние данные · нет обновления](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/live-stale) — `/live/king?state=stale`; демосессия: account.
- [Сетка ещё не создана](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/live-not-started) — `/live/king?state=not-started`; демосессия: player.
- [Загрузка](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/live-loading) — `/live/king?state=loading`; демосессия: player.
- [Ошибка с повтором](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/live-error) — `/live/king?state=error`; демосессия: player.
- [Итоги турнира](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/live-finished) — `/live/king?state=finished`; демосессия: player.

## Другие страницы

- [Клубы](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/clubs) — `/clubs`; демосессия: guest.
- [Страница клуба](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/club) — `/clubs/black-padel`; демосессия: guest.
- [Лига · в разработке](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/league) — `/league`; демосессия: guest.
- [Общий рейтинг · в разработке](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/ranking) — `/ranking`; демосессия: guest.
- [О приложении](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/app) — `/app-info`; демосессия: guest.
- [Политика cookie](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/cookies) — `/legal/cookies`; демосессия: guest.
- [Условия участия](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/terms) — `/legal/terms`; демосессия: guest.
- [Политика конфиденциальности](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/privacy) — `/legal/privacy`; демосессия: guest.

## Системные

- [Сессия истекла](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/system-session) — `/status/session`; демосессия: guest.
- [Нет доступа](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/system-forbidden) — `/status/forbidden`; демосессия: guest.
- [Нет соединения](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/system-offline) — `/status/offline`; демосессия: guest.
- [Слишком много запросов](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/system-rate-limit) — `/status/rate-limit`; демосессия: guest.
- [Сервис недоступен](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/system-unavailable) — `/status/unavailable`; демосессия: guest.
- [Страница не найдена](https://one-padel-play-concept.evgeny-1800.chatgpt.site/#/preview/404) — `/not-found`; демосессия: guest.
