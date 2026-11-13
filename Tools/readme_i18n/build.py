#!/usr/bin/env python3
"""Builds README.md and its translations (README.<language>.md) from template.md and strings/<language>.json.

Edit the English text in strings/en.json (and template.md for layout), update the same keys in
every other strings file, then run: python3 Tools/readme_i18n/build.py
"""
from __future__ import annotations

import json
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]

# Code, name in its own language. English first, then by name.
LANGUAGES = [
    ("en", "English"), ("ar", "العربية"), ("bn", "বাংলা"), ("de", "Deutsch"), ("es", "Español"),
    ("fa", "فارسی"), ("fil", "Filipino"), ("fr", "Français"), ("hi", "हिन्दी"), ("id", "Bahasa Indonesia"),
    ("it", "Italiano"), ("ja", "日本語"), ("ko", "한국어"), ("nl", "Nederlands"), ("pl", "Polski"),
    ("pt-BR", "Português (Brasil)"), ("ru", "Русский"), ("ta", "தமிழ்"), ("te", "తెలుగు"), ("th", "ไทย"),
    ("tr", "Türkçe"), ("uk", "Українська"), ("ur", "اردو"), ("vi", "Tiếng Việt"), ("zh-Hans", "简体中文"),
    ("zh-Hant", "繁體中文"),
]
RIGHT_TO_LEFT = {"ar", "fa", "ur"}
GENERATED_NOTE = ("<!-- Built by Tools/readme_i18n/build.py from template.md and strings/<language>.json. "
                  "Edit those and run the script instead of editing this file. -->")


def file_name(code: str) -> str:
    return "README.md" if code == "en" else f"README.{code}.md"


def language_bar(current: str) -> str:
    items = [f"**{name}**" if code == current else f"[{name}]({file_name(code)})" for code, name in LANGUAGES]
    return "<sub>🌐 " + " · ".join(items) + "</sub>"


def render(code: str, template: str, english: dict, strings: dict) -> str:
    missing = sorted(set(english) - set(strings))
    if missing:
        raise SystemExit(f"{code}: missing {', '.join(missing)}")
    for key, value in strings.items():
        if key in english and sorted(re.findall(r"\]\(([^)]+)\)", value)) != sorted(re.findall(r"\]\(([^)]+)\)", english[key])):
            raise SystemExit(f"{code}: the links in {key} differ from English")
    text = re.sub(r"\{\{(\w+)\}\}", lambda m: strings[m.group(1)], template)
    head, rest = text.split("\n", 1)
    extra = "\n\n" + language_bar(code)
    if code != "en":
        extra += "\n\n> " + strings["translation_note"]
    text = head + extra + "\n" + rest
    text = text.rstrip("\n") + "\n\n" + GENERATED_NOTE + "\n"
    if code in RIGHT_TO_LEFT:
        text = '<div dir="rtl">\n\n' + text.rstrip("\n") + "\n\n</div>\n"
    return text


def main() -> None:
    template = (HERE / "template.md").read_text()
    english = json.loads((HERE / "strings/en.json").read_text())
    english_keys = {k: v for k, v in english.items() if k != "translation_note"}
    for code, _ in LANGUAGES:
        path = HERE / f"strings/{code}.json"
        if not path.exists():
            raise SystemExit(f"Missing {path.relative_to(ROOT)}")
        strings = json.loads(path.read_text())
        (ROOT / file_name(code)).write_text(render(code, template, english_keys, strings))
    print(f"Built {len(LANGUAGES)} READMEs")


if __name__ == "__main__":
    main()
