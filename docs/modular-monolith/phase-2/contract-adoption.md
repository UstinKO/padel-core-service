# Generated Club response — LFPT-1200

После включения application-среза через #1234 два существующих GET `/api/public/clubs` и `/api/public/clubs/{id}` возвращают `com.padle.core.contract.clubs.Club`. Это единственная generated-модель нового library-модуля `padel-api-contract`. Единственный исполняемый jar по-прежнему собирает bootstrap. Каталог, главная, формы MVC, account/player/live DTO и БД этим срезом не переносятся.

## Источник и генерация

Канонический источник клубных paths и схем Club/Problem — [public-clubs.openapi.json](../../../padel-api-contract/src/main/openapi/public-clubs.openapi.json). OAS 3.1.0, имена, типы, required/nullable и описания операций перенесены из действующего discovery JSON без изменения. Problem включён для разрешения ссылок ответов, Java-модель ошибки не генерируется; обработчики продолжают отдавать прежний Spring ProblemDetail.

[Прежний public-discovery.openapi.json](../../frontend-react/contract/public-discovery.openapi.json) сохраняет прежний путь и развёрнутые схемы для прямых читателей, frontend и изолированной пробы. Клубные paths/Club/Problem в нём — проверяемое представление канонического источника; остальной discovery пока редактируется там. Самостоятельная правка клубного раздела запрещена: Maven-тест и frontend job PR Check отклоняют расхождение. Для обновления представления:

```sh
python3 .github/bin/sync-public-club-contract.py --write
python3 .github/bin/sync-public-club-contract.py
```

Изменение владения не требует `$ref`-разрешателя от существующего прямого читателя JSON. Начальная проекция совпала с прежним файлом, поэтому его содержимое не изменилось. Остальные OpenAPI JSON и рукописные TS-модели не генерируются заново.

OpenAPI Generator **7.26.0**, `java/restclient`, `modelsToGenerate=Club`, без APIs/supporting files/tests/docs. Jackson 3/Jakarta, Java release 17, `openApiNullable=false`, `useJackson3/useSpringBoot4/useJakartaEe/useBeanValidation/useOneOfInterfaces=true`, `supportUrlQuery=false`, `dateLibrary=java8`, timestamp скрыт. Настройки взяты из [проверенной пробы](generator-probe.md). Малый `templates/beanValidation.mustache` снимает ошибочный `@NotNull` с required-nullable полей; generated Java не редактируется и остаётся только в `target/generated-sources/openapi`.

Required-nullable поля всегда сериализуются как явные ключи с null; присутствие ключа при десериализации не валидируется. Это response двух GET, не разрешение для request/PATCH, секретов, readOnly/writeOnly или live oneOf. Их ограничения из пробы сохраняются.

## Вызовы и переходные типы

| Тип / действие | Владелец |
|---|---|
| PublicClub / ClubReadPort / PublicClubQuery | application, без зависимости на contract |
| JPA Club / ClubRepository / LegacyClubReadAdapter / ClubReadModelMapper | прежний src под bootstrap, до поздней #1051 |
| PublicClubMapper | MapStruct в web-адаптере bootstrap: нейтральный record → generated Club |
| canonicalUrl | тот же base URL без завершающих `/` и `/clubs/{id}`, тип URI в generated DTO, прежняя строка в JSON |
| HTTP статус/headers/ProblemDetail/fieldErrors | прежние контроллеры и обработчики bootstrap |
| SEO клубной SPA | существующий потребитель PublicDiscoveryService; bean getters/URI.toString вместо record accessors |

`PublicClubResponse` удалён после проверки production/test-потребителей: два GET, сервис, MapStruct и мок SEO-теста. Отдельные ClubDto/ClubSummary и entity сохраняются. SEO и JSON не получают дополнительных полей. Фильтр активности, сортировка nombre/id и readOnly proxy по-прежнему принадлежат принятому application-срезу.

Contract не зависит от application/engines/bootstrap/JPA/Spring MVC/Data/Security или HTTP-клиентов. Compile classpath: Jackson 3.2.4 (включая общие annotations 2.22), Jakarta Validation 3.1.1 и Annotation 3.0.0; Hibernate Validator/JUnit/AssertJ — только тесты. Jackson 2 core/databind запрещены общим Enforcer. Собственный Jackson 2 генератора остаётся внутри build-plugin, не в runtime. Application Enforcer/ArchUnit продолжает запрещать contract и Jackson.

## Сборка и проверки

Root reactor содержит четыре дочерних модуля: contract, engines, application, bootstrap. Dockerfile копирует POM и исходники contract до соответствующих Maven-фаз; Python для production-сборки не требуется. PR Check сверяет канонический OpenAPI перед frontend-проверками и проверяет конечный boot jar.

```sh
./mvnw -B verify
python3 .github/bin/check-contract-artifact.py
python3 .github/bin/verify-club-generation.py
```

Artifact guard требует ровно одну contract library, только Club.class, bytecode Java 17 и отсутствие shadow-классов/ссылок на запрещённые слои. Отрицательные тесты проверяют shadow copy, отсутствие библиотеки, новую версию bytecode и запрещённые ссылки. Чистая повторная Maven-генерация сравнивается с предыдущей по SHA-256 Java-исходника.

Модульные проверки фиксируют все девять полей, Unicode, null и нормализацию отсутствующих nullable-полей в явные ключи, Jakarta required/non-nullable и точную проекцию OpenAPI. Существующие PublicDiscoveryIntegrationTest, PublicClubApplicationIntegrationTest и SEO/PublicEventIntegrationTest проходят через реальные endpoint/MapStruct/Jackson и отдельную PostgreSQL. Финальные результаты API/browser/общего smoke/отката записываются в машинной сводке рядом с этим документом; независимая приёмка #1201 остаётся следующим шагом.
