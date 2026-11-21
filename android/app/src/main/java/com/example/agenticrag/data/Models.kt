package com.example.agenticrag.data

import com.google.gson.annotations.SerializedName

data class QueryRequest(
    @SerializedName("session_id") val sessionId: String = "android-default",
    val question: String,
    val k: Int = 3,
    @SerializedName("max_hops") val maxHops: Int = 3,
    @SerializedName("simulate_wrong_first_hop") val simulateWrongFirstHop: Boolean = false,
    val scenario: String = "auto",
)

data class ResetRequest(@SerializedName("session_id") val sessionId: String = "android-default")

data class EvidenceRecord(
    val id: String = "",
    @SerializedName("doc_id") val docId: String = "",
    val title: String = "",
    val entity: String = "",
    val text: String = "",
    val score: Double = 0.0,
)

data class TraceStep(
    val id: String = "",
    val agent: String = "",
    val hop: Int? = null,
    val title: String = "",
    val status: String = "",
    val thought: String = "",
    val action: String = "",
    val observation: String = "",
    val audit: String = "",
    val evidence: List<EvidenceRecord> = emptyList(),
    @SerializedName("latency_ms") val latencyMs: Double = 0.0,
)

data class FactRecord(
    val key: String = "",
    val subject: String = "",
    val predicate: String = "",
    val value: String = "",
    @SerializedName("source_id") val sourceId: String = "",
    @SerializedName("source_title") val sourceTitle: String = "",
    val confidence: Double = 0.0,
    val query: String = "",
    val hop: Int = 0,
    @SerializedName("created_at") val createdAt: String = "",
    val verified: Boolean = true,
)

data class KMetric(
    val k: Int = 3,
    @SerializedName("latency_ms") val latencyMs: Double = 0.0,
    @SerializedName("top_score") val topScore: Double = 0.0,
    @SerializedName("chunk_overlap") val chunkOverlap: Double = 0.0,
    @SerializedName("chunk_ids") val chunkIds: List<String> = emptyList(),
)

data class QueryMetrics(
    val k: Int = 3,
    @SerializedName("latency_ms") val latencyMs: Double = 0.0,
    @SerializedName("retrieval_calls") val retrievalCalls: Int = 0,
    @SerializedName("cache_hits") val cacheHits: Int = 0,
    @SerializedName("max_hops") val maxHops: Int = 3,
    @SerializedName("hops_used") val hopsUsed: Int = 0,
    @SerializedName("k_comparison") val kComparison: List<KMetric> = emptyList(),
)

data class QueryResponse(
    val id: String = "",
    @SerializedName("session_id") val sessionId: String = "",
    val question: String = "",
    val scenario: String = "",
    val answer: String = "",
    val verified: Boolean = false,
    val status: String = "",
    @SerializedName("sub_questions") val subQuestions: List<String> = emptyList(),
    val steps: List<TraceStep> = emptyList(),
    @SerializedName("facts_used") val factsUsed: List<FactRecord> = emptyList(),
    val metrics: QueryMetrics = QueryMetrics(),
    @SerializedName("created_at") val createdAt: String = "",
)

data class MemoryResponse(
    @SerializedName("session_id") val sessionId: String = "",
    val facts: List<FactRecord> = emptyList(),
    @SerializedName("trace_count") val traceCount: Int = 0,
)

data class ResetResponse(
    @SerializedName("session_id") val sessionId: String = "",
    val cleared: Int = 0,
    val message: String = "",
)

data class Annotation(
    val block: String = "",
    val thought: String? = null,
    val observation: String? = null,
    val audit: String? = null,
)

data class Scenario(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val question: String = "",
    val k: Int = 3,
    @SerializedName("max_hops") val maxHops: Int = 3,
    @SerializedName("simulate_wrong_first_hop") val simulateWrongFirstHop: Boolean = false,
    val expected: String = "",
    val annotations: List<Annotation> = emptyList(),
)

data class CorpusDocument(
    val id: String = "",
    val title: String = "",
    val entity: String = "",
    val text: String = "",
    @SerializedName("chunk_count") val chunkCount: Int = 0,
)

data class CorpusResponse(
    val backend: String = "",
    @SerializedName("document_count") val documentCount: Int = 0,
    @SerializedName("chunk_count") val chunkCount: Int = 0,
    val documents: List<CorpusDocument> = emptyList(),
)
