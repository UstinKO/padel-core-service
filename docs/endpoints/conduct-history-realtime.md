# Обновление Team Playoff после восстановления истории

`TournamentConductHistory.restore` сохраняет контрольную точку и восстановленное состояние в одной транзакции. После успешного восстановления Team Playoff вызывает `WebSocketService.notifyTeamPlayoffStateUpdated`.

- Legacy получает `WebSocketMessage` с `type=STATE_UPDATED`, `tournamentId`, timestamp и пустым data в `/topic/team-playoff/{tournamentId}`. Доставка использует существующий `AfterCommit`: до commit и после rollback сообщения нет.
- React получает прежний безопасный invalidation в `/topic/public/live/{tournamentId}`. Несколько вызовов `PublicLiveNotifications.changed` в транзакции объединяются в один refetch-сигнал.
- Пары, результаты, доступ к истории и структура снимка не меняются. Другие форматы в legacy-тему Team Playoff не отправляются.

Legacy-слушатель по обновлению планирует штатный автоматический reload; ручная перезагрузка зрителя не требуется. React перечитывает snapshot. Сбой брокера после commit не отменяет сохранённую версию; повторное открытие читает актуальный серверный снимок.

Целевая проверка: `TournamentConductHistoryIntegrationTest` фиксирует commit/rollback настоящей PostgreSQL и сообщения обеих тем. Конечные экраны дополнительно проверяются двумя браузерными зрителями в отдельном preview; один серверный snapshot не заменяет эту проверку. Источник дефекта — [#1195](https://github.com/UstinKO/padel-core-service/issues/1195).
