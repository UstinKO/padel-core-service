# LFPT-0650: Fix NPE в логе TournamentServiceLevelRestrictionTest — задать horaInicio в фикстуре турнира

## Статус
done

## Источник
Issue [#650](https://github.com/UstinKO/padel-core-service/issues/650) (найдено при прогоне `./mvnw verify` в #617). Клиентского запроса нет — спека написана напрямую по issue.

## Контекст / зачем
При прогоне `TournamentServiceLevelRestrictionTest` в лог дважды пишется `NullPointerException` уровня ERROR:

```
ERROR TournamentService -- Error sending tournament confirmation email: Cannot invoke "java.time.LocalTime.format(java.time.format.DateTimeFormatter)" because the return value of "com.padle.core.padelcoreservice.model.Tournament.getHoraInicio()" is null
java.lang.NullPointerException: ...
    at TournamentService.sendConfirmationEmail(...)
    at TournamentService.registerPlayer(...)
```

Воспроизведено на `master` 2026-10-26. Причина в тестовой фикстуре: `setUp()` собирает `Tournament` без `horaInicio`. Два теста с успешной регистрацией (`jugadorDentroDeUnaCategoria_seRegistraNormalmente`, `torneoDeFormatoAbierto_noAplicaElChequeoAunqueElNivelEsteLejos`) доходят до `sendConfirmationEmail`, и там падает `tournament.getHoraInicio().format(...)`. Исключение глотает `catch (Exception)` в том же методе, поэтому тесты зелёные, а стек ERROR засоряет вывод `./mvnw verify` и маскирует настоящие ошибки.

В проде дефект не воспроизводится: `hora_inicio` — `NOT NULL` (Liquibase `v1.2-create-tournament-table.yaml`, `@Column(name = "hora_inicio", nullable = false)` в `Tournament`), `TournamentCreateDTO.horaInicio` — `@NotNull`. Фикстура описывает турнир, который в реальной системе существовать не может.

## Связь с MASTER.md
Бизнес-инварианты [specs/MASTER.md](../MASTER.md) не затрагиваются: правка только в тестовой фикстуре. Логика `TournamentLevelPolicy` (LFPT-0303), которую проверяет тест, не меняется.

## Требования

### Функциональные
1. В `TournamentServiceLevelRestrictionTest.setUp()` в builder `Tournament` добавить `horaInicio` (например, `.horaInicio(LocalTime.of(18, 0))`) и импорт `java.time.LocalTime`.
2. Остальные поля фикстуры, стабы и тесты не менять.

### Нефункциональные
- i18n: нет новых строк.
- Безопасность: нет новой точки входа данных.
- Прод-код (`src/main/**`) не меняется.

## Вне скоупа
- `TournamentService.sendConfirmationEmail` и его `catch (Exception)`: нарушение `CODE_STYLE.md` остаётся, при необходимости — отдельной tech-debt задачей.
- Предупреждение `WARN Club not found for id: null`, которое появится после фикса (см. Edge cases): это WARN, а не ERROR, `clubId` в фикстуре не задаём и `clubService` не стабим.
- Проверка отправки письма (`verify(emailService).sendTournamentConfirmationEmail(...)`): тест про ограничение по уровню, а не про письма.
- Другие тестовые классы с такими же фикстурами без `horaInicio`, если найдутся, — отдельными задачами.

## Изменения в системе

### API
Нет изменений.

### БД
Нет изменений.

### UI
Нет изменений.

Затронут один файл: `src/test/java/com/padle/core/padelcoreservice/service/TournamentServiceLevelRestrictionTest.java`. Документация (`docs/`, `.agents/PROJECT.md`) не требует обновления: правка только в тесте.

## Критерии приёмки
- [ ] `./mvnw test -Dtest=TournamentServiceLevelRestrictionTest` — 4 теста зелёные; в выводе нет `NullPointerException` с `getHoraInicio()` и нет строки `ERROR ... Error sending tournament confirmation email`.
- [ ] `./mvnw verify` зелёный (нужен PostgreSQL).
- [ ] В диффе только `TournamentServiceLevelRestrictionTest.java` (строка `horaInicio` в `setUp()` + импорт `java.time.LocalTime`).

## Edge cases
- **После фикса `sendConfirmationEmail` проходит дальше.** `resolveClubName(null)` вызывает незастабленный мок `clubService.getClubById(null)`, Mockito возвращает `Optional.empty()`, в лог пишется `WARN Club not found for id: null` — ожидаемо, письмо уходит в мок `emailService`.
- **`getFormattedCancellationDeadline()`.** `deadlineCancelacion` в фикстуре не задан, поэтому дедлайн считается через `calculateDefaultDeadline()` = `LocalDateTime.of(fechaInicio, horaInicio)`. Без `horaInicio` он бы тоже упал с NPE — заданное `horaInicio` закрывает и это место.
- **Тесты, которые бросают исключение до регистрации** (`jugadorFueraDeRango_bloqueaAntesDeTocarLaRegistracion`, `jugadorSinNivelEspecificado_bloqueaConExcepcionDistinguible`) не доходят до письма, поле им не мешает; `verifyNoInteractions(registrationRepository)` по-прежнему выполняется.
- **Strict stubs `MockitoExtension`.** Новых стабов нет, поэтому `UnnecessaryStubbingException` не возникнет.

## Открытые вопросы
Нет.
