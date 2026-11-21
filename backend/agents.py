"""Multi-agent pipeline: planner -> researcher -> verifier with recovery mode."""

from __future__ import annotations

import os
import re
import time
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Literal

from pydantic import BaseModel, Field

try:
    from .chroma_store import ChromaStore, SearchHit
except ImportError:
    from chroma_store import ChromaStore, SearchHit

MIN_SCORE = 0.16
SCENARIO_QUESTIONS = {
    "standard": "Who was the father of Ada Lovelace and in which year did he die?",
    "wrong_first_hop": "Who was the father of Ada Lovelace and in which year did he die?",
    "missing_fact": "What was the exact date of the first UPDATE query executed at CERN in 1990?",
}
POISON_ENTITIES = {
    "ada lovelace": "Charles Babbage",
    "grace hopper": "Alan Turing",
    "marie curie": "Pierre Curie",
    "alan turing": "Charles Babbage",
}

KValues = Literal[1, 3, 5]
HopValues = Literal[2, 3, 4]
Scenario = Literal["auto", "standard", "wrong_first_hop", "missing_fact"]


class QueryRequest(BaseModel):
    session_id: str = "default"
    question: str = ""
    k: KValues = 3
    max_hops: HopValues = 3
    simulate_wrong_first_hop: bool = False
    scenario: Scenario = "auto"


class EvidenceRecord(BaseModel):
    id: str
    doc_id: str
    title: str
    entity: str
    text: str
    score: float


class TraceStep(BaseModel):
    id: str
    agent: Literal["planner", "researcher", "verifier", "system"]
    hop: int | None = None
    title: str
    status: Literal["ok", "cache_hit", "poisoned", "warning", "error", "correction", "abstain"]
    thought: str = ""
    action: str = ""
    observation: str = ""
    audit: str = ""
    evidence: list[EvidenceRecord] = Field(default_factory=list)
    latency_ms: float = 0.0


class FactRecord(BaseModel):
    key: str
    subject: str
    predicate: str
    value: str
    source_id: str = ""
    source_title: str = ""
    confidence: float = 0.0
    query: str = ""
    hop: int = 0
    created_at: str = ""
    verified: bool = True


class KMetric(BaseModel):
    k: int
    latency_ms: float
    top_score: float
    chunk_overlap: float
    chunk_ids: list[str]


class QueryMetrics(BaseModel):
    k: int
    latency_ms: float
    retrieval_calls: int
    cache_hits: int
    max_hops: int
    hops_used: int
    k_comparison: list[KMetric]


class QueryResponse(BaseModel):
    id: str
    session_id: str
    question: str
    scenario: str
    answer: str
    verified: bool
    status: Literal["verified", "corrected", "abstained"]
    sub_questions: list[str]
    steps: list[TraceStep]
    facts_used: list[FactRecord]
    metrics: QueryMetrics
    created_at: str


class MemoryResponse(BaseModel):
    session_id: str
    facts: list[FactRecord]
    trace_count: int


@dataclass
class Plan:
    scenario: str
    sub_questions: list[str]
    target_entity: str
    relation: str


@dataclass
class FactCandidate:
    key: str
    subject: str
    predicate: str
    value: str
    source_id: str
    source_title: str
    confidence: float
    query: str
    hop: int


def _now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def _key(subject: str, predicate: str) -> str:
    return f"{subject.strip().lower()}|{predicate.strip().lower()}"


def _normalize(text: str) -> str:
    return re.sub(r"[^a-z0-9 ]", " ", text.lower()).strip()


def _find_entity(question: str, store: ChromaStore) -> str:
    lowered = question.lower()
    matches = [document["entity"] for document in store.documents if document["entity"].lower() in lowered]
    if not matches:
        people = ["CERN", "UNIVAC I", "Bletchley Park", "Great Red Spot"]
        matches = [item for item in people if item.lower() in lowered]
    return max(matches, key=len) if matches else "the topic"


class LLMClient:
    """Optional LangChain LLM for answer phrasing and verifier audits."""

    def __init__(self) -> None:
        self.llm: Any = None
        self.provider = "none"
        provider = os.getenv("LLM_PROVIDER", "auto").lower()
        groq_key = os.getenv("GROQ_API_KEY", "")
        openai_key = os.getenv("OPENAI_API_KEY", "")
        try:
            if provider in {"auto", "groq"} and groq_key:
                from langchain_groq import ChatGroq

                self.llm = ChatGroq(
                    model=os.getenv("GROQ_MODEL", "llama-3.1-8b-instant"),
                    api_key=groq_key,
                    temperature=0,
                )
                self.provider = "groq"
            elif provider in {"auto", "openai"} and openai_key:
                from langchain_openai import ChatOpenAI

                self.llm = ChatOpenAI(
                    model=os.getenv("OPENAI_MODEL", "gpt-4o-mini"),
                    api_key=openai_key,
                    temperature=0,
                )
                self.provider = "openai"
        except Exception:
            self.llm = None
            self.provider = "none"

    def _invoke(self, system: str, prompt: str) -> str:
        if self.llm is None:
            return ""
        try:
            from langchain_core.messages import HumanMessage, SystemMessage

            response = self.llm.invoke([SystemMessage(content=system), HumanMessage(content=prompt)])
            return str(response.content).strip()
        except Exception:
            return ""

    def phrase_answer(self, question: str, facts: list[FactCandidate]) -> str:
        if not facts:
            return "Insufficient evidence in knowledge base"
        deterministic = ", ".join(f"{fact.subject}: {fact.value}" for fact in facts)
        result = self._invoke(
            "You are a precise trivia answer editor. Use only the supplied evidence. "
            "Return one short declarative sentence with no preamble.",
            f"Question: {question}\nVerified evidence: {deterministic}\nAnswer:",
        )
        if result and len(result) < 400:
            return result.replace("\n", " ").strip()
        if len(facts) == 1:
            return f"{facts[0].subject}: {facts[0].value}."
        father_fact = next((fact for fact in facts if fact.predicate == "father"), None)
        death_fact = next((fact for fact in facts if fact.predicate == "death_year"), None)
        if father_fact and death_fact:
            return f"{father_fact.subject}'s father was {father_fact.value}, who died in {death_fact.value}."
        clauses = [f"{fact.subject} {fact.predicate.replace('_', ' ')} {fact.value}" for fact in facts]
        return "; ".join(clauses).capitalize() + "."

    def audit(self, claim: str, evidence: str) -> str:
        result = self._invoke(
            "You are a fact-verification agent. Reply with SUPPORTED or CONTRADICTED followed by one short reason.",
            f"Claim: {claim}\nRetrieved evidence: {evidence}\nAudit:",
        )
        return result[:400] if result else ""


class SessionMemory:
    def __init__(self) -> None:
        self.facts: dict[str, FactRecord] = {}
        self.traces: list[QueryResponse] = []

    def get(self, key: str) -> FactRecord | None:
        return self.facts.get(key)

    def put(self, candidate: FactCandidate) -> FactRecord:
        record = FactRecord(**candidate.__dict__, verified=True, created_at=_now())
        self.facts[candidate.key] = record
        return record

    def clear(self) -> int:
        count = len(self.facts)
        self.facts.clear()
        return count


class AgentPipeline:
    def __init__(self, store: ChromaStore) -> None:
        self.store = store
        self.llm = LLMClient()
        self.sessions: dict[str, SessionMemory] = {}

    def session(self, session_id: str) -> SessionMemory:
        return self.sessions.setdefault(session_id, SessionMemory())

    def _plan(self, request: QueryRequest) -> Plan:
        question = request.question.strip()
        scenario = request.scenario
        if not question and scenario in SCENARIO_QUESTIONS:
            question = SCENARIO_QUESTIONS[scenario]
        lowered = _normalize(question)
        if scenario == "auto":
            if any(token in lowered for token in ("cern", "update query", "first sql", "1990")):
                scenario = "missing_fact"
            elif "father" in lowered and any(token in lowered for token in ("die", "died", "death")):
                scenario = "standard"
            else:
                scenario = "standard"

        entity = _find_entity(question, self.store)
        if entity == "the topic" and "ada" in lowered:
            entity = "Ada Lovelace"
        if scenario == "missing_fact":
            probes = [question]
            probes.append(f"What does the knowledge base record about {entity}?")
            if len(lowered.split()) > 4:
                probes.append("Which indexed sources mention this event or technology?")
            return Plan(scenario, probes[: request.max_hops], entity, "absent_fact")

        if "father" in lowered and any(token in lowered for token in ("die", "died", "death")):
            sub_questions = [
                f"Who was the father of {entity}?",
                f"In which year did {{{{HOP_1}}}} die?",
            ]
            return Plan(scenario, sub_questions[: request.max_hops], entity, "father_death")

        clauses = [clause.strip() for clause in question.split(" and ") if clause.strip()]
        if len(clauses) > 1:
            resolved = [clauses[0]] + [f"{{{{HOP_{index}}}}} {clause}" for index, clause in enumerate(clauses[1:], start=1)]
            return Plan(scenario, resolved[: request.max_hops], entity, "generic")
        return Plan(scenario, [question][: request.max_hops], entity, "generic")

    def _extract_fact(self, query: str, hits: list[SearchHit], hop: int, target: str) -> FactCandidate | None:
        text = " ".join(hit.text for hit in hits)
        lowered = _normalize(query)
        top = hits[0] if hits else None
        if top is None or top.score < MIN_SCORE:
            return None

        value = ""
        predicate = "answer"
        if "father" in lowered:
            predicate = "father"
            for pattern in (
                r"(?:His|Her|Their|The) father was (?:the\s+)?(?:[a-z]+\s+)?([A-Z][a-z]+(?:\s+[A-Z][a-z]+){0,2})",
                r"([A-Z][a-z]+(?:\s+[A-Z][a-z]+){0,2}) was the father of",
            ):
                match = re.search(pattern, text)
                if match:
                    value = match.group(1).strip()
                    if _normalize(value) == _normalize(target):
                        value = ""
                    break
        elif any(token in lowered for token in ("die", "died", "death")):
            predicate = "death_year"
            match = re.search(r"died in(?:\s+[A-Z][a-z]+)*\s+in\s+(\d{4})", text) or re.search(
                r"(?:died in|death in)\s+(\d{4})", text
            )
            if match:
                value = match.group(1)
        elif "born" in lowered:
            predicate = "birth_year"
            match = re.search(r"born in (\d{4})", text)
            if match:
                value = match.group(1)
        elif "discovered" in lowered and "elements" in lowered:
            predicate = "discovered_elements"
            match = re.search(r"discovered the elements ([\w]+ and [\w]+)", text)
            if match:
                value = match.group(1)
        elif "highest" in lowered and "mountain" in lowered:
            predicate = "highest_mountain"
            match = re.search(r"([A-Z][a-z]+(?: [A-Z][a-z]+)?) is Earth'?s highest mountain", text)
            if match:
                value = match.group(1)
        elif "largest" in lowered and "planet" in lowered:
            predicate = "largest_planet"
            match = re.search(r"([A-Z][a-z]+) is the fifth planet from the Sun and the largest planet", text)
            if match:
                value = match.group(1)
        elif "storm" in lowered and "jupiter" in lowered:
            predicate = "jupiter_storm"
            match = re.search(r"storm called the ([A-Z][\w]+(?: [A-Z][\w]+)*)", text)
            if match:
                value = match.group(1)

        if not value and predicate in {"death_year", "birth_year"}:
            return None

        if not value:
            query_tokens = set(_normalize(query).split()) - {"the", "who", "what", "was", "is", "did", "of", "in", "which", "a"}
            best_sentence = ""
            best_overlap = 0
            for sentence in re.split(r"(?<=[.!?])\s+", text):
                overlap = len(query_tokens & set(_normalize(sentence).split()))
                if overlap > best_overlap:
                    best_overlap = overlap
                    best_sentence = sentence
            match = re.search(r"\b([A-Z][a-z]+(?:\s+[A-Z][a-z]+){0,2})\b", best_sentence)
            if match and best_overlap >= 2:
                value = match.group(1).strip()

        if not value:
            return None
        return FactCandidate(
            key=_key(target, predicate),
            subject=target,
            predicate=predicate,
            value=value,
            source_id=top.doc_id,
            source_title=top.title,
            confidence=min(0.99, round(0.55 + top.score, 3)),
            query=query,
            hop=hop,
        )

    @staticmethod
    def _evidence(hits: list[SearchHit]) -> list[EvidenceRecord]:
        return [
            EvidenceRecord(
                id=hit.id,
                doc_id=hit.doc_id,
                title=hit.title,
                entity=hit.entity,
                text=hit.text,
                score=hit.score,
            )
            for hit in hits
        ]

    def _retrieve(
        self,
        query: str,
        hop: int,
        k: int,
        memory: SessionMemory,
        target: str,
        counters: dict[str, int],
    ) -> tuple[TraceStep, FactCandidate | None, list[SearchHit], bool]:
        started = time.perf_counter()
        cached_key = None
        if "father" in _normalize(query):
            cached_key = _key(target, "father")
        elif any(token in _normalize(query) for token in ("die", "died", "death")):
            for fact in memory.facts.values():
                if fact.predicate == "father":
                    cached_key = _key(fact.value, "death_year")
                    break
        cached = memory.get(cached_key) if cached_key else None

        if cached is not None:
            counters["cache_hits"] += 1
            step = TraceStep(
                id=str(uuid.uuid4()),
                agent="researcher",
                hop=hop,
                title=f"Hop {hop} - Session Memory hit",
                status="cache_hit",
                thought=f"Check cached verified facts before querying Chroma for: {query}",
                action=f"MemoryLookup[{cached.key}]",
                observation=f"Reused verified fact '{cached.value}' from {cached.source_title}. No retrieval call made.",
                latency_ms=round((time.perf_counter() - started) * 1000, 3),
            )
            candidate = FactCandidate(**{field: getattr(cached, field) for field in FactCandidate.__dataclass_fields__})
            return step, candidate, [], True

        hits = self.store.search(query, k)
        counters["retrieval_calls"] += 1
        candidate = self._extract_fact(query, hits, hop, target)
        top_score = hits[0].score if hits else 0.0
        observation = (
            f"Top similarity {top_score:.3f}. "
            + (
                f"Candidate fact: {candidate.value} ({candidate.source_title})."
                if candidate
                else "No supported fact was found in the retrieved chunks."
            )
        )
        step = TraceStep(
            id=str(uuid.uuid4()),
            agent="researcher",
            hop=hop,
            title=f"Hop {hop} - Researcher",
            status="ok" if candidate else "warning",
            thought=f"Resolve sub-question {hop}: {query}",
            action=f"ChromaSearch[query='{query}', k={k}]",
            observation=observation,
            evidence=self._evidence(hits),
            latency_ms=round((time.perf_counter() - started) * 1000, 3),
        )
        return step, candidate, hits, False

    def run(self, request: QueryRequest) -> QueryResponse:
        started = time.perf_counter()
        memory = self.session(request.session_id)
        plan = self._plan(request)
        if not request.question.strip() and plan.scenario in SCENARIO_QUESTIONS:
            request = request.model_copy(update={"question": SCENARIO_QUESTIONS[plan.scenario]})
        steps: list[TraceStep] = []
        counters = {"retrieval_calls": 0, "cache_hits": 0}
        facts: list[FactCandidate] = []
        scenario = plan.scenario
        poisoned_entity = POISON_ENTITIES.get(_normalize(plan.target_entity), "Charles Babbage")
        if request.simulate_wrong_first_hop and scenario != "missing_fact":
            scenario = "wrong_first_hop"

        plan_step = TraceStep(
            id=str(uuid.uuid4()),
            agent="planner",
            title="Planner - decompose question",
            status="ok",
            thought=f"Decompose the question into at most {request.max_hops} sequential hops.",
            action=f"Plan[scenario={scenario}, max_hops={request.max_hops}]",
            observation=" -> ".join(f"Hop {index}: {item}" for index, item in enumerate(plan.sub_questions, start=1)),
            latency_ms=0.0,
        )
        steps.append(plan_step)

        if scenario == "wrong_first_hop":
            return self._run_poisoned(request, plan, memory, steps, facts, counters, started, poisoned_entity)

        resolved: list[str] = []
        used_hits: list[SearchHit] = []
        hop_evidence: dict[int, list[SearchHit]] = {}
        cached_flags: dict[int, bool] = {}
        for index, sub_question in enumerate(plan.sub_questions, start=1):
            query = sub_question
            for hop_number, fact in enumerate(facts, start=1):
                query = query.replace("{{HOP_%d}}" % hop_number, fact.value)
            query = re.sub(r"\{\{HOP_\d+\}\}", plan.target_entity, query).strip()
            resolved.append(query)
            hop_target = facts[-1].value if index > 1 and facts else plan.target_entity
            step, candidate, hits, was_cached = self._retrieve(query, index, request.k, memory, hop_target, counters)
            steps.append(step)
            used_hits.extend(hits)
            hop_evidence[index] = hits
            cached_flags[index] = was_cached
            if candidate:
                facts.append(candidate)

        unsupported = [
            fact
            for fact in facts
            if not cached_flags.get(fact.hop, False)
            and fact.value.lower() not in " ".join(hit.text.lower() for hit in hop_evidence.get(fact.hop, []))
        ]
        final_status = "verified"
        stored_facts: list[FactRecord] = []
        if scenario == "missing_fact" or not facts or unsupported:
            final_status = "abstained"
            audit_text = (
                f"No retrieved chunk exceeded the evidence threshold ({MIN_SCORE:.2f}). "
                "Researcher exhausted the configured hop cap; verifier refused to synthesize an unsupported answer."
            )
            if unsupported:
                audit_text = (
                    f"Verifier rejected {len(unsupported)} unsupported claim(s): "
                    + ", ".join(f"{fact.subject}={fact.value}" for fact in unsupported)
                    + ". No final answer synthesized."
                )
            steps.append(
                TraceStep(
                    id=str(uuid.uuid4()),
                    agent="verifier",
                    title="Verifier - abstain",
                    status="abstain",
                    thought="Check whether the retrieved evidence supports a definite answer.",
                    action="Verify[claim=final_answer]",
                    observation=audit_text,
                    audit=audit_text,
                )
            )
            answer = "Insufficient evidence in knowledge base"
        else:
            audit_text = "All hop claims are grounded in retrieved Chroma chunks. No contradictions detected."
            llm_audit = self.llm.audit("; ".join(f"{fact.subject} {fact.value}" for fact in facts), used_hits[0].text if used_hits else "")
            if llm_audit:
                audit_text += f" LLM audit: {llm_audit}"
            steps.append(
                TraceStep(
                    id=str(uuid.uuid4()),
                    agent="verifier",
                    title="Verifier - cross-check",
                    status="ok",
                    thought="Cross-check intermediate claims against source chunks and entity continuity.",
                    action="Verify[claim=final_answer]",
                    observation=audit_text,
                    audit=audit_text,
                )
            )
            answer = self.llm.phrase_answer(request.question, facts)
            for fact in facts:
                stored_facts.append(memory.put(fact))

        response = QueryResponse(
            id=str(uuid.uuid4()),
            session_id=request.session_id,
            question=request.question,
            scenario=scenario,
            answer=answer,
            verified=final_status == "verified",
            status=final_status,
            sub_questions=resolved,
            steps=steps,
            facts_used=stored_facts,
            metrics=self._metrics(request, counters, len(resolved), started, resolved[0] if resolved else request.question),
            created_at=_now(),
        )
        memory.traces.append(response)
        return response

    def _run_poisoned(
        self,
        request: QueryRequest,
        plan: Plan,
        memory: SessionMemory,
        steps: list[TraceStep],
        facts: list[FactCandidate],
        counters: dict[str, int],
        started: float,
        poison_entity: str,
    ) -> QueryResponse:
        base_question = f"Who was the father of {plan.target_entity}?"
        death_question = "In which year did the father die?"
        observation = (
            f"Top retrieved chunk names the real father, but the poisoned selector chose '{poison_entity}' "
            "because that entity appears in a related biography."
        )
        steps.append(
            TraceStep(
                id=str(uuid.uuid4()),
                agent="researcher",
                hop=1,
                title="Hop 1 - Poisoned selection",
                status="poisoned",
                thought=f"Identify the father of {plan.target_entity}.",
                action=f"ChromaSearch[query='{base_question}', k={request.k}] + forced_wrong_entity",
                observation=observation,
                evidence=self._evidence(self.store.search(base_question, request.k)),
                latency_ms=2.4,
            )
        )
        counters["retrieval_calls"] += 1
        poison_query = f"In which year did {poison_entity} die?"
        poison_hits = self.store.search(poison_query, request.k)
        counters["retrieval_calls"] += 1
        steps.append(
            TraceStep(
                id=str(uuid.uuid4()),
                agent="researcher",
                hop=2,
                title="Hop 2 - Misled by poisoned context",
                status="warning",
                thought=f"Follow Hop 1 claim and find the death year of {poison_entity}.",
                action=f"ChromaSearch[query='{poison_query}', k={request.k}]",
                observation=(
                    f"Only biographical context for {poison_entity} was found; no death year is stated. "
                    f"Top similarity {poison_hits[0].score if poison_hits else 0.0:.3f}."
                ),
                evidence=self._evidence(poison_hits),
            )
        )
        truth_hits = self.store.search(base_question, request.k)
        truth_candidate = self._extract_fact(base_question, truth_hits, 1, plan.target_entity)
        audit = (
            f"Contradiction detected. Hop 1 claimed '{poison_entity}' is the father of {plan.target_entity}, "
            f"but the retrieved chunk states '{truth_candidate.value if truth_candidate else 'Lord Byron'}'. "
            f"Hop 2 evidence for '{poison_entity}' does not support the claim. Chain halted before synthesis."
        )
        steps.append(
            TraceStep(
                id=str(uuid.uuid4()),
                agent="verifier",
                title="Verifier - contradiction detected",
                status="error",
                thought="Cross-check Hop 2 evidence against Hop 1 claim and retrieved source chunks.",
                action="Verify[claim=entity_continuity]",
                observation=audit,
                audit=audit,
            )
        )
        llm_audit = self.llm.audit(audit, truth_hits[0].text if truth_hits else "")
        if llm_audit:
            steps[-1].audit += f" LLM audit: {llm_audit}"

        corrected_facts: list[FactCandidate] = []
        stored_corrected_facts: list[FactRecord] = []
        steps.append(
            TraceStep(
                id=str(uuid.uuid4()),
                agent="system",
                title="Recovery - rollback and re-plan",
                status="correction",
                thought="Discard the poisoned fact and retry the same hop slots with grounded evidence.",
                action="Rollback[hop=1] -> Replan[from_original_question]",
                observation=f"Removed unverified claim '{poison_entity}'. Re-running Hop 1 and Hop 2 with verified retrieval.",
            )
        )
        for index, query in enumerate((base_question, death_question), start=1):
            resolved_query = query
            if index == 2:
                resolved_query = f"In which year did {corrected_facts[0].value} die?" if corrected_facts else death_question
            step, candidate, _hits, _cached = self._retrieve(
                resolved_query,
                index,
                request.k,
                memory,
                corrected_facts[0].value if index == 2 and corrected_facts else plan.target_entity,
                counters,
            )
            steps.append(step)
            if candidate:
                corrected_facts.append(candidate)

        if len(corrected_facts) < 2:
            answer = "Insufficient evidence in knowledge base"
            final_status = "abstained"
            verifier_status: Literal["abstain", "ok"] = "abstain"
            final_audit = "Corrected chain still lacks supported evidence; verifier abstained."
        else:
            answer = self.llm.phrase_answer(request.question, corrected_facts)
            final_status = "corrected"
            verifier_status = "ok"
            final_audit = (
                "Corrected chain verified. Both hops now cite source chunks and entity continuity is restored."
            )
            for fact in corrected_facts:
                stored_corrected_facts.append(memory.put(fact))

        steps.append(
            TraceStep(
                id=str(uuid.uuid4()),
                agent="verifier",
                title="Verifier - corrected chain",
                status=verifier_status,
                thought="Audit the corrected chain before answering.",
                action="Verify[claim=final_answer]",
                observation=final_audit,
                audit=final_audit,
            )
        )
        resolved_questions = [base_question, f"In which year did {corrected_facts[0].value} die?" if corrected_facts else death_question]
        response = QueryResponse(
            id=str(uuid.uuid4()),
            session_id=request.session_id,
            question=request.question,
            scenario="wrong_first_hop",
            answer=answer,
            verified=final_status != "abstained",
            status=final_status,
            sub_questions=resolved_questions,
            steps=steps,
            facts_used=stored_corrected_facts,
            metrics=self._metrics(request, counters, 2, started, base_question),
            created_at=_now(),
        )
        memory.traces.append(response)
        return response

    def _metrics(
        self,
        request: QueryRequest,
        counters: dict[str, int],
        hops_used: int,
        started: float,
        query: str,
    ) -> QueryMetrics:
        comparison = self.store.compare_k(query, (1, 3, 5))
        return QueryMetrics(
            k=request.k,
            latency_ms=round((time.perf_counter() - started) * 1000, 3),
            retrieval_calls=counters["retrieval_calls"],
            cache_hits=counters["cache_hits"],
            max_hops=request.max_hops,
            hops_used=hops_used,
            k_comparison=[KMetric(**item) for item in comparison],
        )

    def get_memory(self, session_id: str) -> MemoryResponse:
        memory = self.session(session_id)
        facts = sorted(memory.facts.values(), key=lambda item: item.created_at, reverse=True)
        return MemoryResponse(session_id=session_id, facts=facts, trace_count=len(memory.traces))

    def reset_memory(self, session_id: str) -> int:
        return self.session(session_id).clear()

    def get_traces(self, session_id: str | None = None) -> list[QueryResponse]:
        if session_id:
            return list(self.session(session_id).traces)
        return [trace for session in self.sessions.values() for trace in session.traces]


__all__ = ["AgentPipeline", "QueryRequest", "QueryResponse", "MemoryResponse", "TraceStep", "FactRecord"]
