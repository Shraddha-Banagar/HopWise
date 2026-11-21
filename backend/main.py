"""FastAPI service for the multi-hop agentic RAG & trivia QA system."""

from __future__ import annotations

import os
from typing import Any

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

try:
    from .agents import AgentPipeline, MemoryResponse, QueryRequest, QueryResponse
    from .chroma_store import ChromaStore
except ImportError:
    from agents import AgentPipeline, MemoryResponse, QueryRequest, QueryResponse
    from chroma_store import ChromaStore

app = FastAPI(title="Agentic RAG Trivia API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=os.getenv("ALLOWED_ORIGINS", "*").split(","),
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

store = ChromaStore(os.getenv("CHROMA_PERSIST_DIR"))
pipeline = AgentPipeline(store)

BENCHMARKS: list[dict[str, Any]] = [
    {
        "id": "standard",
        "title": "Annotated Multi-Hop",
        "subtitle": "Clean 2-hop decomposition, Chroma retrieval, memory caching, verification",
        "question": "Who was the father of Ada Lovelace and in which year did he die?",
        "k": 3,
        "max_hops": 3,
        "simulate_wrong_first_hop": False,
        "expected": "Lord Byron, 1824",
        "annotations": [
            {"block": "Planner", "thought": "Split into identity hop then attribute hop."},
            {"block": "Researcher Hop 1", "observation": "Chroma chunks identify Lord Byron as Ada Lovelace's father."},
            {"block": "Researcher Hop 2", "observation": "Poet biography states Lord Byron died in 1824."},
            {"block": "Verifier Audit", "audit": "Entity continuity holds; both claims grounded in source chunks."},
        ],
    },
    {
        "id": "wrong_first_hop",
        "title": "Wrong-First-Hop Propagation",
        "subtitle": "Poisoned entity misleads Hop 2; Verifier detects and rolls back",
        "question": "Who was the father of Ada Lovelace and in which year did he die?",
        "k": 3,
        "max_hops": 3,
        "simulate_wrong_first_hop": True,
        "expected": "Corrected to Lord Byron, 1824",
        "annotations": [
            {"block": "Researcher Hop 1", "observation": "Forced plausible but wrong entity: Charles Babbage."},
            {"block": "Researcher Hop 2", "observation": "Query follows poisoned entity; death year cannot be grounded."},
            {"block": "Verifier Audit", "audit": "Contradiction between Hop 1 claim and retrieved chunk; chain halted."},
            {"block": "Recovery", "audit": "Rollback, re-plan, re-retrieve, and verify corrected chain."},
        ],
    },
    {
        "id": "missing_fact",
        "title": "Missing-Fact RAG",
        "subtitle": "Low similarity, hop exhaustion, safe verifier abstention",
        "question": "What was the exact date of the first UPDATE query executed at CERN in 1990?",
        "k": 3,
        "max_hops": 3,
        "simulate_wrong_first_hop": False,
        "expected": "Insufficient evidence in knowledge base",
        "annotations": [
            {"block": "Researcher", "observation": "All Chroma similarity scores remain below threshold."},
            {"block": "Planner", "observation": "Alternate probe queries generated until the hop cap is reached."},
            {"block": "Verifier Audit", "audit": "No supporting chunk exists; answer withheld instead of hallucinated."},
        ],
    },
]

BENCHMARK_BY_ID = {item["id"]: item for item in BENCHMARKS}


class ResetRequest(BaseModel):
    session_id: str = "default"


class ResetResponse(BaseModel):
    session_id: str
    cleared: int
    message: str


class CorpusResponse(BaseModel):
    backend: str
    document_count: int
    chunk_count: int
    documents: list[dict[str, Any]]


@app.get("/api/health")
def health() -> dict[str, str]:
    return {"status": "ok", "vector_backend": store.backend, "llm_provider": pipeline.llm.provider}


@app.get("/api/scenarios")
def scenarios() -> list[dict[str, Any]]:
    return BENCHMARKS


@app.post("/api/query", response_model=QueryResponse)
def query(request: QueryRequest) -> QueryResponse:
    resolved = request.model_copy()
    if not resolved.question.strip() and resolved.scenario in BENCHMARK_BY_ID:
        resolved.question = BENCHMARK_BY_ID[resolved.scenario]["question"]
    if resolved.scenario == "wrong_first_hop":
        resolved.simulate_wrong_first_hop = True
    return pipeline.run(resolved)


@app.get("/api/traces", response_model=list[QueryResponse])
def traces(session_id: str | None = None) -> list[QueryResponse]:
    return pipeline.get_traces(session_id)


@app.get("/api/memory", response_model=MemoryResponse)
def memory(session_id: str = "default") -> MemoryResponse:
    return pipeline.get_memory(session_id)


@app.post("/api/reset-memory", response_model=ResetResponse)
def reset_memory(request: ResetRequest) -> ResetResponse:
    cleared = pipeline.reset_memory(request.session_id)
    return ResetResponse(
        session_id=request.session_id,
        cleared=cleared,
        message=f"Cleared {cleared} cached facts for session {request.session_id}.",
    )


@app.get("/api/corpus", response_model=CorpusResponse)
def corpus() -> CorpusResponse:
    return CorpusResponse(
        backend=store.backend,
        document_count=len(store.documents),
        chunk_count=len(store.chunks),
        documents=store.corpus_payload(),
    )
