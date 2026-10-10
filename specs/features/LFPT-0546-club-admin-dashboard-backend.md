# LFPT-0546: Feature клубная учётная запись — фильтрация статистики главной панели по клубу (бэкенд)

## Статус
approved

## Источник
Клиентский запрос [specs/requests/LFPT-0546-club-admin-dashboard.md](../requests/LFPT-0546-club-admin-dashboard.md) (Telegram, пересланное сообщение от Evgeny_754, `telegram_launch_message_id: 4992`). GitHub issue [#546](https://github.com/UstinKO/padel-core-service/issues/546).

Разбито на бэкенд/фронтенд (`GIT_WORKFLOW.md` §1.4) — эта спека покрывает бэкенд. Зависимая фронтенд-задача — [#547](https://github.com/UstinKO/padel-core-service/issues/547) (скрытие карточки «Администраторы»).

## Контекст / зачем
После входа клубная учётная запись (`ROLE_CLUB_ADMIN`) видит на главной админ-панели (`GET /admin`, `AdminController.adminPanel()`) статистику **всей платформы** 1-Padel: все активные турниры, общий лист ожидания, последние турниры всех клубов. Это вводит клуб в заблуждение — он должен видеть рабочую информацию только своего клуба.

## Связь с MASTER.md
Не меняет инвариантов `specs/MASTER.md` — не вводит новую роль и не меняет флоу регистрации/статусов. `ROLE_CLUB_ADMIN` и изоляция доступа по клубу (`Owner.clubId`) уже описаны в MASTER.md (LFPT-375/LFPT-376, раздел «Роли пользователей») — эта задача расширяет тот же принцип изоляции на статистику главной панели, явно ещё не упомянутую там как область изоляции. Точечное дополнение MASTER.md не требуется — раздел уже описывает `ROLE_CLUB_ADMIN` общей формулировкой ("видит и администрирует только турниры, регистрации, оплаты и матчи... своего клуба"), под которую статистика панели подпадает по смыслу.

## Требования

### Функциональные
1. **`TournamentRepository`** — новый метод `long countByClubIdAndIsActiveTrue(Long clubId)` (derived query, по аналогии с существующими `findByClubId`/`countByIsActiveTrue`).
2. **`TournamentRepository`** — новый метод с `@Query`, аналогичный существующему `findTopByOrderByCreatedAtDesc(int limit)` (строка 44-45), но с фильтром по клубу:
   ```
   @Query("SELECT t FROM Tournament t WHERE t.clubId = :clubId ORDER BY t.createdAt DESC LIMIT :limit")
   List<Tournament> findTopByClubIdOrderByCreatedAtDesc(@Param("clubId") Long clubId, @Param("limit") int limit)
   ```
3. **`TournamentRegistrationRepository`** — новый метод с `@Query`, аналогичный существующему `countTotalWaitlist()` (строка 67-68), но с join на `tournament.clubId` (по образцу уже существующего `findDistinctPlayerIdsByTournamentClubId`, строка 30-31):
   ```
   @Query("SELECT COUNT(tr) FROM TournamentRegistration tr WHERE tr.status = 'WAITLIST' AND tr.isActive = true AND tr.tournament.clubId = :clubId")
   long countTotalWaitlistByClub(@Param("clubId") Long clubId)
   ```
4. **`TournamentService`** — три новых club-scoped метода рядом с существующими платформенными аналогами (секция `// ==================== НОВЫЕ МЕТОДЫ ДЛЯ КОНТРОЛЛЕРА ====================`, строки 879-889, и `getTotalWaitlistCount()`, строка 1226-1230):
   - `long getTotalActiveTournamentsForClub(Long clubId)` → `tournamentRepository.countByClubIdAndIsActiveTrue(clubId)`.
   - `List<TournamentDto> getRecentTournamentsForClub(Long clubId, int limit)` → `mapToDtoWithDetails(tournamentRepository.findTopByClubIdOrderByCreatedAtDesc(clubId, limit))` (переиспользует существующий приватный `mapToDtoWithDetails(List<Tournament>)`, как и `getRecentTournaments`).
   - `long getTotalWaitlistCountForClub(Long clubId)` → `registrationRepository.countTotalWaitlistByClub(clubId)`.
5. **`AdminController.adminPanel()`** — ветвление по `owner.isClubAdmin()`:
   - `isClubAdmin() == true` и `owner.getClubId() != null` → использовать три club-scoped метода выше с `owner.getClubId()`.
   - `isClubAdmin() == true` и `owner.getClubId() == null` (не должно происходить в норме, но защититься по аналогии с `clubsForForm()` в этом же контроллере, строки 532-544, которая уже обрабатывает этот edge case) → `totalTournaments = 0`, `totalWaitlist = 0`, `recentTournaments = List.of()`.
   - Иначе (`OWNER`/`SUPER_ADMIN`/`ORGANIZER`) → без изменений, текущие платформенные методы (`getTotalActiveTournaments()`, `getTotalWaitlistCount()`, `getRecentTournaments(5)`).
   - `recentTournamentsWithFlags` строится из результата (клубного или платформенного) `recentTournaments` тем же существующим кодом (строки 70-78) — без изменений самой логики построения флагов, только источник входного списка меняется для `CLUB_ADMIN`.
6. **Не трогать**: `totalPlayers` (`playerService.contarJugadoresActivos()`) и `recentPlayers` (`playerService.getRecentPlayers(5)`) — явное решение заказчика оставить как есть на этом этапе (вопрос доступа клуба ко всей базе игроков решается позже). `totalOwners` — значение продолжает вычисляться и передаваться в модель как сейчас (`ownerService.getTotalActiveOwners()`, без изменений); скрытие соответствующей карточки в шаблоне для `CLUB_ADMIN` — отдельная фронтенд-задача [#547], не часть этой спеки.

### Нефункциональные
- i18n: новых пользовательских строк нет.
- Безопасность: новой точки входа данных от пользователя нет — `owner.getClubId()` берётся из `@AuthenticationPrincipal`, не из параметров запроса (тот же паттерн, что уже используется в `AdminController.listTournaments()`, строка 111, и `TournamentAccessService`).
- Производительность: три новых запроса вместо трёх существующих, та же сложность (один `COUNT`, один `COUNT` с join, один `SELECT ... LIMIT`) — не добавляет N+1.

## Вне скоупа
- Карточка «Администраторы» (`totalOwners`) и её скрытие в шаблоне для `CLUB_ADMIN` — [#547].
- `totalPlayers`/`recentPlayers` — явно вне скоупа по решению заказчика (см. клиентский запрос, п.5).
- Любые изменения прав на управление турнирами/регистрациями/оплатами — уже реализованы (LFPT-375/376), не затрагиваются.

## Изменения в системе

### API
Нет REST-эндпоинтов. Меняется только содержимое модели Thymeleaf-рендеринга `GET /admin` (серверный маршрут, не REST API) для роли `CLUB_ADMIN`.

### БД
Нет новой миграции — используются существующие колонки `tournaments_db.club_id`, `tournament_registrations_db.status`/`is_active` и существующая связь `TournamentRegistration.tournament`.

### UI
Нет изменений шаблонов в этой (backend) задаче — `admin/panel.html` продолжает читать те же имена атрибутов модели (`totalTournaments`, `totalWaitlist`, `recentTournaments`/`recentTournamentsWithFlags`), меняется только то, чем контроллер их заполняет для `CLUB_ADMIN`.

## Критерии приёмки
- [ ] `GET /admin` для `CLUB_ADMIN` без активных турниров своего клуба (но с активными турнирами других клубов на платформе) — `totalTournaments == 0`.
- [ ] `GET /admin` для `CLUB_ADMIN` с N активными турнирами своего клуба и M активными турнирами других клубов (M > 0) — `totalTournaments == N`, не `N + M`.
- [ ] `totalWaitlist` для `CLUB_ADMIN` считает только регистрации со статусом `WAITLIST` на турнирах своего клуба, не на турнирах других клубов.
- [ ] «Последние турниры» (`recentTournaments`/`recentTournamentsWithFlags`) для `CLUB_ADMIN` содержат только турниры его клуба, отсортированные по дате создания по убыванию, даже если на платформе есть более свежие турниры других клубов.
- [ ] `CLUB_ADMIN` с `clubId == null` — `GET /admin` не бросает исключение, `totalTournaments == 0`, `totalWaitlist == 0`, список последних турниров пуст.
- [ ] `GET /admin` для `OWNER`/`SUPER_ADMIN`/`ORGANIZER` — `totalTournaments`, `totalWaitlist`, `recentTournaments` совпадают с текущим (платформенным) поведением до этой задачи (regression).
- [ ] `totalPlayers`, `recentPlayers`, `totalOwners` не изменены этой задачей ни для одной роли.
- [ ] `./mvnw verify` зелёный.

## Edge cases
- У клуба есть активные турниры, но все они `SOLO_POR_ENLACE`/любой другой режим видимости (если применимо) — видимость турнира (`TournamentVisibility`) не влияет на админ-панель ни для одной роли (см. спеку LFPT-0491, п.8 — админка видит оба режима); клубная статистика считает такие турниры так же, как публичные.
- У клуба 0 турниров вообще (новый клуб) — все три показателя `0`/пустой список, без исключений (та же логика, что и "нет активных").
- Два реплики в Docker Swarm — новые методы read-only (`@Transactional(readOnly = true)` на уровне класса `TournamentService`, не переопределяется), дополнительного риска конкурентного доступа не создают.

## Открытые вопросы
Нет. Технические решения приняты архитектором самостоятельно (аналогично п.3 `GIT_WORKFLOW.md`):
1. Названия новых методов (`getTotalActiveTournamentsForClub`, `getRecentTournamentsForClub`, `getTotalWaitlistCountForClub`, `countByClubIdAndIsActiveTrue`, `findTopByClubIdOrderByCreatedAtDesc`, `countTotalWaitlistByClub`) — по аналогии с существующими платформенными методами и уже принятым в проекте паттерном `*ByClub`/`*ForClub` (`getTournamentsByClub`, `getPublicTournamentsByClub`).
2. Обработка `owner.getClubId() == null` для `CLUB_ADMIN` — нули/пустой список вместо исключения, по аналогии с `AdminController.clubsForForm()` (единственный уже существующий в этом контроллере прецедент на этот edge case).
