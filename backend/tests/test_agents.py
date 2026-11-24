import os
import sys
import unittest
from unittest.mock import patch

from agents import AgentPipeline, QueryRequest
from chroma_store import ChromaStore


class AgentPipelineRegressionTests(unittest.TestCase):
    def setUp(self) -> None:
        self.env = patch.dict(os.environ, {"LLM_PROVIDER": "none"})
        self.env.start()
        self.addCleanup(self.env.stop)
        self.modules = patch.dict(sys.modules, {"chromadb": None})
        self.modules.start()
        self.addCleanup(self.modules.stop)
        self.pipeline = AgentPipeline(ChromaStore())

    def run_query(self, question: str, **kwargs: object):
        return self.pipeline.run(
            QueryRequest(
                session_id=self._testMethodName,
                question=question,
                k=3,
                max_hops=3,
                **kwargs,
            )
        )

    @staticmethod
    def fact_values(response, predicate: str) -> list[str]:
        return [fact.value for fact in response.facts_used if fact.predicate == predicate]

    @staticmethod
    def fact_triples(response) -> list[tuple[str, str, str]]:
        return [(fact.subject, fact.predicate, fact.value) for fact in response.facts_used]

    def test_highest_mountain_and_range(self) -> None:
        response = self.run_query(
            "What is the highest mountain, and in which mountain range is it located?"
        )

        self.assertEqual(response.status, "verified")
        self.assertEqual(response.sub_questions[0], "What is the highest mountain?")
        self.assertEqual(
            response.sub_questions[1],
            "Which mountain range is Mount Everest located in?",
        )
        self.assertIn(
            ("Mount Everest", "attribute", "highest mountain above sea level"),
            self.fact_triples(response),
        )
        self.assertIn(("Mount Everest", "located_in", "Himalayas"), self.fact_triples(response))
        self.assertEqual(
            response.answer,
            "Mount Everest is the highest mountain above sea level, and it is located in the Himalayan mountain range.",
        )

    def test_alternate_highest_mountain_phrasing(self) -> None:
        response = self.run_query("Which mountain is the highest, and what range is it found in?")

        self.assertEqual(response.status, "verified")
        self.assertIn("highest mountain above sea level", self.fact_values(response, "attribute"))
        self.assertIn("Himalayas", self.fact_values(response, "located_in"))
        self.assertEqual(
            response.answer,
            "Mount Everest is the highest mountain above sea level, and it is located in the Himalayan mountain range.",
        )

    def test_scientist_and_other_element(self) -> None:
        response = self.run_query(
            "Which scientist discovered polonium, and what other element did she discover?"
        )

        self.assertEqual(response.status, "verified")
        self.assertIn(("Marie Curie", "discovered", "polonium"), self.fact_triples(response))
        self.assertIn(("Marie Curie", "discovered", "radium"), self.fact_triples(response))
        self.assertEqual(
            response.answer,
            "Marie Curie discovered polonium and radium, along with her husband Pierre Curie.",
        )

    def test_father_and_profession(self) -> None:
        response = self.run_query(
            "Who was Ada Lovelace's father, and what was his profession?"
        )

        self.assertEqual(response.status, "verified")
        self.assertIn(("Ada Lovelace", "father", "Lord Byron"), self.fact_triples(response))
        self.assertIn(
            ("Lord Byron", "profession", "English Romantic poet"),
            self.fact_triples(response),
        )
        self.assertEqual(
            response.answer,
            "Ada Lovelace's father was Lord Byron, an English Romantic poet.",
        )

    def test_father_and_death_year(self) -> None:
        response = self.run_query(
            "Who was the father of Ada Lovelace and in which year did he die?"
        )

        self.assertEqual(response.status, "verified")
        self.assertIn(("Ada Lovelace", "father", "Lord Byron"), self.fact_triples(response))
        self.assertIn(("Lord Byron", "death_year", "1824"), self.fact_triples(response))
        self.assertEqual(
            response.answer,
            "Ada Lovelace's father was Lord Byron, who died in 1824.",
        )

    def test_discovery_and_award_year(self) -> None:
        response = self.run_query(
            "Which elements did Marie Curie discover and in which year did she win the Nobel Prize in Physics?"
        )

        self.assertEqual(response.status, "verified")
        self.assertIn("polonium", self.fact_values(response, "discovered"))
        self.assertIn("radium", self.fact_values(response, "discovered"))
        self.assertIn("1903", self.fact_values(response, "award_year"))
        self.assertIn("polonium and radium", response.answer)
        self.assertIn("1903", response.answer)

    def test_largest_planet_and_storm(self) -> None:
        response = self.run_query("What is the largest planet and which storm is on it?")

        self.assertEqual(response.status, "verified")
        self.assertIn(
            ("Jupiter", "attribute", "largest planet in the Solar System"),
            self.fact_triples(response),
        )
        self.assertIn(("Jupiter", "storm", "Great Red Spot"), self.fact_triples(response))
        self.assertEqual(
            response.answer,
            "Jupiter is the largest planet in the Solar System, and it has a storm called the Great Red Spot.",
        )

    def test_missing_fact_abstains(self) -> None:
        response = self.run_query(
            "What was the exact date of the first UPDATE query executed at CERN in 1990?"
        )

        self.assertEqual(response.status, "abstained")
        self.assertEqual(response.answer, "Insufficient evidence in knowledge base")
        self.assertEqual(response.steps[-1].status, "abstain")

    def test_poisoned_hop_recovery(self) -> None:
        response = self.run_query(
            "Who was the father of Ada Lovelace and in which year did he die?",
            simulate_wrong_first_hop=True,
        )

        self.assertEqual(response.status, "corrected")
        statuses = [step.status for step in response.steps]
        self.assertIn("poisoned", statuses)
        self.assertIn("error", statuses)
        self.assertIn("correction", statuses)
        self.assertEqual(
            response.answer,
            "Ada Lovelace's father was Lord Byron, who died in 1824.",
        )

    def test_session_memory_is_reused(self) -> None:
        question = "Who was the father of Ada Lovelace and in which year did he die?"
        first = self.run_query(question)
        second = self.run_query(question)

        self.assertEqual(first.status, "verified")
        self.assertEqual(second.status, "verified")
        self.assertGreaterEqual(second.metrics.cache_hits, 1)
        self.assertEqual(len(self.pipeline.get_memory(self._testMethodName).facts), 2)


if __name__ == "__main__":
    unittest.main()
