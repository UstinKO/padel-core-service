"""Тесты разбора входящих сообщений релея (без сети)."""
import unittest

import relay

RICH = {
    "blocks": [
        {"type": "heading", "size": 2, "text": "Задача: email-уведомления"},
        {"type": "paragraph", "text": ["Нужно ", {"type": "bold", "text": "автоматически"}, " отправлять письмо."]},
        {"type": "list", "items": [
            {"label": "1.", "blocks": [{"type": "paragraph", "text": "Участие отменено."}]},
            {"label": "2.", "blocks": [{"type": "paragraph", "text": "Переведены в резерв."}]},
        ]},
        {"type": "blockquote", "blocks": [{"type": "paragraph", "text": "Клуб: {club}"}]},
        {"type": "table", "cells": [[{"text": "Сценарий"}, {"text": "Письмо"}], [{"text": "Отмена"}, {"text": "Да"}]]},
        {"type": "divider"},
        {"type": "photo", "photo": [{"file_id": "x", "width": 10}], "caption": {"text": "скриншот"}},
    ]
}


def message(**extra):
    base = {"message_id": 1, "chat": {"id": -1}, "from": {"id": 7}}
    base.update(extra)
    return base


class BuildMessageFragmentTest(unittest.TestCase):
    def test_rich_message_without_text_becomes_fragment(self):
        fragment = relay.build_message_fragment(None, message(rich_message=RICH))
        self.assertIsNotNone(fragment)
        for expected in ("Задача: email-уведомления", "Нужно автоматически отправлять письмо.",
                         "1. Участие отменено.", "2. Переведены в резерв.", "Клуб: {club}",
                         "Сценарий | Письмо", "Отмена | Да", "скриншот"):
            self.assertIn(expected, fragment)
        self.assertIn("photo", fragment.split("\n", 1)[0])

    def test_plain_text_unchanged(self):
        self.assertEqual(relay.build_message_fragment(None, message(text="привет")), "привет")

    def test_empty_rich_message_is_not_a_task(self):
        self.assertIsNone(relay.build_message_fragment(None, message(rich_message={"blocks": [{"type": "divider"}]})))

    def test_forward_from_hidden_user_is_marked(self):
        fragment = relay.build_message_fragment(None, message(
            text="задача", forward_origin={"type": "hidden_user", "sender_user_name": "Женя"}))
        self.assertIn("переслано от Женя", fragment)


if __name__ == "__main__":
    unittest.main()
