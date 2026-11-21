"""ChromaDB-backed vector store with an offline deterministic embedding function.

The embedding function is lexical/hash based so the demo works without downloading
model weights. Swap it for an OpenAI/Groq embedding function in production.
"""

from __future__ import annotations

import hashlib
import math
import os
import re
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Sequence

os.environ.setdefault("ANONYMIZED_TELEMETRY", "False")
os.environ.setdefault("CHROMA_TELEMETRY_ENABLED", "False")

try:
    from chromadb.api.types import EmbeddingFunction, Documents, Embeddings

    _EmbeddingBase = EmbeddingFunction
except Exception:
    _EmbeddingBase = object
    Documents = None
    Embeddings = None

try:
    from .seed_corpus import format_document, load_corpus
except ImportError:
    from seed_corpus import format_document, load_corpus

DIM = 384
COLLECTION_NAME = "agentic_multi_hop_rag"

_SYNONYMS = {
    "father": "father parent dad paternal",
    "mother": "mother parent maternal",
    "died": "died death dead passed",
    "death": "death died dead",
    "born": "born birth birthday",
    "designed": "designed designer design built",
    "designer": "designer designed design",
    "wrote": "wrote written author note",
    "discovered": "discovered discovery found",
    "highest": "highest tallest summit peak",
    "largest": "largest biggest greatest",
    "compiler": "compiler compiled compilation",
    "radioactivity": "radioactivity radioactive radium polonium",
}

_TOKEN_RE = re.compile(r"[a-z0-9]+")


def _expand(text: str) -> str:
    words = _TOKEN_RE.findall(text.lower())
    expanded = list(words)
    for word in words:
        if word in _SYNONYMS:
            expanded.extend(_SYNONYMS[word].split())
    return " ".join(expanded)


def embed(text: str) -> list[float]:
    """Deterministic normalized lexical embedding (tokens + character trigrams)."""
    normalized = _expand(text)
    words = _TOKEN_RE.findall(normalized)
    vector = [0.0] * DIM

    for token in words:
        digest = hashlib.sha256(token.encode("utf-8")).digest()
        index = int.from_bytes(digest[:4], "big") % DIM
        sign = 1.0 if digest[4] & 1 else -1.0
        vector[index] += sign * 1.0

    compact = re.sub(r"[^a-z0-9]+", "", normalized)
    for start in range(max(0, len(compact) - 2)):
        trigram = compact[start : start + 3]
        digest = hashlib.sha256(trigram.encode("utf-8")).digest()
        index = int.from_bytes(digest[:4], "big") % DIM
        sign = 1.0 if digest[4] & 1 else -1.0
        vector[index] += sign * 0.18

    norm = math.sqrt(sum(value * value for value in vector))
    if norm == 0:
        vector[0] = 1.0
        return vector
    return [value / norm for value in vector]


def cosine(left: Sequence[float], right: Sequence[float]) -> float:
    return sum(a * b for a, b in zip(left, right))


class DeterministicEmbeddingFunction(_EmbeddingBase):
    """Embedding function compatible with Chroma's EmbeddingFunction protocol."""

    @staticmethod
    def name() -> str:
        return "deterministic_lexical_v1"

    def __call__(self, input: Sequence[str]) -> list[list[float]]:  # noqa: A002
        return [embed(text) for text in input]

    def get_config(self) -> dict[str, Any]:
        return {}

    def build_from_config(self, config: dict[str, Any]) -> "DeterministicEmbeddingFunction":
        return self

    def is_picklable(self) -> bool:
        return True


@dataclass(frozen=True)
class Chunk:
    id: str
    doc_id: str
    title: str
    entity: str
    text: str
    vector: list[float]


@dataclass(frozen=True)
class SearchHit:
    id: str
    doc_id: str
    title: str
    entity: str
    text: str
    score: float
    source: str


def _split_document(document: dict[str, Any]) -> list[Chunk]:
    full_text = format_document(document)
    sentences = re.split(r"(?<=[.!?])\s+", full_text)
    groups: list[str] = []
    current = ""
    for sentence in sentences:
        candidate = f"{current} {sentence}".strip()
        if current and len(candidate) > 420:
            groups.append(current)
            current = sentence
        else:
            current = candidate
    if current:
        groups.append(current)

    chunks: list[Chunk] = []
    for index, group in enumerate(groups):
        chunks.append(
            Chunk(
                id=f"{document['id']}-c{index}",
                doc_id=document["id"],
                title=document["title"],
                entity=document["entity"],
                text=group,
                vector=embed(group),
            )
        )
    return chunks


class ChromaStore:
    """Vector index with automatic local fallback when ChromaDB is unavailable."""

    def __init__(self, persist_dir: str | Path | None = None) -> None:
        self.documents = load_corpus()
        self.chunks = [chunk for document in self.documents for chunk in _split_document(document)]
        self._by_id = {chunk.id: chunk for chunk in self.chunks}
        self.backend = "local-lexical"
        self.collection: Any = None
        self._init_chroma(Path(persist_dir) if persist_dir else Path(__file__).with_name(".chroma"))

    def _init_chroma(self, persist_dir: Path) -> None:
        try:
            import chromadb
            from chromadb.config import Settings

            embedding_fn = DeterministicEmbeddingFunction()
            persist_dir.mkdir(parents=True, exist_ok=True)
            client = chromadb.PersistentClient(
                path=str(persist_dir),
                settings=Settings(anonymized_telemetry=False),
            )
            collection = client.get_or_create_collection(
                name=COLLECTION_NAME,
                embedding_function=embedding_fn,
                metadata={"hnsw:space": "cosine"},
            )
            if collection.count() != len(self.chunks):
                client.delete_collection(name=COLLECTION_NAME)
                collection = client.create_collection(
                    name=COLLECTION_NAME,
                    embedding_function=embedding_fn,
                    metadata={"hnsw:space": "cosine"},
                )
                collection.upsert(
                    ids=[chunk.id for chunk in self.chunks],
                    documents=[chunk.text for chunk in self.chunks],
                    metadatas=[
                        {
                            "doc_id": chunk.doc_id,
                            "title": chunk.title,
                            "entity": chunk.entity,
                        }
                        for chunk in self.chunks
                    ],
                )
            self.collection = collection
            self.backend = "chromadb"
        except Exception:
            self.collection = None
            self.backend = "local-lexical"

    def search(self, query: str, k: int) -> list[SearchHit]:
        k = max(1, min(5, int(k)))
        if self.collection is not None:
            try:
                return self._chroma_search(query, k)
            except Exception:
                pass
        return self._local_search(query, k)

    def _chroma_search(self, query: str, k: int) -> list[SearchHit]:
        result = self.collection.query(
            query_texts=[query],
            n_results=min(k, len(self.chunks)),
            include=["documents", "metadatas", "distances"],
        )
        ids = result["ids"][0]
        documents = result["documents"][0]
        metadatas = result["metadatas"][0]
        distances = result["distances"][0]
        hits: list[SearchHit] = []
        for chunk_id, text, metadata, distance in zip(ids, documents, metadatas, distances):
            hits.append(
                SearchHit(
                    id=chunk_id,
                    doc_id=metadata["doc_id"],
                    title=metadata["title"],
                    entity=metadata["entity"],
                    text=text,
                    score=round(max(0.0, 1.0 - float(distance)), 4),
                    source="chromadb",
                )
            )
        return hits

    def _local_search(self, query: str, k: int) -> list[SearchHit]:
        query_vector = embed(query)
        scored = [(cosine(query_vector, chunk.vector), chunk) for chunk in self.chunks]
        scored.sort(key=lambda pair: pair[0], reverse=True)
        return [
            SearchHit(
                id=chunk.id,
                doc_id=chunk.doc_id,
                title=chunk.title,
                entity=chunk.entity,
                text=chunk.text,
                score=round(max(0.0, score), 4),
                source="local-lexical",
            )
            for score, chunk in scored[:k]
        ]

    def compare_k(self, query: str, k_values: Iterable[int] = (1, 3, 5)) -> list[dict[str, Any]]:
        """Latency and overlap metrics used by the Memory & Corpus screen."""
        searches: dict[int, list[SearchHit]] = {}
        metrics: list[dict[str, Any]] = []
        for value in k_values:
            started = time.perf_counter()
            hits = self.search(query, value)
            latency_ms = round((time.perf_counter() - started) * 1000, 3)
            searches[value] = hits
            reference = searches.get(3, hits)
            reference_ids = {hit.id for hit in reference}
            ids = {hit.id for hit in hits}
            overlap = round(len(ids & reference_ids) / max(1, min(len(ids), len(reference_ids))), 3)
            metrics.append(
                {
                    "k": value,
                    "latency_ms": latency_ms,
                    "top_score": hits[0].score if hits else 0.0,
                    "chunk_overlap": overlap,
                    "chunk_ids": [hit.id for hit in hits],
                }
            )
        return metrics

    def corpus_payload(self) -> list[dict[str, Any]]:
        return [
            {
                "id": document["id"],
                "title": document["title"],
                "entity": document["entity"],
                "text": document["text"],
                "chunk_count": sum(1 for chunk in self.chunks if chunk.doc_id == document["id"]),
            }
            for document in self.documents
        ]


__all__ = ["ChromaStore", "SearchHit", "embed", "cosine"]
