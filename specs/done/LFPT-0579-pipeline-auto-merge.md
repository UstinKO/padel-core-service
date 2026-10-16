# LFPT-0579: Автомерж PR конвейера — оркестратор pipeline-merge в GitHub Actions

## Статус
done

## Источник
[GitHub issue #579](https://github.com/UstinKO/padel-core-service/issues/579). Прямая постановка архитектора, клиентского запроса нет. Этап 1 дорожной карты «Автономный конвейер: от Telegram до прода» (локальная `docs/pipeline-autonomy.md`, решения пользователя от 2026-10-16: ревью человеком убирается, ничего не держится на ручном ревью).

## Контекст / зачем
Каждый PR конвейера ждёт, пока пользователь его вольёт. Пользователь убирает себя из процесса: PR, который конвейер довёл до зелёного pr-check, должен вливаться и деплоиться автоматически. Порядок важен: пока фронт на Thymeleaf, фронт-PR без задеплоенного бэк-PR роняет приложение. Задачи могут идти параллельно, поэтому мерж и деплой должны быть строго последовательными.

## Связь с MASTER.md
Не затрагивает бизнес-логику продукта. Меняет процесс разработки (`.claude/GIT_WORKFLOW.md`, `develop-feature.md`) — правила протокола для роутины вводит этап 2 (#580).

## Требования

### Функциональные
1. **Метки** (создаются один раз): `pipeline`, `pipeline:ready`, `pipeline:needs-input`, `pipeline:failed`, `pipeline:conflict`, `pipeline:deploy-failed`, `pipeline:halt`.
2. **`pr-check.yml` переиспользуемый**: добавляется `workflow_call` с входом `ref`; на `pull_request` поведение не меняется.
3. **Оркестратор `.github/workflows/pipeline-merge.yml`**:
   - триггеры: `workflow_run` (`PR Check`, `Deploy to Production`, completed), `pull_request: labeled`, `schedule` каждые 15 минут, `workflow_dispatch` (вход `dry_run`);
   - `concurrency: pipeline-merge` без отмены текущего прогона;
   - за один прогон вливает не больше одного PR.
4. **Выбор кандидата** (`.github/bin/pipeline-select.sh`), по порядку:
   - есть открытый issue с меткой `pipeline:halt` → ничего не делать;
   - идёт или стоит в очереди прогон `Deploy to Production` → ничего не делать (оркестратор сработает по его завершении);
   - кандидаты — открытые не-draft PR в `master` с метками `pipeline` и `pipeline:ready`, без `pipeline:conflict` и `pipeline:deploy-failed`, от старых к новым;
   - у кандидата успешный check `build` (PR Check) на текущем head-коммите;
   - строка `Depends-On: #M` в описании → PR #M влит, и, если #M менял не только `specs/**`, `.claude/**`, `docs/**`, его прогон `Deploy to Production` успешен;
   - кандидат с конфликтом (`mergeable = CONFLICTING`) получает `pipeline:conflict` и комментарий, выбор идёт дальше.
5. **Проверка на свежем master**: перед мержем pr-check повторно прогоняется на `refs/pull/N/merge`. Упал → `pipeline:conflict` + комментарий со ссылкой на прогон, мержа нет.
6. **Мерж**: если master не сдвинулся с момента выбора — `gh pr merge --squash` с заголовком «<заголовок PR> (#N)» и `--match-head-commit`. Затем:
   - PR менял что-то кроме `specs/**`, `.claude/**`, `docs/**` → `gh workflow run deploy.yml -f pr=N`;
   - иначе деплой не нужен → оркестратор перезапускает себя (`workflow_dispatch`), чтобы взять следующий PR.
7. **Сбой деплоя**: по завершении `Deploy to Production` с `failure` для деплоя PR конвейера (`Deploy PR #N` в имени прогона) → PR получает `pipeline:deploy-failed`, открывается issue `pipeline:halt` со ссылкой на прогон (если открытого ещё нет).
8. **`deploy.yml`**: `workflow_dispatch` с входом `pr`; `run-name` «Deploy PR #N» при dispatch; `concurrency: deploy-production` без отмены; `archive-specs` берёт заголовок задеплоенного коммита через API, а не из `head_commit` (у dispatch его нет).

### Нефункциональные
- Права `GITHUB_TOKEN` оркестратора — минимально нужные на уровне job: `contents`, `pull-requests`, `issues` на запись, `actions` на запись (dispatch), `checks` на чтение.
- Всё, что оркестратор делает с PR, сопровождается комментарием с причиной и ссылкой на прогон.
- `dry_run` печатает решение и ничего не меняет.

## Вне скоупа
- Протокол роутины (маркеры `Pipeline-Task`, `Depends-On`, постановка `pipeline:ready`) — #580.
- Уведомления в Telegram — движок задач, #581. Оркестратор в Telegram не пишет и роутину не запускает.
- Автоматическая починка конфликтов — перезапуск роутины по `pipeline:conflict` делает движок (#581).
- Branch protection / rulesets для master — не вводятся, ручной мерж людьми остаётся возможен.

## Изменения в системе

### API
Нет.

### БД
Нет.

### UI
Нет. Изменения только в `.github/`:
- новый `.github/workflows/pipeline-merge.yml`;
- новый `.github/bin/pipeline-select.sh`;
- `.github/workflows/pr-check.yml` — `workflow_call`;
- `.github/workflows/deploy.yml` — `workflow_dispatch`, `run-name`, `concurrency`, заголовок коммита для `archive-specs`.

## Критерии приёмки
- [ ] PR с `pipeline` + `pipeline:ready` и зелёным `build` вливается без человека; если он меняет код — стартует `Deploy to Production` с именем «Deploy PR #N».
- [ ] PR только в `specs/**`/`.claude/**`/`docs/**` вливается без деплоя, после чего оркестратор сразу берёт следующий PR.
- [ ] PR с `Depends-On: #M` не вливается, пока #M не влит и его деплой не успешен; после успешного деплоя #M вливается сам.
- [ ] Два готовых PR вливаются по одному: второй — только после завершения деплоя первого.
- [ ] Открытый issue `pipeline:halt` останавливает автомерж; после его закрытия автомерж продолжается.
- [ ] PR без `pipeline:ready` (обычный ручной процесс) оркестратор не трогает.
- [ ] Упавший verify на свежем master → `pipeline:conflict` + комментарий, мержа нет.
- [ ] Упавший деплой PR конвейера → `pipeline:deploy-failed` + issue `pipeline:halt`.
- [ ] `archive-specs` срабатывает и на деплое по `push`, и на деплое по `workflow_dispatch`.
- [ ] `workflow_dispatch` с `dry_run=true` печатает выбранный PR и причины пропуска остальных, ничего не меняя.

## Edge cases
- Человек вливает PR вручную, пока оркестратор проверяет другой: master сдвигается → оркестратор не мержит (проверка SHA master), следующий прогон перепроверит на новом master.
- Деплой PR #M упал, а фронт-PR с `Depends-On: #M` готов → фронт не вливается (зависимость не выполнена), плюс halt.
- `refs/pull/N/merge` ещё не пересчитан GitHub (`mergeable = UNKNOWN`) → кандидат пропускается до следующего прогона.
- Несколько pending-прогонов оркестратора: GitHub оставляет один ожидающий в группе concurrency, остальные отменяются — не страшно, каждый прогон пересматривает всё состояние заново.
- `archive-specs` после деплоя PR #N вливает свой PR от `github-actions[bot]` — этот PR без метки `pipeline`, оркестратор его не трогает.

## Открытые вопросы
Нет. Технические решения:
1. **Повторный verify перед каждым мержем, а не «branch up to date»**: обновление ветки от имени `GITHUB_TOKEN` не перезапускает pr-check, поэтому проверка на свежем master делается прямо в оркестраторе (переиспользуемый pr-check).
2. **Деплой через `workflow_dispatch`**: мерж от `GITHUB_TOKEN` не порождает `push`-прогон `deploy.yml`.
3. **Строго один мерж+деплой за раз**: даёт и порядок «бэк → фронт», и отсутствие гонок; цена — около 10 минут на PR.
