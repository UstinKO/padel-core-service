import type { User, Registration } from '../domain/types';
export interface Scenario {
    id: string;
    group: string;
    title: string;
    path: string;
    fixture?: string;
    description?: string;
    navigationState?: unknown;
}
export const demoUser: User = { id: 'demo-player', first: 'Даниил', last: 'Медведев', email: 'daniel@example.com', phone: '+54 9 11 5555 0100', telegram: '@daniel_demo', level: 'C7', preferredLocale: 'ru', emailVerified: true, preview: true };
const reg = (eventId: string, status: Registration['status'], extra: Partial<Registration> = {}): Registration => ({ eventId, status, createdAt: '2026-10-26T12:00:00Z', ...extra });
export function scenarioState(name: string): {
    user: User | null;
    registrations: Registration[];
} {
    if (name === 'guest')
        return { user: null, registrations: [] };
    const user = { ...demoUser };
    let registrations: Registration[] = [];
    if (name === 'google') {
        delete user.phone;
        user.authProvider = 'google';
    }
    if (name === 'account')
        registrations = [reg('king', 'confirmed', { paymentStatus: 'paid' }), reg('americano', 'waiting', { position: 3 }), reg('pairs', 'solo-later', { partnerMode: 'add-later', partnerDeadline: '29 октября · 19:00' }), reg('training', 'completed', { paymentStatus: 'paid' })];
    if (name === 'confirmed')
        registrations = [reg('king', 'confirmed', { paymentStatus: 'unpaid' })];
    if (name === 'waiting')
        registrations = [reg('americano', 'waiting', { position: 3 })];
    if (name === 'offer')
        registrations = [reg('americano', 'confirmed', { participationStatus: 'pending', promotedFromReserve: true, paymentStatus: 'unpaid' })];
    if (name === 'history')
        registrations = [reg('king', 'completed', { paymentStatus: 'paid' }), reg('training', 'cancelled', { paymentStatus: 'refunded' })];
    if (name === 'pair-search')
        registrations = [reg('pairs', 'solo-search', { partnerMode: 'search', allowContact: false })];
    if (name === 'pair-later')
        registrations = [reg('pairs', 'solo-later', { partnerMode: 'add-later', partnerDeadline: '29 октября · 19:00' })];
    if (name === 'pair-pending')
        registrations = [reg('pairs', 'solo-search', { partnerMode: 'search', proposalState: 'sent', proposalTo: 'partner-alex' })];
    if (name === 'pair-reserve-search')
        registrations = [reg('pairs', 'waiting', { partnerMode: 'search', allowContact: false })];
    if (name === 'pair-reserve-later')
        registrations = [reg('pairs', 'waiting', { partnerMode: 'add-later' })];
    if (name === 'pair-confirmed')
        registrations = [reg('pairs', 'confirmed', { partnerMode: 'with-partner', partner: 'Алексей Петров', partnerId: 'partner-alex', paymentStatus: 'pending' })];
    return { user, registrations };
}
const s = (id: string, group: string, title: string, path: string, fixture = 'guest', description?: string): Scenario => ({ id, group, title, path, fixture, description });
export const scenarios: Scenario[] = [
    s('home', 'Начало', 'Главная · новый посетитель', '/home'), s('home-player', 'Начало', 'Главная · игрок', '/home', 'account'), s('catalogue', 'Начало', 'Все мероприятия', '/events'),
    ...['empty', 'loading', 'error'].map(state => s(`catalogue-${state}`, 'Начало', `Каталог · ${({ empty: 'событий пока нет', loading: 'загрузка', error: 'ошибка с повтором' })[state]}`, '/events?state=' + state)), s('filters-empty', 'Начало', 'Фильтры · ничего не найдено', '/events?gender=Мужской&level=D5'),
    ...[['king', 'Король корта'], ['americano', 'Американо · индивидуальный, резерв'], ['pairs', 'Американо · постоянные пары'], ['training', 'Тренировка + игра'], ['open', 'Cancha abierta']].map(([id, title]) => s('event-' + id, 'Мероприятия', title, '/event/' + id)),
    ...[['full', 'Мест нет'], ['closed', 'Запись закрыта'], ['live', 'Турнир идёт'], ['finished', 'Турнир завершён'], ['cancelled', 'Мероприятие отменено']].map(([state, title]) => s('event-' + state, 'Мероприятия', title, '/event/king?state=' + state)),
    s('login-error', 'Аккаунт', 'Ошибка входа · неверные данные', '/login?state=invalid-credentials'), s('register-error', 'Аккаунт', 'Регистрация · email уже используется', '/register?state=email-exists'), s('register-phone-error', 'Аккаунт', 'Регистрация · телефон уже используется', '/register?state=phone-exists'), s('register-name-error', 'Аккаунт', 'Регистрация · проверка имени', '/register?state=name-rejected'), s('register-captcha', 'Аккаунт', 'Регистрация · проверка безопасности', '/register?state=captcha'), s('register-rate-limit', 'Аккаунт', 'Регистрация · пауза с таймером', '/register?state=rate-limit'), s('login', 'Аккаунт', 'Вход · Google и email', '/login'), s('register', 'Аккаунт', 'Регистрация с выбранным турниром', '/register?event=king'), s('google', 'Аккаунт', 'После Google · обязательные контакты', '/complete-profile?event=king', 'google'),
    s('forgot', 'Аккаунт', 'Восстановление пароля', '/forgot-password'),
    { ...s('reset', 'Аккаунт', 'Новый пароль', '/reset-password'), navigationState: { recoveryEmail: 'daniel@example.com' } }, s('reset-expired', 'Аккаунт', 'Ссылка восстановления истекла', '/reset-password'), s('oauth-error', 'Аккаунт', 'Google · вход не завершён', '/oauth-error?event=king'),
    s('book', 'Запись', 'Подтверждение участия · игрок', '/book/king', 'player'), s('success', 'Запись', 'Окно · вы записаны', '/book/king?show=confirmed', 'confirmed'), s('reserve', 'Запись', 'Записаться в резерв', '/book/americano', 'player'), s('reserve-success', 'Запись', 'Окно · вы в резерве', '/book/americano?show=waiting', 'waiting'),
    ...[['conflict', 'Последнее место заняли'], ['offline', 'Ошибка отправки'], ['unknown', 'Неизвестен результат запроса']].map(([v, t]) => s('book-' + v, 'Запись', t, '/book/king?simulate=' + v, 'player', 'Чтобы увидеть ответ, отметьте согласие с условиями и нажмите «Подтвердить запись».')),
    s('pair-book', 'Пары', 'Три способа регистрации', '/book/pairs', 'player'), s('pair-deadline', 'Пары', 'Срок добавления партнёра прошёл', '/book/pairs?state=partner-deadline', 'player'), s('pair-full', 'Пары', 'Полный парный турнир', '/book/pairs?state=full', 'player'), s('pair-search', 'Пары', 'Ищу партнёра', '/partners/pairs', 'pair-search'), s('pair-later', 'Пары', 'Добавить партнёра позже', '/partners/pairs?mode=add', 'pair-later'), s('pair-pending', 'Пары', 'Предложение отправлено', '/partners/pairs', 'pair-pending'),
    s('pair-reserve-search', 'Пары', 'Резерв · поиск партнёра', '/partners/pairs', 'pair-reserve-search'), s('pair-reserve-later', 'Пары', 'Резерв · добавить партнёра', '/partners/pairs?mode=add', 'pair-reserve-later'), s('pair-reserve-invitation', 'Пары', 'Резерв · принять предложение пары', '/partner-invitation', 'pair-reserve-search'),
    ...[['pending', 'Входящее предложение'], ['accepted', 'Пара подтверждена'], ['declined', 'Предложение отклонено'], ['expired', 'Приглашение истекло'], ['incompatible', 'Категория не подходит']].map(([state, t]) => s('invite-' + state, 'Пары', t, '/partner-invitation?state=' + state, state === 'accepted' ? 'pair-confirmed' : 'pair-search')),
    s('cancel-error', 'Кабинет', 'Отмена · ошибка с повтором', '/games?dialog=cancel&event=king&state=cancel-error', 'confirmed'), s('payment', 'Кабинет', 'Окно оплаты · демостатусы', '/games?dialog=payment&event=king', 'confirmed'), s('profile-edit', 'Кабинет', 'Редактирование профиля', '/profile?dialog=edit', 'account'), s('profile-error', 'Кабинет', 'Профиль · ошибка сохранения', '/profile?dialog=edit&state=save-error', 'account'), s('games', 'Кабинет', 'Мои записи · все статусы', '/games', 'account'), s('games-empty', 'Кабинет', 'Мои записи · пусто', '/games', 'player'), s('games-history', 'Кабинет', 'История · завершено и отменено', '/games', 'history'), s('offer', 'Кабинет', 'Переведены в основу · подтвердить участие', '/waitlist-offer', 'offer'), s('offer-unavailable', 'Кабинет', 'Уведомление · статус записи изменился', '/waitlist-offer?event=americano', 'waiting'), s('profile', 'Кабинет', 'Профиль и редактирование', '/profile', 'account'), s('pair-account', 'Кабинет', 'Готовая пара в кабинете', '/games', 'pair-confirmed'),
    ...(['king', 'americano', 'pairs'] as const).flatMap(format => [s('live-' + format, 'Проведение', ({ king: 'Король корта', americano: 'Американо · 19 раундов', pairs: 'Пары · квалификация и плей-офф' })[format], '/live/' + format, 'account'), s('rank-' + format, 'Проведение', `Рейтинг · ${{king: 'Король корта', americano: 'Американо', pairs: 'Постоянные пары'}[format]}`, '/live/' + format + '?tab=rank', 'player')]),
    ...(['king', 'americano', 'pairs'] as const).flatMap(format => {
        const title = {king: 'Король корта', americano: 'Американо', pairs: 'Постоянные пары'}[format];
        return [s('schedule-' + format, 'Проведение', 'Сетка · ' + title, '/live/' + format + '?tab=schedule', 'account'), s('history-' + format, 'Проведение', 'История матчей · ' + title, '/live/' + format + '?tab=history', 'account'), s('public-' + format, 'Проведение', 'Зритель · ' + title, '/live/' + format + '?view=public', 'guest')];
    }),
    s('event-confirmed', 'Мероприятия', 'Вы записаны · Король корта', '/event/king', 'confirmed'),
    s('event-waiting', 'Мероприятия', 'Вы в резерве · Американо', '/event/americano', 'waiting'),
    s('live-playoff', 'Проведение', 'Пары · этап плей-офф', '/live/pairs?state=playoff', 'account'),
    s('live-stale', 'Проведение', 'Последние данные · нет обновления', '/live/king?state=stale', 'account'),
    ...[['not-started', 'Сетка ещё не создана'], ['loading', 'Загрузка'], ['error', 'Ошибка с повтором'], ['finished', 'Итоги турнира']].map(([state, t]) => s('live-' + state, 'Проведение', t, '/live/king?state=' + state, 'player')),
    s('clubs', 'Другие страницы', 'Клубы', '/clubs'), s('club', 'Другие страницы', 'Страница клуба', '/clubs/black-padel'), s('league', 'Другие страницы', 'Лига · в разработке', '/league'), s('ranking', 'Другие страницы', 'Общий рейтинг · в разработке', '/ranking'), s('app', 'Другие страницы', 'О приложении', '/app-info'), s('cookies', 'Другие страницы', 'Политика cookie', '/legal/cookies'), s('terms', 'Другие страницы', 'Условия участия', '/legal/terms'), s('privacy', 'Другие страницы', 'Политика конфиденциальности', '/legal/privacy'),
    ...[['session', 'Сессия истекла'], ['forbidden', 'Нет доступа'], ['offline', 'Нет соединения'], ['rate-limit', 'Слишком много запросов'], ['unavailable', 'Сервис недоступен']].map(([state, title]) => s('system-' + state, 'Системные', title, '/status/' + state)), s('404', 'Системные', 'Страница не найдена', '/not-found')
];
