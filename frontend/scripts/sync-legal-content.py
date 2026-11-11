#!/usr/bin/env python3
# coding: utf-8
"""Обновляет React-копию утверждённых legal-текстов из действующих шаблонов."""

import json
from html.parser import HTMLParser
from pathlib import Path


class LegalCases(HTMLParser):
    LANGUAGES = {"'ru'": "ru", "'en'": "en", "*": "es"}
    VOID_TAGS = {"br", "hr", "img", "input", "meta", "link"}

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.stack = []
        self.content = {}
        self.active = None

    def handle_starttag(self, tag, attributes):
        attributes = dict(attributes)
        if self.active is None and "th:case" in attributes:
            self.active = self.LANGUAGES[attributes["th:case"]]
            self.stack = [{"tag": "div", "children": []}]
            return
        if self.active is None:
            return
        node = {"tag": tag, "children": []}
        if tag == "a" and "href" in attributes:
            node["href"] = attributes["href"]
        self.stack[-1]["children"].append(node)
        if tag not in self.VOID_TAGS:
            self.stack.append(node)

    def handle_endtag(self, tag):
        if self.active and self.stack and self.stack[-1]["tag"] == tag:
            node = self.stack.pop()
            if not self.stack:
                self.content[self.active] = node["children"]
                self.active = None

    def handle_data(self, data):
        if self.active and data:
            self.stack[-1]["children"].append(data)


def main():
    root = Path(__file__).resolve().parents[2]
    content = {}
    for page in ("terminos", "privacidad", "cookies"):
        parser = LegalCases()
        source = root / "src/main/resources/templates/legal" / f"{page}.html"
        parser.feed(source.read_text(encoding="utf-8"))
        if set(parser.content) != {"es", "ru", "en"}:
            raise ValueError(f"Не найдены все языки юридической страницы {page}")
        content[page] = parser.content
    target = root / "frontend/src/legal/content.json"
    target.write_text(json.dumps(content, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
