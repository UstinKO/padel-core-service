# LFPT-0598: Оркестратор автомержа — закрывать связанные issue после мержа

## Статус
done

## Источник
[GitHub issue #598](https://github.com/UstinKO/padel-core-service/issues/598). Найдено на пилоте #582 (задача Telegram #5114).

## Контекст / зачем
PR, влитый оркестратором от имени `GITHUB_TOKEN`, не закрывает issue по `Closes #N`, хотя связь распознана (`closingIssuesReferences`): #594 → #566, #596 → #547, #584 → #580 остались открытыми.

## Связь с MASTER.md
Не затрагивает.

## Требования
### Функциональные
1. Job `merge` в `pipeline-merge.yml` после `gh pr merge` закрывает каждую issue из `closingIssuesReferences` с комментарием «Реализовано в #PR».
2. Права job — `issues: write`.

## Вне скоупа
Остальная логика оркестратора.

## Изменения в системе
`.github/workflows/pipeline-merge.yml`.

## Критерии приёмки
- [ ] После автомержа PR с `Closes #N` issue #N закрыта с комментарием.

## Edge cases
- Issue уже закрыта — `gh issue close` не роняет шаг (`|| true`).

## Открытые вопросы
Нет.
