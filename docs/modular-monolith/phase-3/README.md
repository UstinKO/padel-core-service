# Продолжение миграции: вычислительные движки

King of Court #1279 включён через PR #1281. Следующий согласованный срез — [расписание Americano #1332](americano-extraction.md): совместный перенос движка/валидатора и их исходных тестов в engines. В библиотеке теперь четыре вычислительных класса и девять публичных вложенных типов. Ниже сохранён исторический отчёт King of Court; его цифры и ограничения относятся к проверке 23.11.2026.

## Исторический перенос King of Court

Задача [#1279](https://github.com/UstinKO/padel-core-service/issues/1279) следует за принятой #1201 / PR #1240. [Спецификация](../../../specs/features/LFPT-1279-king-pairing-engine-module.md). Родитель — #1041; последовательность King of Court → отдельный последующий срез Americano согласована владельцем.

## Реализация

KingOfCourtPairingEngine и его 13 исходных unit-тестов перенесены в существующий `padel-tournament-engines`. Оба файла байтово совпадают с исходным master; package `com.padle.core.padelcoreservice.service`, публичные статические методы, вложенные record, приоритеты, порядок и Random сохранены. KingOfCourtService и его nextRound-тест остаются в корневом src, компилируемом bootstrap. Сервисная логика, транзакции, history/дополнительные очки, HTTP/WS и пользовательские строки не меняются.

В engines теперь два вычислительных класса: PlayoffMatchingEngine и KingOfCourtPairingEngine. Только Playoff имеет прежний Spring bean в TournamentEngineConfig; статическая утилита King of Court не получает bean или дополнительную конфигурацию. Существующие зависимость bootstrap → engines, Docker COPY и DevTools restart.include охватывают новый класс без изменения POM или путей доставки.

ArchUnit разрешает JDK и фактически импортированные production-классы библиотеки, учитывая базовый тип массива enum. Разрешения на весь legacy service-пакет нет. Отрицательный пример в том же пакете проверяет, что такая зависимость отклоняется.

Проверка boot jar требует одну обычную библиотеку engines, оба движка и семь публичных вложенных типов. Основные и любые вложенные shadow-классы в BOOT-INF/classes запрещены. Проверяются Java 17 и отсутствие Spring/JPA. Семь Python-тестов включают отсутствие любого движка/вложенного типа, shadow основного/вложенного класса, неверный bytecode и ссылку на framework.

## Проверки и воспроизведение

Автономные unit/ArchUnit: `./mvnw -pl padel-tournament-engines -am test` — без Spring/БД. Полная проверка приложения: корневой `./mvnw verify` с отдельной PostgreSQL и frontend. Артефакт: `python3 .github/bin/check-engine-artifact.py`; отрицательные проверки: `python3 -m unittest discover -s .github/bin -p test_engine_artifact.py`.

`tests/browser/modular-pilot/king-engine.mjs` принимает путь к временному JSON с origin, gatewayPassword, password и tournamentId собственного preview. Файл создаётся mode 0600 в игнорируемом runtime и удаляется после прогона. Подготовка использует только Preview 0 и восемь синтетических игроков этого контура; внешние данные не используются. Скрипт выполняет действующий initialize API, вводит два результата через UI, запускает следующий раунд, проверяет отсутствие немедленных повторов партнёров, настоящие WS-события, history и шесть desktop/mobile RU/ES/EN страниц с refresh.

Браузер использует два штатных CDN script URL SockJS/Stomp, как существующая матрица модульного пилота; остальные внешние запросы заблокированы, согласие на аналитику отклонено. Соединение с настоящим `/ws` и сообщения `/topic/tournament/{kingId}` не подменяются. Физическое устройство и новый ручной PASS владельца не заявляются.

Общий smoke/репетиция: `python3 automation/ui-preview/playoff-pilot.py smoke --rehearse`. Проверка возврата использует отдельно собранный неизменённый master на той же синтетической БД; схема/Liquibase и сохранённое состояние King of Court сравниваются до/после.

## Границы

Reactor по-прежнему содержит четыре дочерних модуля и один boot jar. Americano, остальные application-сценарии, persistence/Liquibase, web/integrations и Swarm не выделяются. Исходные принятые ограничения первой и второй фаз сохраняются.

## Результат 23.11.2026

[Безопасная сводка](qa-2026-11-23.json). Корневой `clean verify` с frontend на Java 21 / release 17 и отдельной PostgreSQL: **1226 тестов**, 0 failures/errors, 2 штатных пропуска. Engines исполняет 24 теста: 13 исходных King of Court, 9 Playoff и 2 проверки границ. KingOfCourtServiceNextRoundTest остаётся в bootstrap и проходит. Три artifact-guard прошли; Python: 27 GitHub (в том числе 7 engine guard), 48 relay, 5 worker (1 Linux-only пропуск), 12 preview и 22 release. Docs-drift и agent-migration — 0 расхождений.

Итоговый локальный jar SHA-256 `399f1019231be3a3abeda3fcaa2e303a337f229d45ff0ba845d76f9ca39257a3` собран до коммита из рабочей копии master `d82e7e6871dd06e3cd0622dd12ee56c8da5fade8` с изменением размещения классов. Хеши двух неизменённых исходных файлов и проверочных исходников приведены в сводке; этот локальный sourceRevision не выдаётся за будущий SHA коммита PR. CI опубликованного коммита пересобирает и проверяет его отдельно.

На настоящем jar initialize API создал проведение с 8 игроками и 2 кортами. Через UI введены два результата, начат второй раунд: немедленных повторов партнёров нет. Получены 6 настоящих WS-сообщений, JavaScript errors — 0; шесть read/refresh страниц 1440/390 × RU/ES/EN и history прошли. Первая попытка наблюдателя с локальной заменой CDN-библиотек не установила WS; итоговый прогон использует два штатных CDN script URL, как существующий пилот, без замены протокола или ответов приложения.

Изолированно собранный неизменённый master имеет jar SHA-256 `d42c04da78e67c08502b19ea8d7212f4a35bb93f22c9a4fbc3ab82bb037f296d`. Переключение на него и native rollback сохранили полное состояние King of Court и отпечаток колонок схемы/журнала Liquibase. Общий legacy/API/React/Expo/session/CSRF/роли/участие/JWT smoke и репетиция выпуска/отката прошли; состояние King of Court до/после совпало. JWT login/me/events — 200, anonymous me — 401. Expo сохранил обычный preview Basic 401, фактический API 200 и отрисовку через прежний QA-адаптер. Репетиция проверила пять snapshots, старый lazy chunk, ошибки chunk/readiness и восстановление журнала.

Повтор initialize уже запущенного турнира дал 500 и на исходном master, и после переноса; состояние при этом сохранилось. Это отдельный существующий [дефект #1280](https://github.com/UstinKO/padel-core-service/issues/1280), его причина и исправление не включаются в перенос. Новый ручной PASS владельца, физические телефоны и новое измерение перекрытия concurrent не заявляются. Runtime, доступы и полные state/HTTP JSON остаются локально.
