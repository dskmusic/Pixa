# -*- coding: utf-8 -*-
"""Genera values/strings.xml (en) y values-es/strings.xml (es) desde strings.tsv.

Formato de strings.tsv:  key|||texto en ingles|||texto en espanol
Lineas que empiezan por '#' son comentarios de seccion y se vuelcan al XML.
Lineas vacias se ignoran.
"""
import io
import os
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
TSV = os.path.join(os.path.dirname(os.path.abspath(__file__)), "strings.tsv")


def esc(s):
    s = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    s = s.replace('"', "\\\"").replace("'", "\\'")
    s = s.replace("\\n", "\\n")
    return s


def build(lang_index):
    out = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    seen = set()
    for raw in io.open(TSV, encoding="utf-8"):
        line = raw.rstrip("\n").rstrip("\r")
        if not line.strip():
            continue
        if line.startswith("#"):
            out.append("")
            out.append("    <!-- %s -->" % line.lstrip("# ").strip())
            continue
        parts = line.split("|||")
        if len(parts) != 3:
            raise SystemExit("Linea mal formada: %r" % line)
        key, en, es = (p.strip() for p in parts)
        if key in seen:
            raise SystemExit("Clave duplicada: %s" % key)
        seen.add(key)
        value = (en, es)[lang_index]
        attr = ' formatted="false"' if value.count("%") > 0 and "$" not in value else ""
        out.append('    <string name="%s"%s>%s</string>' % (key, attr, esc(value)))
    out.append("</resources>")
    out.append("")
    return "\n".join(out), len(seen)


def main():
    en, n = build(0)
    es, _ = build(1)
    p1 = os.path.join(ROOT, "app/src/main/res/values/strings.xml")
    p2 = os.path.join(ROOT, "app/src/main/res/values-es/strings.xml")
    io.open(p1, "w", encoding="utf-8").write(en)
    io.open(p2, "w", encoding="utf-8").write(es)
    print("OK %d claves -> %s / %s" % (n, p1, p2))


if __name__ == "__main__":
    main()
