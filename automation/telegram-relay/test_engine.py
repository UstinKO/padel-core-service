"""Тесты движка задач (LFPT-0581): python3 -m unittest test_engine -v"""

import json
import os
import tempfile
import unittest
import urllib.parse

import engine as E


class FakeTelegram:
    def __init__(self):
        self.next_id = 1000
        self.sent = []      # (id, text, reply_to, markup)
        self.edits = {}     # id -> (text, markup)

    def send(self, text, reply_markup=None, reply_to=None):
        self.next_id += 1
        self.sent.append((self.next_id, text, reply_to, reply_markup))
        return self.next_id

    def edit(self, message_id, text, reply_markup=None):
        self.edits[message_id] = (text, reply_markup)


class FakeGH:
    def __init__(self):
        self.items = {}     # task_id -> {number: item}
        self.pulls = {}     # number -> pull json
        self.files = {}     # number -> needs deploy
        self.runs = []
        self.comment_map = {}
        self.replies = {}   # task_id -> [(comment_id, created_ts, text)]
        self.closing = {}   # issue -> [PR без Pipeline-Task, закрывающий её]
        self.check = {}     # PR -> success | pending | failure | none

    def issue(self, task, number, labels=(), state="open"):
        self.items.setdefault(task, {})[number] = {
            "number": number, "title": f"LFPT-{number:04d}: issue", "html_url": f"https://gh/{number}",
            "labels": [{"name": l} for l in labels], "state": state, "body": f"text\nPipeline-Task: {task}\n",
        }

    def pr(self, task, number, closes, labels=("pipeline",), merged=False, deploy=True, depends=None):
        body = f"Closes #{closes}\nPipeline-Task: {task}\n" + (f"Depends-On: #{depends}\n" if depends else "")
        self.items.setdefault(task, {})[number] = {
            "number": number, "title": f"LFPT-{closes:04d}: PR", "html_url": f"https://gh/pull/{number}",
            "labels": [{"name": l} for l in labels], "state": "closed" if merged else "open",
            "body": body, "pull_request": {},
        }
        self.pulls[number] = {"merged": merged, "state": "closed" if merged else "open", "merge_commit_sha": f"sha{number}",
                              "head": {"sha": f"head{number}"}}
        self.files[number] = deploy

    def label(self, task, number, *labels):
        self.items[task][number]["labels"] = [{"name": l} for l in labels]

    def merge(self, task, number):
        self.items[task][number]["state"] = "closed"
        self.pulls[number]["merged"] = True

    def deploy(self, pr, conclusion="success", status="completed"):
        self.runs.append({"display_title": f"Deploy PR #{pr}", "head_sha": "", "status": status,
                          "conclusion": conclusion, "created_at": f"2026-10-16T0{len(self.runs)}:00:00Z",
                          "html_url": f"https://gh/runs/{pr}"})

    # интерфейс GitHub
    def search_task(self, task_id):
        return [dict(v) for v in self.items.get(task_id, {}).values()]

    def checks(self, sha):
        return self.check.get(int(sha.removeprefix("head") or 0), "none")

    def pull(self, number):
        return self.pulls[number]

    def needs_deploy(self, number):
        return self.files[number]

    def deploy_runs(self):
        return list(self.runs)

    def comments(self, number):
        return self.comment_map.get(number, [])

    def unmarked_pr(self, number, closes, merged=True):
        self.closing.setdefault(closes, []).append({
            "number": number, "title": f"LFPT-{closes:04d}: PR", "html_url": f"https://gh/pull/{number}",
            "labels": [{"name": "pipeline"}], "state": "closed" if merged else "open",
            "body": f"Closes #{closes}\n", "pull_request": {},
        })
        self.pulls[number] = {"merged": merged, "state": "closed" if merged else "open", "merge_commit_sha": f"sha{number}"}
        self.files[number] = True

    def prs_closing(self, issue_number):
        return [dict(v) for v in self.closing.get(issue_number, [])]

    def find_replies(self, task_id):
        return list(self.replies.get(task_id, []))

    def halt_issues(self):
        return []


class EngineTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.db = os.path.join(self.tmp, "engine.db")
        self.clock = [1_000_000.0]
        self.fires = []
        self.fire_result = ("ok", {"session_url": "https://claude.ai/code/s"}, None)
        self.paused = False
        self.tg = FakeTelegram()
        self.gh = FakeGH()
        self.engine = self.make_engine()

    def make_engine(self):
        cfg = E.EngineConfig(repo="o/r", max_parallel=2, stall_hours=3, poll_interval=0, db_path=self.db)
        return E.Engine(
            cfg, E.Store(self.db), E.Telegram(send=self.tg.send, edit=self.tg.edit), self.gh,
            fire=self.fire, is_paused=lambda: self.paused, now=lambda: self.clock[0],
        )

    def fire(self, text):
        self.fires.append(text)
        return self.fire_result

    def status(self, tid):
        return self.engine.store.get(tid)["status"]

    def tick(self, minutes=1):
        self.clock[0] += minutes * 60
        self.engine.tick()

    def replies_to(self, tid):
        return [text for _, text, reply_to, _ in self.tg.sent if reply_to == tid]

    # ── критерии приёмки ──

    def test_parallel_limit_and_slot_release(self):
        a = self.engine.submit("architect", "u", "задача A")
        b = self.engine.submit("architect", "u", "задача B")
        c = self.engine.submit("customer", "e", "задача C")
        self.assertEqual([self.status(a), self.status(b), self.status(c)], [E.RUNNING, E.RUNNING, E.QUEUED])
        self.assertEqual(len(self.fires), 2)
        self.assertIn("позиция 1", self.tg.edits[c][0])

        self.gh.issue(a, 10)
        self.gh.pr(a, 11, closes=10, labels=("pipeline", E.READY))
        self.tick()
        self.assertEqual(self.status(a), E.MERGING)
        self.assertEqual(self.status(c), E.RUNNING, "освободившийся слот отдан задаче из очереди")
        self.assertIn(f"[telegram_launch_message_id: {c}]", self.fires[2])

    def test_rate_limit_retries_without_human(self):
        self.fire_result = ("retry", "примерно через 2 мин", 120)
        t = self.engine.submit("architect", "u", "задача")
        self.assertEqual(self.status(t), E.QUEUED)
        self.fire_result = ("ok", {"session_url": "u"}, None)
        self.tick(1)
        self.assertEqual(len(self.fires), 1, "до Retry-After не повторяем")
        self.tick(2)
        self.assertEqual(self.status(t), E.RUNNING)
        self.assertEqual(len(self.fires), 2)

    def test_fire_error_is_failed_with_restart(self):
        self.fire_result = ("error", "500 api_error", None)
        t = self.engine.submit("architect", "u", "задача")
        self.assertEqual(self.status(t), E.FAILED)
        self.assertEqual(self.tg.edits[t][1], E.RESTART_BTN(t))
        self.fire_result = ("ok", {"session_url": "u"}, None)
        self.engine.restart(t)
        self.assertEqual(self.status(t), E.RUNNING)
        self.assertEqual(self.fires[-1], self.fires[0], "без следов в GitHub перезапуск — исходным текстом")

    def test_restart_keeps_queue_and_active(self):
        a = self.engine.submit("architect", "u", "A")
        b = self.engine.submit("architect", "u", "B")
        c = self.engine.submit("architect", "u", "C")
        self.engine = self.make_engine()   # «рестарт relay»
        self.assertEqual([self.status(a), self.status(b), self.status(c)], [E.RUNNING, E.RUNNING, E.QUEUED])
        self.tick()
        self.assertEqual(len(self.fires), 2, "после рестарта лимит соблюдается")

    def test_full_lifecycle_backend_frontend_to_done(self):
        t = self.engine.submit("customer", "e", "бэк + фронт")
        self.gh.issue(t, 20)
        self.gh.issue(t, 21)
        self.gh.pr(t, 22, closes=20, labels=("pipeline", E.READY))
        self.tick()
        self.assertEqual(self.status(t), E.RUNNING, "фронт-issue без PR — роутина ещё работает, слот занят")

        self.gh.pr(t, 23, closes=21, labels=("pipeline",), depends=22)
        self.tick()
        self.assertEqual(self.status(t), E.RUNNING)
        self.gh.label(t, 23, "pipeline", E.READY)
        self.tick()
        self.assertEqual(self.status(t), E.MERGING)

        self.gh.merge(t, 22)
        self.gh.deploy(22, status="in_progress")
        self.tick()
        self.assertEqual(self.status(t), E.MERGING)
        self.assertIn("🚀", self.tg.edits[t][0])
        self.gh.runs[-1].update(status="completed", conclusion="success")
        self.gh.merge(t, 23)
        self.gh.deploy(23)
        self.tick()
        self.assertEqual(self.status(t), E.DONE)
        final = self.replies_to(t)
        self.assertEqual(len(final), 1)
        self.assertIn("реализована и влита на прод — можно проверять", final[0])
        self.tick()
        self.assertEqual(len(self.replies_to(t)), 1, "итог — один раз")

    def test_docs_only_pr_done_without_deploy(self):
        t = self.engine.submit("architect", "u", "доки")
        self.gh.issue(t, 30)
        self.gh.pr(t, 31, closes=30, labels=("pipeline", E.READY), merged=True, deploy=False)
        self.tick()
        self.assertEqual(self.status(t), E.DONE)

    def test_questions_and_reply_continuation(self):
        t = self.engine.submit("customer", "e", "задача")
        self.gh.issue(t, 40, labels=("pipeline", E.L_NEEDS_INPUT))
        self.gh.comment_map[40] = [{"id": 7, "body": "🤖 pipeline-questions\n1. Какой цвет?"}]
        self.tick()
        self.assertEqual(self.status(t), E.NEEDS_INPUT)
        q = self.replies_to(t)
        self.assertEqual(len(q), 1)
        self.assertIn("1. Какой цвет?", q[0])
        q_msg = [mid for mid, text, *_ in self.tg.sent if text == q[0]][0]
        self.assertEqual(self.engine.store.task_for_message(q_msg), t, "реплай на вопрос найдёт задачу")

        self.engine.continue_task(t, "customer", "e", "Синий", message_id=5555)
        self.assertEqual(self.status(t), E.RUNNING)
        self.assertIn(f"[продолжение задачи #{t}]", self.fires[-1])
        self.assertIn("Синий", self.fires[-1])
        self.tick()
        self.assertEqual(self.status(t), E.RUNNING, "старая метка needs-input не возвращает задачу назад")
        self.assertEqual(len(self.replies_to(t)), 1, "вопросы не дублируются")

    def test_reply_while_running_does_not_start_second_session(self):
        t = self.engine.submit("architect", "u", "задача")
        answer = self.engine.continue_task(t, "architect", "u", "ещё", message_id=6000)
        self.assertIn("в работе", answer)
        self.assertEqual(len(self.fires), 1)

    def test_cancel_only_queued(self):
        self.engine.submit("architect", "u", "A")
        self.engine.submit("architect", "u", "B")
        c = self.engine.submit("architect", "u", "C")
        self.assertEqual(self.engine.cancel(c), "Задача отменена")
        self.assertEqual(self.status(c), E.CANCELLED)
        self.tick()
        self.assertEqual(len(self.fires), 2)

    def test_conflict_auto_repair_once_then_human(self):
        t = self.engine.submit("architect", "u", "задача")
        self.gh.issue(t, 50)
        self.gh.pr(t, 51, closes=50, labels=("pipeline", E.READY, E.L_CONFLICT))
        self.tick()
        self.assertEqual(self.status(t), E.RUNNING, "автопочинка: роутина перезапущена")
        self.assertIn("pipeline:conflict на #51", self.fires[-1])
        self.tick()
        self.assertEqual(self.status(t), E.RUNNING, "пока следы не изменились — старая метка не считается")

        self.gh.label(t, 51, "pipeline", E.READY, E.L_CONFLICT, "x")  # роутина что-то сделала, но конфликт остался
        self.tick()
        self.assertEqual(self.status(t), E.CONFLICT)
        self.assertEqual(len(self.fires), 2, "вторую автопочинку не запускаем")
        self.assertTrue(any("конфликт при вливании" in r for r in self.replies_to(t)))

    def test_conflict_repair_not_judged_while_fix_checks_run(self):
        """#5384: сессия-починка запушила фикс (pr-check идёт), но метку pipeline:conflict ещё не сняла."""
        t = self.engine.submit("architect", "u", "бэк + фронт")
        self.gh.issue(t, 80)
        self.gh.pr(t, 82, closes=80, labels=("pipeline", E.READY, E.L_CONFLICT))
        self.gh.check[82] = "failure"
        self.tick()
        self.assertEqual((self.status(t), len(self.fires)), (E.RUNNING, 2), "автопочинка запущена")
        self.gh.check[82] = "pending"   # сессия запушила фикс
        self.tick()
        self.gh.check[82] = "success"
        self.tick()
        self.assertEqual(self.status(t), E.RUNNING)
        self.assertFalse(any("автопочинка не помогла" in r for r in self.replies_to(t)))
        self.gh.label(t, 82, "pipeline", E.READY)   # сняла pipeline:conflict
        self.tick()
        self.assertEqual(self.status(t), E.MERGING)

    def test_deploy_failed_notifies(self):
        t = self.engine.submit("architect", "u", "задача")
        self.gh.issue(t, 60)
        self.gh.pr(t, 61, closes=60, labels=("pipeline", E.READY))
        self.gh.merge(t, 61)
        self.gh.deploy(61, conclusion="failure")
        self.tick()
        self.assertEqual(self.status(t), E.DEPLOY_FAILED)
        self.assertTrue(any("деплой упал" in r for r in self.replies_to(t)))

    def test_stalled_without_traces(self):
        """#5304: прогон без issue/PR и без pipeline-reply — «нет движения» через час, а не через STALL_HOURS."""
        t = self.engine.submit("architect", "u", "задача")
        self.tick(30)
        self.assertEqual(self.status(t), E.RUNNING)
        self.tick(31)
        self.assertEqual(self.status(t), E.STALLED)
        self.assertTrue(any("нет движения" in r for r in self.replies_to(t)))

    def test_green_pr_without_ready_is_continued_once(self):
        """#5289/#5317: сессия открыла PR, pr-check зелёный, а pipeline:ready так и не поставлен."""
        t = self.engine.submit("customer", "u", "карточки")
        self.gh.issue(t, 653, labels=("pipeline",))
        self.gh.pr(t, 654, closes=653)
        self.gh.check[654] = "pending"
        self.tick()
        self.gh.check[654] = "success"
        self.tick(10)
        self.assertEqual((self.status(t), len(self.fires)), (E.RUNNING, 1), "сразу после зелёного не дёргаем")
        self.tick(31)
        self.assertEqual(len(self.fires), 2)
        self.assertIn("PR #654", self.fires[-1])
        self.assertIn("pipeline:ready", self.fires[-1])
        self.assertEqual(self.status(t), E.RUNNING)

        self.tick(60)
        self.assertEqual(len(self.fires), 2, "второй раз сами не продолжаем")
        self.tick(3 * 60)
        self.assertEqual(self.status(t), E.STALLED)

        self.gh.label(t, 654, "pipeline", E.READY)
        self.tick()
        self.assertEqual(self.status(t), E.MERGING)

    def test_stalled_green_pr_is_continued(self):
        """Задача уже «нет движения» (как #5317 до выкатки) — продолжаем её, не дожидаясь кнопки."""
        t = self.engine.submit("customer", "u", "карточки")
        self.gh.issue(t, 653, labels=("pipeline",))
        self.gh.pr(t, 654, closes=653)
        self.tick()
        self.gh.check[654] = "success"
        self.engine.store.update(t, status=E.STALLED)
        self.tick(31)
        self.tick(31)
        self.assertEqual(len(self.fires), 2)
        self.assertEqual(self.status(t), E.RUNNING)

    def test_not_continued_when_checks_pending_or_issue_uncovered(self):
        t = self.engine.submit("customer", "u", "бэк + фронт")
        self.gh.issue(t, 70, labels=("pipeline",))
        self.gh.issue(t, 71, labels=("pipeline",))
        self.gh.pr(t, 72, closes=70)
        self.gh.check[72] = "success"
        self.tick()
        self.tick(40)
        self.assertEqual(len(self.fires), 1, "фронт (#71) ещё без PR — сессия может работать")
        snap = E.collect_snapshot(self.gh, t, [])
        self.assertFalse(E.ready_forgotten(snap))
        self.gh.pr(t, 73, closes=71)
        self.gh.check[73] = "pending"
        self.assertFalse(E.ready_forgotten(E.collect_snapshot(self.gh, t, [])))
        self.gh.check[73] = "success"
        self.assertTrue(E.ready_forgotten(E.collect_snapshot(self.gh, t, [])))
        self.gh.label(t, 73, "pipeline", E.L_FAILED)
        self.assertFalse(E.ready_forgotten(E.collect_snapshot(self.gh, t, [])))

    def test_summary_hides_old_stalled(self):
        old = self.engine.submit("customer", "u", "давняя задача")
        self.tick(61)
        self.assertEqual(self.status(old), E.STALLED)
        self.assertIn("давняя", self.engine.queue_summary())
        self.tick(25 * 60)
        self.engine.submit("customer", "u", "свежая задача")
        summary = self.engine.queue_summary()
        self.assertNotIn("давняя", summary)
        self.assertIn("свежая", summary)

    def test_github_checks_summary(self):
        def gh(*runs):
            class GH(E.GitHub):
                def _get(self, path):
                    return {"check_runs": [{"status": s, "conclusion": c} for s, c in runs]}
            return GH("t", "o/r").checks("sha")
        self.assertEqual(gh(("completed", "success"), ("completed", "skipped")), "success")
        self.assertEqual(gh(("completed", "success"), ("in_progress", None)), "pending")
        self.assertEqual(gh(("completed", "success"), ("completed", "failure")), "failure")
        self.assertEqual(gh(("completed", "skipped")), "none")
        self.assertEqual(gh(), "none")

    def test_reply_without_traces_answers_and_frees_slot(self):
        # LFPT-0641: вопрос о статусе — прогон не создаёт issue/PR, а оставляет 🤖 pipeline-reply.
        a = self.engine.submit("customer", "e", "что сейчас происходит?")
        b = self.engine.submit("architect", "u", "задача B")
        c = self.engine.submit("architect", "u", "задача C")
        self.assertEqual(self.status(c), E.QUEUED)
        self.gh.replies[a] = [(77, self.clock[0] + 30, "LFPT-0638 ждёт файлов приложения веткой в репозитории.")]
        self.tick()
        self.assertEqual(self.status(a), E.ANSWERED)
        self.assertTrue(any("ждёт файлов" in r for r in self.replies_to(a)))
        self.assertEqual(self.status(c), E.RUNNING, "слот освобождён сразу, а не через STALL_HOURS")
        self.assertIn("ответ дан", self.tg.edits[a][0])

        # Реплай на ответ — продолжение; старый ответ не закрывает новый запуск.
        reply_mid = [mid for mid, text, rt, _ in self.tg.sent if rt == a][-1]
        self.engine.store.update(c, status=E.DONE)
        self.assertEqual(self.engine.continue_task(a, "customer", "e", "а когда будет?", 9001), "")
        self.assertEqual(self.engine.store.task_for_message(reply_mid), a)
        self.assertEqual(self.status(a), E.RUNNING)
        self.tick()
        self.assertEqual(self.status(a), E.RUNNING)
        self.gh.replies[a].append((78, self.clock[0], "Как только пришлёте файлы."))
        self.tick()
        self.assertEqual(self.status(a), E.ANSWERED)
        self.assertTrue(any("пришлёте файлы" in r for r in self.replies_to(a)))

    def test_new_questions_with_same_label_reach_chat(self):
        # LFPT-0641: продолжение оставило pipeline:needs-input и задало вопрос новым комментарием.
        t = self.engine.submit("customer", "e", "задача")
        self.gh.issue(t, 40, labels=("pipeline", E.L_NEEDS_INPUT))
        self.gh.comment_map[40] = [{"id": 7, "body": "🤖 pipeline-questions\n1. Пришлите файлы."}]
        self.tick()
        self.assertEqual(self.status(t), E.NEEDS_INPUT)
        self.engine.continue_task(t, "customer", "e", "вот ссылка на Drive", 9002)
        self.assertEqual(self.status(t), E.RUNNING)
        self.tick()
        self.assertEqual(self.status(t), E.RUNNING, "метка та же, новых вопросов нет — сессия ещё работает")
        self.gh.comment_map[40].append({"id": 8, "body": "🤖 pipeline-questions\n1. Drive недоступен — нужна ветка."})
        self.tick()
        self.assertEqual(self.status(t), E.NEEDS_INPUT)
        self.assertTrue(any("Drive недоступен" in r for r in self.replies_to(t)))

    def test_summary_skips_batch_headers(self):
        self.engine.submit("customer", "e", "[собрано из 2 сообщений через кнопки Начать/Отправить]\n--- Сообщение 1 ---\n"
                           "[переслано от x]\nПодключить вход через Google\n\n--- Сообщение 2 ---\nещё")
        self.assertIn("— Подключить вход через Google", self.engine.queue_summary())

    def test_github_find_replies_parses_marker(self):
        queries = []

        class GH(E.GitHub):
            def _get(self, path):
                if path.startswith("/search/"):
                    queries.append(urllib.parse.unquote(path))
                    return {"items": [{"number": 638}]}
                return [
                    {"id": 1, "created_at": "2026-10-19T13:09:50Z", "body": "🤖 pipeline-reply 5252\n\nЖдём файлы."},
                    {"id": 2, "created_at": "2026-10-19T13:10:00Z", "body": "🤖 pipeline-reply 52521\nчужая задача"},
                    {"id": 3, "created_at": "2026-10-19T13:11:00Z", "body": "упоминание 🤖 pipeline-reply 5252 в тексте"},
                ]
        found = GH("t", "o/r").find_replies(5252)
        self.assertEqual([(c, t) for c, _, t in found], [(1, "Ждём файлы.")])
        self.assertEqual(found[0][1], E._ts("2026-10-19T13:09:50Z"))
        # без is:issue / is:pull-request поиск GitHub отвечает 422 (#5280)
        self.assertEqual(len(queries), 2)
        self.assertTrue(all(" is:issue " in q or " is:pull-request " in q for q in queries))

    def test_pr_without_marker_found_by_closes(self):
        """#5280: роутина не написала Pipeline-Task в PR #648 — задача всё равно доходит до «влито на прод»."""
        t = self.engine.submit("customer", "u", "сохранять вход")
        self.tick()
        self.gh.issue(t, 646, labels=("pipeline",), state="closed")
        self.gh.unmarked_pr(648, closes=646)
        self.gh.deploy(648)
        self.tick()
        self.assertEqual(self.status(t), E.DONE)
        self.assertIn("648", self.tg.edits[t][0])

    def test_github_prs_closing_filters_by_closes(self):
        class GH(E.GitHub):
            def _get(self, path):
                assert "is:pull-request" in urllib.parse.unquote(path)
                return {"items": [{"number": 648, "body": "Closes #646"}, {"number": 650, "body": "см. #646"},
                                  {"number": 651, "body": "Closes #6460"}]}
        self.assertEqual([p["number"] for p in GH("t", "o/r").prs_closing(646)], [648])

    def test_pause_keeps_queue(self):
        self.paused = True
        t = self.engine.submit("architect", "u", "задача")
        self.assertEqual(self.status(t), E.QUEUED)
        self.assertIn("пауз", self.tg.edits[t][0])
        self.paused = False
        self.tick()
        self.assertEqual(self.status(t), E.RUNNING)

    def test_deploy_state_matching(self):
        runs = [{"display_title": "Deploy PR #58", "status": "completed", "conclusion": "failure", "created_at": "1", "head_sha": "x"},
                {"display_title": "LFPT-1: x (#580)", "status": "completed", "conclusion": "success", "created_at": "2", "head_sha": "y"}]
        self.assertEqual(E.deploy_state_for(580, "", runs)[0], "success")
        self.assertEqual(E.deploy_state_for(58, "", runs)[0], "failure")
        self.assertEqual(E.deploy_state_for(5, "", runs)[0], "none")


if __name__ == "__main__":
    unittest.main()
