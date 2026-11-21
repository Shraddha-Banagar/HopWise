"""Curated multi-hop corpus and loader for the Chroma vector index."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

CORPUS_PATH = Path(__file__).with_name("seed_corpus.json")


def load_corpus() -> list[dict[str, Any]]:
    """Load curated documents from the bundled JSON corpus."""
    with CORPUS_PATH.open("r", encoding="utf-8") as handle:
        payload = json.load(handle)
    return payload["documents"]


def format_document(document: dict[str, Any]) -> str:
    return f"{document['title']}. {document['text']}"


__all__ = ["load_corpus", "format_document"]
