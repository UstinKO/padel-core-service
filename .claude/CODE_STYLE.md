# CODE_STYLE.md — стиль Java-кода в padel-core-service

Обязательные правила написания Java-кода. Читают **все**, кто пишет или ревьюит код: Developer, Tester (тестовый код), Architect (финальная сверка), обычная интерактивная сессия. Дополняет [CODE_REVIEW.md](CODE_REVIEW.md) (там — *что* проверять: ТЗ, DRY, SOLID, ловушки), здесь — *как* писать.

**Главная мысль:** проект на **Java 17** (`<java.version>17</java.version>`), а значительная часть кода написана в стиле Java 8: ручные `null`-проверки, `orElse(null)` с последующим `if (x == null)`, императивные циклы с аккумулирующим `new ArrayList<>()`, `catch (Exception e)` на каждом шаге. Новый код так писать нельзя. Целевой стиль — современный Java 17: неизменяемые данные, `Optional` вместо `null`, Stream API вместо циклов-аккумуляторов, `record`, switch-выражения, pattern matching для `instanceof`.

> Аудит 2026-10-09 (`src/main/java`, 229 файлов, ~30 тыс. строк): `== null`/`!= null` — 528 мест в 82 файлах; `orElse(null)` — 24; `Optional.isPresent()` + `get()` — 20/36; `catch (Exception …)` — 161 место в 37 файлах; `Map<String, Object>` как "DTO" — 193 места; `Collectors.toList()` — 70 (при 105 `.toList()`); `record` — только 13 вложенных, ни одного DTO; text blocks — 0; `@Data` на JPA-сущностях — 6. Задачи на рефакторинг — с меткой `tech-debt` и ссылкой на этот файл.

---

## 0. Границы языка: Java 17, не 21

Компиляция идёт с `release 17` (Docker/CI запускают на 21, но исходники — 17). **Нельзя** (не скомпилируется): pattern matching в `switch` (`case Foo f ->`), record patterns (`if (o instanceof Point(int x, int y))`), `List.getFirst()/getLast()` (SequencedCollection), virtual threads, string templates.

**Можно и нужно:** `record`, switch-выражения со стрелками и `yield`, pattern matching для `instanceof`, text blocks `"""`, `var` для локальных переменных, `Stream.toList()`, `Optional.orElseThrow()` без аргументов, `Optional.isEmpty()`, `Optional.or()/stream()/ifPresentOrElse()`, `List.of/Set.of/Map.of`, `Collectors.teeing`, `String.isBlank()/strip()/formatted()`, `Objects.requireNonNullElse`.

---

## 1. `null` и `Optional`

**1.1. Метод, который может ничего не вернуть, возвращает `Optional<T>`, а не `null`.** Это касается сервисов, хелперов, приватных методов. `return null;` в новом коде — только в реализациях чужих контрактов (Spring/Hibernate/Jackson требуют `null`) и с комментарием, почему.

**1.2. Не "распаковывать" `Optional` обратно в `null`.** Запрещено: `orElse(null)` + `if (x == null)`, `isPresent()` + `get()`. Голый `get()` запрещён вообще — только `orElseThrow()`.

```java
// ❌ было (TeamPlayoffService.buildMatchNote)
AmericanoTeam t1 = match.getTeam1Id() != null ? teamRepository.findById(match.getTeam1Id()).orElse(null) : null;
AmericanoTeam t2 = match.getTeam2Id() != null ? teamRepository.findById(match.getTeam2Id()).orElse(null) : null;
return (t1 != null ? t1.getDisplayName() : tbdLabel) + " vs " + (t2 != null ? t2.getDisplayName() : tbdLabel);

// ✅ надо
private String buildMatchNote(AmericanoMatch match, String tbdLabel) {
    return teamName(match.getTeam1Id(), tbdLabel) + " vs " + teamName(match.getTeam2Id(), tbdLabel);
}

private String teamName(Long teamId, String fallback) {
    return Optional.ofNullable(teamId)
            .flatMap(teamRepository::findById)
            .map(AmericanoTeam::getDisplayName)
            .orElse(fallback);
}
```

```java
// ❌ было
TournamentRegistration reg = registrationRepository.findById(registrationId).orElse(null);
if (reg == null || !Boolean.TRUE.equals(reg.getIsDoubleRegistration())) {
    return;
}
// ... работа с reg

// ✅ надо
registrationRepository.findById(registrationId)
        .filter(reg -> Boolean.TRUE.equals(reg.getIsDoubleRegistration()))
        .ifPresent(reg -> syncPairPayment(tournamentId, reg, hasPaid, attended));
```

**1.3. Цепочки `null`-проверок через getter'ы — через `Optional.map`.**

```java
// ❌ было (TeamPlayoffService.importFromRegistrations)
Long partnerId = partnerReg != null ? partnerReg.getPlayer().getId()
        : (mainReg.getPartner() != null ? mainReg.getPartner().getId() : null);

// ✅ надо
Optional<Long> partnerId = Optional.ofNullable(partnerReg)
        .map(r -> r.getPlayer().getId())
        .or(() -> Optional.ofNullable(mainReg.getPartner()).map(PlayerPadel::getId));
```

**1.4. "Не найдено" = исключение, а не `null` и не пустая заглушка.** Если отсутствие сущности — ошибка вызова, `findById(id).orElseThrow(() -> new ResourceNotFoundException(...))`. Повторяющиеся `orElseThrow` по одному и тому же репозиторию выносятся в один приватный метод `getTournamentOrThrow(id)` / `requireTeam(id)` (DRY).

**1.5. Где `Optional` НЕ использовать** (иначе станет хуже):
- поле класса/entity/DTO, параметр метода, элемент коллекции — нет (`Optional` не сериализуется и не предназначен для этого). Для параметров — перегрузка или `@Nullable`;
- коллекции не оборачиваются в `Optional` — пустая коллекция и есть "ничего": возвращать `List.of()`, никогда не `null`;
- в "горячем" алгоритмическом коде (генерация раундов Americano, движок плей-офф) с примитивами — можно `OptionalInt` или простой `if`, если `Optional` ухудшает читаемость;
- вместо тривиального `x != null ? x : default` — `Objects.requireNonNullElse(x, default)`, а не `Optional.ofNullable(x).orElse(default)`.

**1.6. `Boolean`-обёртки.** `Boolean.TRUE.equals(flag)` — нормально для nullable-полей entity (их 25 в коде, это допустимо). В новых полях/DTO, где `null` не имеет смысла, — примитив `boolean` с дефолтом в БД, тогда проверка не нужна.

---

## 2. Коллекции и Stream API

**2.1. Трансформация коллекции = stream, не цикл с аккумулятором.** Паттерн "создать `new ArrayList<>()` → `for` → `if` → `add`" заменяется на `stream().filter().map().toList()`.

```java
// ❌
List<TeamDto> result = new ArrayList<>();
for (AmericanoTeam team : teams) {
    if (Boolean.TRUE.equals(team.getAttended())) {
        result.add(mapper.toDto(team));
    }
}
return result;

// ✅
return teams.stream()
        .filter(team -> Boolean.TRUE.equals(team.getAttended()))
        .map(mapper::toDto)
        .toList();
```

**2.2. `.toList()` вместо `collect(Collectors.toList())`.** `toList()` возвращает неизменяемый список — это и есть цель. `Collectors.toList()`/`toCollection(ArrayList::new)` — только если список дальше реально мутируется (и тогда это видно по коду).

**2.3. Группировки и индексы — через `Collectors`, не через `HashMap` + `computeIfAbsent` в цикле.**

```java
// ✅
Map<Long, List<TournamentRegistration>> byPair = registrations.stream()
        .collect(Collectors.groupingBy(RegistrationPairPositions::pairGroupId));
Map<Long, PlayerPadel> playersById = players.stream()
        .collect(Collectors.toMap(PlayerPadel::getId, Function.identity()));
long confirmed = registrations.stream().filter(r -> r.getStatus() == CONFIRMED).count();
```

**2.4. Поиск — `anyMatch/noneMatch/allMatch/findFirst`, не цикл с флагом и `break`.**

**2.5. Никаких хаков ради лямбд.** `int[] counter = {1}` / `AtomicInteger` только чтобы инкрементировать счётчик внутри `forEach`/`computeIfAbsent` — признак, что stream здесь не подходит. Тогда либо честный цикл, либо `IntStream`/`Stream.iterate`, либо перестроить алгоритм. Побочные эффекты внутри `map`/`filter` запрещены; `forEach` — только для конечного действия (сохранить, отправить), не для сборки результата.

```java
// ❌ (RegistrationPairPositions)
int[] nextPos = {1};
for (TournamentRegistration reg : ordered) {
    if (...) continue;
    groupToPos.computeIfAbsent(pairGroupId(reg), k -> nextPos[0]++);
}

// ✅
List<Long> groupIds = ordered.stream()
        .filter(r -> ACTIVE_STATUSES.contains(r.getStatus()))
        .map(RegistrationPairPositions::pairGroupId)
        .distinct()
        .toList();
return IntStream.range(0, groupIds.size()).boxed()
        .collect(Collectors.toMap(groupIds::get, i -> i + 1, (a, b) -> a, LinkedHashMap::new));
```

**2.6. Когда цикл — нормально.** Алгоритмы с индексной арифметикой, `int[]`-слотами, ранним выходом по сложному условию, мутацией нескольких структур одновременно (генерация раундов Americano, `PlayoffMatchingEngine`, `buildCirclePairs`) — классический `for` читается лучше. Правило: stream — для *трансформации данных*, цикл — для *алгоритма*. Если stream получается длиннее ~6 операций или требует вложенных лямбд в 3 уровня — разбить на именованные методы или оставить цикл.

**2.7. Неизменяемость по умолчанию.** Константы-коллекции — `List.of/Set.of/Map.of` (или `EnumSet.of` для enum'ов). Повторяющиеся наборы статусов (`CONFIRMED || PARTNER_INVITED || PAIR_REGISTERED`) — одна именованная константа `Set<RegistrationStatus>` (лучше — метод на самом enum: `status.isActive()`), а не цепочка `||` в каждом месте.

---

## 3. Данные: `record`, DTO, `Map<String, Object>`

**3.1. Новые DTO, request/response-модели, value-объекты и результаты методов — `record`.** Не `@Data`-класс, не `Map<String, Object>`. Валидация (`@NotNull`, `@Size`…) вешается на компоненты record. Если нужна нормализация — компактный конструктор.

```java
public record TeamSwapRequest(@NotNull Long matchId, @Min(1) @Max(2) int slot, @NotNull Long teamId) {}
```

Исключение: DTO, которые Thymeleaf-форма биндит через `th:object` и сеттеры (`@ModelAttribute` с пошаговым заполнением) — пока остаются классами; MapStruct с record работает (через конструктор).

**3.2. `Map<String, Object>` как ответ API или как "сумка" параметров — запрещено в новом коде.** Ответ REST — типизированный record; `ResponseEntity<?>`/`ResponseEntity<Map<String, Object>>` → `ResponseEntity<XxxResponse>`. Ошибки — через `@ExceptionHandler`/`ProblemDetail` (см. §5), а не `Map.of("error", e.getMessage())`. `Map<String, Object>` допустим только там, где API фреймворка требует map (claims JWT, атрибуты OAuth2, `Model`).

**3.3. Методы, возвращающие несколько значений, — вложенный `record`** (`MatchTeamChangeResult`, `GenerationResult` — правильные примеры, так и продолжать), не массив и не `Map`.

**3.4. JPA-сущности — без `@Data`.** `@Data` генерирует `equals/hashCode/toString` по всем полям, включая ленивые связи: `LazyInitializationException` в `toString`, рекурсия на двунаправленных связях, нестабильный `hashCode` в `Set`. На entity — `@Getter @Setter @NoArgsConstructor` (+ `@Builder`/`@AllArgsConstructor` при необходимости), `equals/hashCode` — по `id`, вручную, если нужны; `@ToString.Exclude` на связях. Entity — не record (JPA требует мутабельности и no-args-конструктора).

**3.5. Lombok — по-прежнему стиль проекта:** `@RequiredArgsConstructor` + `private final` для зависимостей, `@Slf4j` для логгера. `@Autowired` на полях — нет (в коде сейчас 0, держать так). `LoggerFactory.getLogger` — только если класс не может использовать Lombok.

---

## 4. Управляющие конструкции

**4.1. `switch` — только выражения со стрелками.** Старый `case X: ... break;` не использовать. Switch по enum без `default`, если покрыты все значения, — компилятор проверит полноту для switch-выражения.

```java
String label = switch (stage) {
    case ROUND_OF_16 -> "1/8";
    case QUARTER_FINAL -> "1/4";
    case SEMI_FINAL -> "1/2";
    case FINAL -> "Final";
};
```

**4.2. Поведение, зависящее от enum, — в самом enum,** а не в `if/else`/`switch`, размноженных по сервисам (`TournamentType`, `RegistrationStatus`: `isActive()`, `occupiesSlot()`, `requiresPartner()`).

**4.3. Pattern matching для `instanceof`:** `if (principal instanceof Owner owner)` вместо `instanceof` + каст.

**4.4. Ранний выход вместо вложенности.** Guard-clause (`if (...) return/throw`) в начале метода; не больше 2 уровней вложенности (`CODE_REVIEW.md` §5). Однострочный `if (...) continue;` без фигурных скобок — нет, скобки всегда.

**4.5. Text blocks** для многострочных SQL (`@Query`, JDBC в `TestTournamentController`), JSON в тестах, HTML-фрагментов — вместо конкатенации `"..." + "..."`.

**4.6. `var`** — для локальных переменных, когда тип очевиден из правой части (`var teams = teamRepository.findByTournamentId(id);`, `var dto = new TeamDto(...)`). Не использовать, когда тип неочевиден (`var x = service.process(y)`) и для примитивов/литералов.

**4.7. Форматирование строк** — `"...%s...".formatted(x)` или конкатенация; в логах — только плейсхолдеры `{}`, никогда конкатенация.

---

## 5. Исключения

**5.1. Не ловить `Exception`.** `catch (Exception e)` — 161 место в коде, это главный источник "тихих" багов: глотается всё, включая `NullPointerException` и ошибки программиста, а пользователь видит `e.getMessage()` (часто технический текст на английском). В новом коде ловить только конкретные ожидаемые исключения. Исключение из правила — верхнеуровневые границы, где падать нельзя: `@Scheduled`-задачи, `@Async`, отправка email/Telegram, итерация батча "один упал — остальные продолжают". Там — `catch (Exception e)` с `log.error("...", id, e)` (**с передачей `e` последним аргументом**, чтобы был стектрейс), а не `e.getMessage()`.

**5.2. Контроллеры не оборачивают каждый вызов сервиса в try/catch.** Обработка — централизованно: `GlobalExceptionHandler` (`@ControllerAdvice`) для REST — `ProblemDetail`/типизированный ответ с правильным HTTP-статусом (404 для `ResourceNotFoundException`, 409/422 для `InvalidStateException`, 403 для доступа); для Thymeleaf-форм с flash-сообщением — один общий хелпер/advice, а не копипаста `try { ... ra.addFlashAttribute("success", ...) } catch (Exception e) { ra.addFlashAttribute("error", "Error al ...: " + e.getMessage()) }` в каждом методе.

**5.3. Бизнес-ошибки — свои исключения** из `exception/` (`ResourceNotFoundException`, `InvalidStateException`, `TournamentRegistrationException`), не `new RuntimeException("...")`/`IllegalStateException` с текстом. Пользовательский текст ошибки — через i18n-ключ (код ошибки в исключении → сообщение из `messages_*.properties`), не хардкод на испанском в сервисе.

**5.4. Не превращать ошибку в пустой результат.** `catch (Exception e) { return List.of(); }` маскирует баг — так нельзя (пример: `AdminController.clubsForForm`). Если отсутствие данных — нормальная ситуация, это выражается через `Optional`/пустую коллекцию из сервиса, без try/catch.

---

## 6. Методы, классы, имена

**6.1. Размер.** Метод — до ~30 строк, класс-сервис — ориентир до ~500 строк. Сервисы > 1000 строк (`TeamPlayoffService` 2233, `TestTournamentController` 2005, `AmericanoService` 1633, `TournamentService` 1488) не наращивать: новая логика — в отдельный компонент по ответственности (`*Engine`, `*Calculator`, `*Policy`, как уже сделано с `PlayoffMatchingEngine`, `QualificationPairing`).

**6.2. Чистые функции отдельно от I/O.** Вычисления (посев, очки, рейтинг, позиции в листе) — в классах без репозиториев, принимающих и возвращающих данные (легко тестировать unit-тестом). Сервис только загружает данные, вызывает чистую функцию, сохраняет результат.

**6.3. Без FQN в коде.** `com.padle.core.padelcoreservice.dto.ClubDto` внутри метода — нет, только `import`.

**6.4. Язык.** Идентификаторы — как в домене (испанские поля entity), **комментарии и логи — на русском** (`CLAUDE.md`). Не писать комментарии/логи на испанском (`log.info("Viendo partidos del torneo...")`, `// comprobar también al compañero`) — это встречается в коде конвейера, так нельзя. Строки для пользователя — только через i18n, не хардкод в Java.

**6.5. Комментарии** объясняют *почему*, а не *что*. Ссылка на задачу (`// LFPT-357: ...`) — когда без неё неясна причина неочевидного решения.

**6.6. Магические строки/числа** — в константы или enum (`"redirect:/tournaments/team-playoff/admin/"` повторяется — один приватный метод `redirectToAdmin(id)`).

---

## 7. Spring-специфика

- `@Transactional(readOnly = true)` на классе сервиса, `@Transactional` на write-методах (как сейчас). Не вешать `@Transactional` на контроллеры и private-методы (не работает через прокси — та же ловушка, что self-invocation, `CODE_REVIEW.md` §6).
- Конфигурация с несколькими ключами — `@ConfigurationProperties` + `record`, а не россыпь `@Value` (и `@Value` никогда для YAML-списков — `CODE_REVIEW.md` §6).
- Маппинг entity ↔ DTO — только MapStruct (`mapper/`); MapStruct умеет маппить в record.
- Repository: derived-запросы и `exists…`/`count…` вместо загрузки списка и `.size()`/`.isEmpty()` в Java; `Optional<T>` как возвращаемый тип для единичных поисков.

---

## 8. Тесты

- AssertJ (`assertThat(...)`) — fluent-стиль; `assertThat(optional).contains(x)` / `.isEmpty()`, `assertThatThrownBy(...)` для исключений.
- Чистые функции (§6.2) покрываются unit-тестами без Spring-контекста — это быстрее и не требует Postgres.
- Тестовые данные — builder'ы/фабрики, многострочный JSON — text blocks.

---

## 9. Правило для рефакторинга существующего кода ("правило бойскаута")

- **Новый код** — строго по этому документу.
- **Изменяемый метод** в рамках фичи/фикса — приводится к этим правилам, *если это не раздувает diff за пределы задачи*: метод, который ты и так переписываешь, — переписать в современном стиле; соседние методы, которые задача не трогает, — не трогать (это scope creep, `CODE_REVIEW.md` §1).
- **Массовый рефакторинг** — только отдельными задачами `tech-debt` (по одному сервису/области за раз), без изменения поведения, с прогоном `./mvnw verify` и тестом на поведение до начала рефакторинга, если покрытия нет.

---

## 10. Чек-лист перед коммитом (добавляется к `CODE_REVIEW.md` §7)

В новом/изменённом коде нет:
- [ ] `orElse(null)`, `Optional.get()`, `isPresent()` + `get()`; `return null` (кроме контрактов фреймворка с комментарием);
- [ ] `catch (Exception e)` вне верхнеуровневых границ (§5.1); `log.error(..., e.getMessage())` вместо передачи `e`;
- [ ] `try/catch` с flash-сообщением в каждом методе контроллера (§5.2);
- [ ] `new ArrayList<>()` + `for` + `add` там, где это `stream().filter().map().toList()`; `Collectors.toList()`;
- [ ] `Map<String, Object>` / `ResponseEntity<?>` как DTO; новый `@Data`-DTO вместо `record`; `@Data` на entity;
- [ ] `case X:` с `break`; `instanceof` + каст; цепочки `status == A || status == B || ...`;
- [ ] FQN-типов в теле метода; комментариев/логов на испанском; хардкода пользовательских строк;
- [ ] фич Java 21 (§0) — не скомпилируется на `release 17`.
