# Этап 13: Переводы admin-ключей (ru + en)

## Цель

Заполнить секцию `admin.*` в `messages_ru.properties`, `messages_en.properties`, `messages-ru.js`, `messages-en.js`.  
К началу этапа `messages_es.properties` уже содержит все `admin.*` ключи (этап 11).

---

## Правила

- Имена собственные не переводим: `King of Court`, `Americano`, `Playoff`, `Flex`, `Full`
- HTML-теги в значениях (`<strong>`, `<a>`) сохраняем — не убирать
- Плейсхолдеры `{0}`, `{1}` не убирать при переводе
- Валюты (`ARS`, `USD`, `USDT`, `EUR`) не переводим
- Технические коды (`PENDING`, `IN_PROGRESS`, `COMPLETED`) оставляем как есть внутри JS

---

## messages_ru.properties — секция admin

```properties
# ─── Admin Common ─────────────────────────────────────────────────────────────
admin.common.btn.new_tournament=Новый турнир
admin.common.btn.new_club=Новый клуб
admin.common.btn.export=Экспорт Excel
admin.common.btn.view=Просмотр
admin.common.btn.manage=Управление
admin.common.btn.activate=Активировать
admin.common.btn.deactivate=Деактивировать
admin.common.btn.toggle=Изменить статус
admin.common.col.id=ID
admin.common.col.name=Название
admin.common.col.club=Клуб
admin.common.col.date=Дата
admin.common.col.status=Статус
admin.common.col.actions=Действия
admin.common.col.email=Email
admin.common.col.phone=Телефон
admin.common.empty=Нет данных
admin.common.form.field_error=Ошибка в поле
admin.common.form.select_placeholder=Выберите...

# ─── Admin Nav ────────────────────────────────────────────────────────────────
admin.nav.panel=Панель Admin
admin.nav.tournaments=Турниры
admin.nav.clubs=Клубы
admin.nav.players=Игроки
admin.nav.export=Экспорт
admin.nav.site=Перейти на сайт
admin.nav.logout=Выйти

# ─── Panel ────────────────────────────────────────────────────────────────────
admin.panel.title=Панель администратора
admin.panel.subtitle=Управление игроками и турнирами 1-Padel
admin.panel.stat.players=Всего игроков
admin.panel.stat.admins=Администраторы
admin.panel.stat.active_tournaments=Активных турниров
admin.panel.stat.waitlist=Список ожидания
admin.panel.koc.rounds=Раунды калибровки:
admin.panel.koc.courts=Корты:
admin.panel.players.empty=Нет зарегистрированных игроков
admin.panel.tournaments.empty=Нет созданных турниров

# ─── Tournaments List ─────────────────────────────────────────────────────────
admin.tournaments.list.title=Управление турнирами
admin.tournaments.list.subtitle=Управляйте всеми турнирами платформы
admin.tournaments.list.col.info=Инфо
admin.tournaments.list.col.registered=Участники
admin.tournaments.list.empty.title=Нет турниров
admin.tournaments.list.empty.text=Начните с создания первого турнира

# ─── Tournament Form ──────────────────────────────────────────────────────────
admin.tournaments.form.title.create=Создать турнир
admin.tournaments.form.title.edit=Редактировать турнир
admin.tournaments.form.subtitle=Заполните данные турнира ниже
admin.tournaments.form.field.club=Клуб
admin.tournaments.form.field.club.select=Выберите клуб
admin.tournaments.form.field.name=Название турнира
admin.tournaments.form.field.name.placeholder=Напр.: Открытый турнир Лето 2026
admin.tournaments.form.field.date_start=Дата начала
admin.tournaments.form.field.time_start=Время начала
admin.tournaments.form.field.duration=Примерная длительность
admin.tournaments.form.field.duration.placeholder=Напр.: 4 часа, 2 дня
admin.tournaments.form.field.gender=Формат по полу
admin.tournaments.form.field.gender.select=Выберите формат
admin.tournaments.form.field.level=Категория / Уровень
admin.tournaments.form.field.level.select=Выберите уровень
admin.tournaments.form.field.type=Тип турнира
admin.tournaments.form.field.type.select=Выберите тип
admin.tournaments.form.field.type.koc=King of Court
admin.tournaments.form.field.type.americano=Americano (Индивидуальный)
admin.tournaments.form.field.type.americano_teams=Americano Пары (Квалификация + Playoff)
admin.tournaments.form.field.type.cancha_abierta=Открытый корт
admin.tournaments.form.field.modality=Режим игры
admin.tournaments.form.field.modality.select=Выберите режим
admin.tournaments.form.field.capacity=Максимум участников
admin.tournaments.form.field.price=Цена
admin.tournaments.form.field.currency=Валюта
admin.tournaments.form.field.currency.ars=ARS (Аргентинское песо)
admin.tournaments.form.field.currency.usd=USD (Доллар)
admin.tournaments.form.field.currency.usdt=USDT (Tether)
admin.tournaments.form.field.currency.eur=EUR (Евро)
admin.tournaments.form.field.status=Статус турнира
admin.tournaments.form.field.deadline=Дедлайн отмены
admin.tournaments.form.field.description=Подробное описание
admin.tournaments.form.field.description.placeholder=Дополнительная информация о турнире...
admin.tournaments.form.field.contact=Контакт организатора
admin.tournaments.form.field.contact.placeholder=Напр.: Иван Иванов - 123456789
admin.tournaments.form.field.faq=URL часто задаваемых вопросов
admin.tournaments.form.field.faq.placeholder=https://ejemplo.com/faq

# ─── Tournament Details ───────────────────────────────────────────────────────
admin.tournaments.details.info.cancel_deadline=Отмена до
admin.tournaments.details.info.club=Клуб
admin.tournaments.details.info.duration=Длительность
admin.tournaments.details.info.date=Дата начала
admin.tournaments.details.stat.attended=Посетили:
admin.tournaments.details.stat.paid=Оплатили:
admin.tournaments.details.stat.total_paid=Всего оплачено:
admin.tournaments.details.col.pair=Пара
admin.tournaments.details.col.pair_contact=Контакт пары
admin.tournaments.details.empty_participants=Нет подтверждённых участников
admin.tournaments.details.teams.lbl.bye=Bye за раунд:
admin.tournaments.details.teams.lbl.courts_suggested=Предлагаемые корты:
admin.tournaments.details.teams.lbl.teams_loaded_manual=Загруженных команд (вручную):
admin.tournaments.details.teams.lbl.format_expected=Ожидаемый формат:
admin.tournaments.details.teams.lbl.confirmed=Подтверждённые участники:
admin.tournaments.details.teams.lbl.players_confirmed=Подтверждённые игроки:
admin.tournaments.details.teams.lbl.players_per_round=Игроков за раунд:
admin.tournaments.details.teams.lbl.pairs=Записанных пар:
admin.tournaments.details.teams.lbl.rounds_suggested=Предлагаемых раундов:
admin.tournaments.details.teams.lbl.teams_loaded=Загружено команд:
admin.tournaments.details.teams.lbl.min_required=Минимально требуется:
admin.tournaments.details.teams.min_teams=Необходимо <strong>минимум 4 команды</strong>.
admin.tournaments.details.teams.step1=Шаг 1 — Загрузить команды
admin.tournaments.details.teams.step2=Шаг 2 — Начать квалификацию
admin.tournaments.details.teams.calibration_rounds=Раунды калибровки
admin.tournaments.details.teams.courts_count=Количество кортов
admin.tournaments.details.teams.status.playoff=Идёт Playoff
admin.tournaments.details.teams.status.qualification=Идёт квалификация
admin.tournaments.details.teams.format.desc=<strong>Формат Americano Пары</strong>: каждая команда играет
admin.tournaments.details.teams.format.playoff_suffix=напр. 6:4). Лучшие команды выходят в <strong>плей-офф</strong>.
admin.tournaments.details.teams.format.playoff_plus=+ <strong>Плей-офф</strong>.
admin.tournaments.details.teams.format.full=<strong>Full</strong>: каждая пара играет вместе ровно 1 раз, без повторений.
admin.tournaments.details.teams.format.flex=<strong>Flex</strong>: гибкий формат, минимизирует повторения.
admin.tournaments.details.teams.format.qualification=<strong>Квалификация</strong> (2 матча на команду, счёт в геймах: 6:4)
admin.tournaments.details.teams.option.auto=Авто (Full если возможно, иначе Flex)
admin.tournaments.details.teams.option.flex=Flex Americano — максимальное разнообразие

# ─── KoC admin ────────────────────────────────────────────────────────────────
admin.koc.title=King of Court — Управление
admin.koc.court=Корт {0}
admin.koc.round=Раунд {0}
admin.koc.status.playing=В игре
admin.koc.status.completed=Завершён
admin.koc.btn.next_round=Следующий раунд
admin.koc.btn.rollback=Откатить раунд
admin.koc.btn.finish=Завершить турнир
admin.koc.btn.youtube=Сохранить YouTube
admin.koc.btn.save_result=Сохранить результат
admin.koc.empty=Нет активных раундов

# ─── Payments ─────────────────────────────────────────────────────────────────
admin.payments.title=Управление платежами
admin.payments.subtitle=Управляйте платежами турнира
admin.payments.col.player=Игрок
admin.payments.col.amount=Сумма
admin.payments.col.status=Статус
admin.payments.col.date=Дата оплаты
admin.payments.col.method=Способ
admin.payments.btn.confirm=Подтвердить оплату
admin.payments.btn.reject=Отклонить
admin.payments.stat.total=Всего собрано
admin.payments.stat.pending=Ожидает
admin.payments.stat.confirmed=Подтверждено
admin.payments.empty=Нет зарегистрированных платежей

# ─── Americano admin ──────────────────────────────────────────────────────────
admin.americano.title=Americano — Управление
admin.americano.double.title=Team Americano — Управление
admin.americano.btn.initialize=Инициализировать турнир
admin.americano.btn.start_round=Начать раунд
admin.americano.btn.complete_round=Завершить раунд
admin.americano.btn.save_result=Сохранить результат
admin.americano.btn.finish=Завершить турнир
admin.americano.btn.preview=Предпросмотр раундов
admin.americano.btn.dropout=Выбыл из турнира
admin.americano.section.rounds=Раунды
admin.americano.section.matches=Матчи
admin.americano.section.ranking=Рейтинг
admin.americano.status.pending=Ожидает
admin.americano.status.in_progress=Идёт
admin.americano.status.completed=Завершён
admin.americano.status.cancelled=Отменён
admin.americano.empty.rounds=Нет доступных раундов
admin.americano.empty.matches=Нет матчей в этом раунде
admin.americano.empty.ranking=Рейтинг недоступен
admin.americano.col.round=Раунд
admin.americano.col.court=Корт
admin.americano.col.team1=Команда 1
admin.americano.col.team2=Команда 2
admin.americano.col.result=Результат
admin.americano.col.points=Очки
admin.americano.col.played=И
admin.americano.col.won=В
admin.americano.col.lost=П

# ─── Playoff admin ────────────────────────────────────────────────────────────
admin.playoff.title=Playoff — Управление
admin.playoff.btn.generate=Сгенерировать сетку
admin.playoff.btn.start=Начать playoff
admin.playoff.btn.finish=Завершить
admin.playoff.bracket.title=Сетка плей-офф
admin.playoff.bracket.round=Раунд {0}
admin.playoff.bracket.winner=Победитель
admin.playoff.bracket.tbd=Не определён
admin.playoff.qualification.title=Таблица квалификации
admin.playoff.qualification.col.team=Команда
admin.playoff.qualification.col.points=Очки
admin.playoff.qualification.col.qualify=Проходит
admin.playoff.status.pending=Ожидает
admin.playoff.status.in_progress=Идёт
admin.playoff.status.finished=Завершён
admin.playoff.empty=Нет данных playoff

# ─── Preview rounds ───────────────────────────────────────────────────────────
admin.preview.title=Предпросмотр раундов
admin.preview.double.title=Предпросмотр раундов (пары)
admin.preview.col.round=Раунд
admin.preview.col.court=Корт
admin.preview.col.team_a=Команда A
admin.preview.col.team_b=Команда B
admin.preview.col.bye=Bye
admin.preview.btn.confirm=Подтвердить и инициализировать
admin.preview.btn.back=Назад
admin.preview.stat.rounds=Всего раундов:
admin.preview.stat.courts=Кортов:
admin.preview.stat.players=Игроков:
admin.preview.empty=Нет раундов для отображения

# ─── Clubs List ───────────────────────────────────────────────────────────────
admin.clubs.list.title=Управление клубами
admin.clubs.list.subtitle=Администрирование клубов 1-Padel
admin.clubs.list.col.city=Город/Зона
admin.clubs.list.col.phone=Телефон
admin.clubs.list.empty.title=Нет клубов
admin.clubs.list.empty.text=Начните с создания первого клуба

# ─── Club Form ────────────────────────────────────────────────────────────────
admin.clubs.form.title.create=Создать клуб
admin.clubs.form.title.edit=Редактировать клуб
admin.clubs.form.field.name=Название клуба
admin.clubs.form.field.city=Город
admin.clubs.form.field.zone=Зона
admin.clubs.form.field.address=Адрес
admin.clubs.form.field.phone=Телефон
admin.clubs.form.field.email=Email
admin.clubs.form.field.courts=Количество кортов
admin.clubs.form.field.description=Описание
admin.clubs.form.field.website=Сайт
admin.clubs.form.field.active=Активен

# ─── Club Details ─────────────────────────────────────────────────────────────
admin.clubs.details.title=Детали клуба
admin.clubs.details.field.city=Город
admin.clubs.details.field.zone=Зона
admin.clubs.details.field.address=Адрес
admin.clubs.details.field.phone=Телефон
admin.clubs.details.field.email=Email
admin.clubs.details.field.courts=Корты
admin.clubs.details.field.website=Сайт
admin.clubs.details.tournaments.title=Турниры клуба
admin.clubs.details.tournaments.empty=Нет турниров в этом клубе

# ─── Players List ─────────────────────────────────────────────────────────────
admin.players.list.title=Управление игроками
admin.players.list.subtitle=Управляйте всеми игроками платформы
admin.players.list.col.registered=Регистрация
admin.players.list.col.email_confirmed=Email подтверждён
admin.players.list.stat.total=Всего игроков
admin.players.list.stat.email_confirmed=Email подтверждён
admin.players.list.empty.title=Нет игроков
admin.players.list.empty.text=Игроки появятся здесь после регистрации

# ─── Player Details ───────────────────────────────────────────────────────────
admin.players.details.title=Детали игрока
admin.players.details.field.email=Email
admin.players.details.field.phone=Телефон
admin.players.details.field.telegram=Telegram
admin.players.details.field.level=Уровень
admin.players.details.field.registered=Зарегистрирован
admin.players.details.field.email_confirmed=Email подтверждён
admin.players.details.field.status=Статус
admin.players.details.field.google=Google OAuth
admin.players.details.tournaments.title=Турниры игрока
admin.players.details.tournaments.col.tournament=Турнир
admin.players.details.tournaments.col.status=Статус записи
admin.players.details.tournaments.col.date=Дата
admin.players.details.tournaments.empty=Нет зарегистрированных турниров
admin.players.details.ranking.title=Позиция в рейтинге
admin.players.details.ranking.col.points=Очки
admin.players.details.ranking.col.position=Позиция
admin.players.details.ranking.col.wins=Победы
```

---

## messages_en.properties — секция admin

```properties
# ─── Admin Common ─────────────────────────────────────────────────────────────
admin.common.btn.new_tournament=New tournament
admin.common.btn.new_club=New club
admin.common.btn.export=Export Excel
admin.common.btn.view=View
admin.common.btn.manage=Manage
admin.common.btn.activate=Activate
admin.common.btn.deactivate=Deactivate
admin.common.btn.toggle=Toggle status
admin.common.col.id=ID
admin.common.col.name=Name
admin.common.col.club=Club
admin.common.col.date=Date
admin.common.col.status=Status
admin.common.col.actions=Actions
admin.common.col.email=Email
admin.common.col.phone=Phone
admin.common.empty=No data available
admin.common.form.field_error=Field error
admin.common.form.select_placeholder=Select...

# ─── Admin Nav ────────────────────────────────────────────────────────────────
admin.nav.panel=Admin Panel
admin.nav.tournaments=Tournaments
admin.nav.clubs=Clubs
admin.nav.players=Players
admin.nav.export=Export
admin.nav.site=View site
admin.nav.logout=Log out

# ─── Panel ────────────────────────────────────────────────────────────────────
admin.panel.title=Administration Panel
admin.panel.subtitle=Manage players and tournaments on 1-Padel
admin.panel.stat.players=Total players
admin.panel.stat.admins=Administrators
admin.panel.stat.active_tournaments=Active tournaments
admin.panel.stat.waitlist=Waitlist
admin.panel.koc.rounds=Calibration rounds:
admin.panel.koc.courts=Courts:
admin.panel.players.empty=No registered players
admin.panel.tournaments.empty=No tournaments created

# ─── Tournaments List ─────────────────────────────────────────────────────────
admin.tournaments.list.title=Tournament Management
admin.tournaments.list.subtitle=Manage all tournaments on the platform
admin.tournaments.list.col.info=Info
admin.tournaments.list.col.registered=Registered
admin.tournaments.list.empty.title=No tournaments
admin.tournaments.list.empty.text=Start by creating your first tournament

# ─── Tournament Form ──────────────────────────────────────────────────────────
admin.tournaments.form.title.create=Create Tournament
admin.tournaments.form.title.edit=Edit Tournament
admin.tournaments.form.subtitle=Fill in the tournament details below
admin.tournaments.form.field.club=Club
admin.tournaments.form.field.club.select=Select a club
admin.tournaments.form.field.name=Tournament Name
admin.tournaments.form.field.name.placeholder=E.g.: Summer Open Tournament 2026
admin.tournaments.form.field.date_start=Start Date
admin.tournaments.form.field.time_start=Start Time
admin.tournaments.form.field.duration=Estimated Duration
admin.tournaments.form.field.duration.placeholder=E.g.: 4 hours, 2 days
admin.tournaments.form.field.gender=Gender Format
admin.tournaments.form.field.gender.select=Select format
admin.tournaments.form.field.level=Category / Level
admin.tournaments.form.field.level.select=Select level
admin.tournaments.form.field.type=Tournament Type
admin.tournaments.form.field.type.select=Select type
admin.tournaments.form.field.type.koc=King of Court
admin.tournaments.form.field.type.americano=Americano (Individual)
admin.tournaments.form.field.type.americano_teams=Americano Teams (Qualification + Playoff)
admin.tournaments.form.field.type.cancha_abierta=Open Court
admin.tournaments.form.field.modality=Play Mode
admin.tournaments.form.field.modality.select=Select mode
admin.tournaments.form.field.capacity=Max Capacity
admin.tournaments.form.field.price=Price
admin.tournaments.form.field.currency=Currency
admin.tournaments.form.field.currency.ars=ARS (Argentine Peso)
admin.tournaments.form.field.currency.usd=USD (Dollar)
admin.tournaments.form.field.currency.usdt=USDT (Tether)
admin.tournaments.form.field.currency.eur=EUR (Euro)
admin.tournaments.form.field.status=Tournament Status
admin.tournaments.form.field.deadline=Cancellation Deadline
admin.tournaments.form.field.description=Detailed Description
admin.tournaments.form.field.description.placeholder=Additional information about the tournament...
admin.tournaments.form.field.contact=Organizer Contact
admin.tournaments.form.field.contact.placeholder=E.g.: John Smith - 123456789
admin.tournaments.form.field.faq=FAQ URL
admin.tournaments.form.field.faq.placeholder=https://example.com/faq

# ─── Tournament Details ───────────────────────────────────────────────────────
admin.tournaments.details.info.cancel_deadline=Cancel by
admin.tournaments.details.info.club=Club
admin.tournaments.details.info.duration=Duration
admin.tournaments.details.info.date=Start date
admin.tournaments.details.stat.attended=Attended:
admin.tournaments.details.stat.paid=Paid:
admin.tournaments.details.stat.total_paid=Total Paid:
admin.tournaments.details.col.pair=Partner
admin.tournaments.details.col.pair_contact=Partner contact
admin.tournaments.details.empty_participants=No confirmed participants
admin.tournaments.details.teams.lbl.bye=Bye per round:
admin.tournaments.details.teams.lbl.courts_suggested=Suggested courts:
admin.tournaments.details.teams.lbl.teams_loaded_manual=Loaded teams (manual):
admin.tournaments.details.teams.lbl.format_expected=Expected format:
admin.tournaments.details.teams.lbl.confirmed=Confirmed registrations:
admin.tournaments.details.teams.lbl.players_confirmed=Confirmed players:
admin.tournaments.details.teams.lbl.players_per_round=Players per round:
admin.tournaments.details.teams.lbl.pairs=Registered pairs:
admin.tournaments.details.teams.lbl.rounds_suggested=Suggested rounds:
admin.tournaments.details.teams.lbl.teams_loaded=Loaded teams:
admin.tournaments.details.teams.lbl.min_required=Minimum required:
admin.tournaments.details.teams.min_teams=You need at least <strong>4 teams</strong> loaded.
admin.tournaments.details.teams.step1=Step 1 — Load teams
admin.tournaments.details.teams.step2=Step 2 — Start qualification
admin.tournaments.details.teams.calibration_rounds=Calibration rounds
admin.tournaments.details.teams.courts_count=Number of courts
admin.tournaments.details.teams.status.playoff=Playoff in progress
admin.tournaments.details.teams.status.qualification=Qualification in progress
admin.tournaments.details.teams.format.desc=<strong>Americano Teams Format</strong>: each team plays
admin.tournaments.details.teams.format.playoff_suffix=e.g. 6:4). Top teams advance to <strong>elimination playoff</strong>.
admin.tournaments.details.teams.format.playoff_plus=+ <strong>Elimination playoff</strong>.
admin.tournaments.details.teams.format.full=<strong>Full</strong>: each pair plays together exactly once, no repeats.
admin.tournaments.details.teams.format.flex=<strong>Flex</strong>: flexible format, minimises repeats.
admin.tournaments.details.teams.format.qualification=<strong>Qualification</strong> (2 matches per team, game score: 6:4)
admin.tournaments.details.teams.option.auto=Auto (Full if possible, Flex otherwise)
admin.tournaments.details.teams.option.flex=Flex Americano — maximum variety

# ─── KoC admin ────────────────────────────────────────────────────────────────
admin.koc.title=King of Court — Management
admin.koc.court=Court {0}
admin.koc.round=Round {0}
admin.koc.status.playing=IN PLAY
admin.koc.status.completed=COMPLETED
admin.koc.btn.next_round=Next round
admin.koc.btn.rollback=Rollback round
admin.koc.btn.finish=Finish tournament
admin.koc.btn.youtube=Save YouTube
admin.koc.btn.save_result=Save result
admin.koc.empty=No active rounds

# ─── Payments ─────────────────────────────────────────────────────────────────
admin.payments.title=Payment Management
admin.payments.subtitle=Manage tournament payments
admin.payments.col.player=Player
admin.payments.col.amount=Amount
admin.payments.col.status=Status
admin.payments.col.date=Payment date
admin.payments.col.method=Method
admin.payments.btn.confirm=Confirm payment
admin.payments.btn.reject=Reject
admin.payments.stat.total=Total collected
admin.payments.stat.pending=Pending
admin.payments.stat.confirmed=Confirmed
admin.payments.empty=No payments registered

# ─── Americano admin ──────────────────────────────────────────────────────────
admin.americano.title=Americano — Management
admin.americano.double.title=Team Americano — Management
admin.americano.btn.initialize=Initialize tournament
admin.americano.btn.start_round=Start round
admin.americano.btn.complete_round=Complete round
admin.americano.btn.save_result=Save result
admin.americano.btn.finish=Finish tournament
admin.americano.btn.preview=Preview rounds
admin.americano.btn.dropout=Drop out
admin.americano.section.rounds=Rounds
admin.americano.section.matches=Matches
admin.americano.section.ranking=Rankings
admin.americano.status.pending=Pending
admin.americano.status.in_progress=In progress
admin.americano.status.completed=Completed
admin.americano.status.cancelled=Cancelled
admin.americano.empty.rounds=No rounds available
admin.americano.empty.matches=No matches in this round
admin.americano.empty.ranking=No rankings available
admin.americano.col.round=Round
admin.americano.col.court=Court
admin.americano.col.team1=Team 1
admin.americano.col.team2=Team 2
admin.americano.col.result=Result
admin.americano.col.points=Points
admin.americano.col.played=P
admin.americano.col.won=W
admin.americano.col.lost=L

# ─── Playoff admin ────────────────────────────────────────────────────────────
admin.playoff.title=Playoff — Management
admin.playoff.btn.generate=Generate bracket
admin.playoff.btn.start=Start playoff
admin.playoff.btn.finish=Finish
admin.playoff.bracket.title=Elimination bracket
admin.playoff.bracket.round=Round {0}
admin.playoff.bracket.winner=Winner
admin.playoff.bracket.tbd=TBD
admin.playoff.qualification.title=Qualification standings
admin.playoff.qualification.col.team=Team
admin.playoff.qualification.col.points=Points
admin.playoff.qualification.col.qualify=Qualifies
admin.playoff.status.pending=Pending
admin.playoff.status.in_progress=In progress
admin.playoff.status.finished=Finished
admin.playoff.empty=No playoff data available

# ─── Preview rounds ───────────────────────────────────────────────────────────
admin.preview.title=Round preview
admin.preview.double.title=Round preview (pairs)
admin.preview.col.round=Round
admin.preview.col.court=Court
admin.preview.col.team_a=Team A
admin.preview.col.team_b=Team B
admin.preview.col.bye=Bye
admin.preview.btn.confirm=Confirm and initialize
admin.preview.btn.back=Back
admin.preview.stat.rounds=Total rounds:
admin.preview.stat.courts=Courts:
admin.preview.stat.players=Players:
admin.preview.empty=No rounds to display

# ─── Clubs List ───────────────────────────────────────────────────────────────
admin.clubs.list.title=Club Management
admin.clubs.list.subtitle=Manage 1-Padel clubs
admin.clubs.list.col.city=City/Zone
admin.clubs.list.col.phone=Phone
admin.clubs.list.empty.title=No clubs
admin.clubs.list.empty.text=Start by creating your first club

# ─── Club Form ────────────────────────────────────────────────────────────────
admin.clubs.form.title.create=Create Club
admin.clubs.form.title.edit=Edit Club
admin.clubs.form.field.name=Club Name
admin.clubs.form.field.city=City
admin.clubs.form.field.zone=Zone
admin.clubs.form.field.address=Address
admin.clubs.form.field.phone=Phone
admin.clubs.form.field.email=Email
admin.clubs.form.field.courts=Number of courts
admin.clubs.form.field.description=Description
admin.clubs.form.field.website=Website
admin.clubs.form.field.active=Active

# ─── Club Details ─────────────────────────────────────────────────────────────
admin.clubs.details.title=Club Details
admin.clubs.details.field.city=City
admin.clubs.details.field.zone=Zone
admin.clubs.details.field.address=Address
admin.clubs.details.field.phone=Phone
admin.clubs.details.field.email=Email
admin.clubs.details.field.courts=Courts
admin.clubs.details.field.website=Website
admin.clubs.details.tournaments.title=Club tournaments
admin.clubs.details.tournaments.empty=No tournaments at this club

# ─── Players List ─────────────────────────────────────────────────────────────
admin.players.list.title=Player Management
admin.players.list.subtitle=Manage all players on the platform
admin.players.list.col.registered=Registered
admin.players.list.col.email_confirmed=Email Confirmed
admin.players.list.stat.total=Total Players
admin.players.list.stat.email_confirmed=Email Confirmed
admin.players.list.empty.title=No players
admin.players.list.empty.text=Players will appear here when they register

# ─── Player Details ───────────────────────────────────────────────────────────
admin.players.details.title=Player Details
admin.players.details.field.email=Email
admin.players.details.field.phone=Phone
admin.players.details.field.telegram=Telegram
admin.players.details.field.level=Level
admin.players.details.field.registered=Registered
admin.players.details.field.email_confirmed=Email confirmed
admin.players.details.field.status=Status
admin.players.details.field.google=Google OAuth
admin.players.details.tournaments.title=Player tournaments
admin.players.details.tournaments.col.tournament=Tournament
admin.players.details.tournaments.col.status=Registration status
admin.players.details.tournaments.col.date=Date
admin.players.details.tournaments.empty=No registered tournaments
admin.players.details.ranking.title=Ranking position
admin.players.details.ranking.col.points=Points
admin.players.details.ranking.col.position=Position
admin.players.details.ranking.col.wins=Wins
```

---

## messages-ru.js — секция admin

```javascript
// === ADMIN COMMON ===
'admin.common.success.deleted':     'Элемент успешно удалён',
'admin.common.success.status':      'Статус успешно обновлён',
'admin.common.error.delete':        'Ошибка при удалении',
'admin.common.error.status':        'Ошибка обновления статуса',
'admin.common.error.connection':    'Ошибка соединения',

// === ADMIN AMERICANO ===
'admin.americano.status.pending':       'Ожидает',
'admin.americano.status.in_progress':   'Идёт',
'admin.americano.status.completed':     'Завершён',
'admin.americano.status.cancelled':     'Отменён',
'admin.americano.round.started':        'Раунд начат',
'admin.americano.round.completed':      'Раунд завершён',
'admin.americano.result.saved':         'Результат сохранён',
'admin.americano.error.start_round':    'Ошибка запуска раунда',
'admin.americano.error.complete_round': 'Ошибка завершения раунда',
'admin.americano.error.save_result':    'Ошибка сохранения результата',

// === KING OF COURT ===
'admin.koc.confirm.initialize':     'Инициализировать турнир King of Court?',
'admin.koc.initialized':            'Турнир инициализирован',
'admin.koc.round.next':             'Переходим к следующему раунду',
'admin.koc.result.saved':           'Результат сохранён',
'admin.koc.result.updated':         'Результат обновлён',
'admin.koc.finished':               'Турнир завершён',
'admin.koc.youtube.saved':          'Ссылка YouTube сохранена',
'admin.koc.status.playing':         'В ИГРЕ',
'admin.koc.status.completed':       'ЗАВЕРШЁН',
'admin.koc.player.unknown':         'Неизвестный игрок',
'admin.koc.victory':                'Победа',
'admin.koc.defeat':                 'Поражение',
'admin.koc.error.initialize':       'Ошибка инициализации',
'admin.koc.error.fields':           'Не все поля заполнены',
'admin.koc.error.same_winners':     'Выберите двух разных победителей',
'admin.koc.error.score_invalid':    'Счёт победителей должен быть больше счёта проигравших',
'admin.koc.error.court_data':       'Не удалось загрузить данные корта',
'admin.koc.error.losers':           'Ошибка определения проигравших',
'admin.koc.error.save':             'Ошибка сохранения',
'admin.koc.error.next_round':       'Ошибка перехода к следующему раунду',
'admin.koc.error.finish':           'Ошибка завершения турнира',
'admin.koc.error.rollback':         'Ошибка отката раунда',
'admin.koc.error.connection':       'Ошибка соединения',
```

---

## messages-en.js — секция admin

```javascript
// === ADMIN COMMON ===
'admin.common.success.deleted':     'Item deleted successfully',
'admin.common.success.status':      'Status updated successfully',
'admin.common.error.delete':        'Error deleting item',
'admin.common.error.status':        'Error updating status',
'admin.common.error.connection':    'Connection error',

// === ADMIN AMERICANO ===
'admin.americano.status.pending':       'Pending',
'admin.americano.status.in_progress':   'In progress',
'admin.americano.status.completed':     'Completed',
'admin.americano.status.cancelled':     'Cancelled',
'admin.americano.round.started':        'Round started',
'admin.americano.round.completed':      'Round completed',
'admin.americano.result.saved':         'Result saved',
'admin.americano.error.start_round':    'Error starting round',
'admin.americano.error.complete_round': 'Error completing round',
'admin.americano.error.save_result':    'Error saving result',

// === KING OF COURT ===
'admin.koc.confirm.initialize':     'Initialize King of Court tournament?',
'admin.koc.initialized':            'Tournament initialized',
'admin.koc.round.next':             'Moving to next round',
'admin.koc.result.saved':           'Result saved',
'admin.koc.result.updated':         'Result updated',
'admin.koc.finished':               'Tournament finished',
'admin.koc.youtube.saved':          'YouTube link saved',
'admin.koc.status.playing':         'IN PLAY',
'admin.koc.status.completed':       'COMPLETED',
'admin.koc.player.unknown':         'Unknown player',
'admin.koc.victory':                'Victory',
'admin.koc.defeat':                 'Defeat',
'admin.koc.error.initialize':       'Initialization error',
'admin.koc.error.fields':           'Not all fields are complete',
'admin.koc.error.same_winners':     'Select two different winners',
'admin.koc.error.score_invalid':    'Winners score must be higher than losers score',
'admin.koc.error.court_data':       'Could not load court data',
'admin.koc.error.losers':           'Error determining losers',
'admin.koc.error.save':             'Error saving',
'admin.koc.error.next_round':       'Error moving to next round',
'admin.koc.error.finish':           'Error finishing tournament',
'admin.koc.error.rollback':         'Error rolling back round',
'admin.koc.error.connection':       'Connection error',
```

---

## Чеклист этапа 13

### messages_ru.properties
- [ ] Все `admin.*` ключи из этапа 11 переведены
- [ ] HTML-теги (`<strong>`) сохранены в значениях
- [ ] Плейсхолдеры `{0}` не потеряны

### messages_en.properties
- [ ] Все `admin.*` ключи из этапа 11 переведены

### messages-ru.js
- [ ] Все `admin.*` ключи из этапа 12 переведены

### messages-en.js
- [ ] Все `admin.*` ключи из этапа 12 переведены

### Проверка
- [ ] Нет ключей с пустым значением
- [ ] Имена собственные (`King of Court`, `Americano`, `Playoff`, `Flex`, `Full`) не переведены
