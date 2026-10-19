package com.fabio.eagleeyes

import com.fabio.eagleeyes.pai.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ParentAnalysisRepositoryTest {
    private class Source : ParentContextStore {
        val children = listOf(AnalysisChild("a", "Jovem A"), AnalysisChild("b", "Jovem B"))
        val read = mutableListOf<String>()
        var revoked = false
        var verified = false
        override suspend fun children(selectedUid: String?) = children.filter { selectedUid == null || it.uid == selectedUid }
            .also { check(it.isNotEmpty()) { "Sem vínculo" } }
        override suspend fun evidence(child: AnalysisChild): ChildEvidence {
            read += child.uid
            return ChildEvidence(child, "2026-10-18: 120 minutos", listOf(
                AnalysisChatMessage("2026-10-18", "user", "Gosto de jogos ${child.uid}", 1),
                AnalysisChatMessage("2026-10-18", "assistant", "Qual jogo você prefere?", 2)
            ), "Histórico recente")
        }
        override suspend fun verifyAccess(children: List<AnalysisChild>) {
            check(!revoked) { "Vínculo removido" }
            verified = true
        }
    }

    @Test fun parentQuestionIncludesUsageAndBothSidesOfLinkedChildrenChat() = runTest {
        val source = Source()
        var prompt = ""
        val repository = ParentAnalysisRepository(source) { input ->
            assertTrue(source.verified)
            prompt = input
            "Resposta"
        }
        assertEquals("Resposta", repository.analyze("Como conversar com meus filhos?"))
        assertTrue(prompt.contains("120 minutos"))
        assertTrue(prompt.contains("JOVEM: Gosto de jogos a"))
        assertTrue(prompt.contains("JOVEM: Gosto de jogos b"))
        assertTrue(prompt.contains("CHATBOX (IA): Qual jogo"))
        assertTrue(prompt.contains("Como conversar com meus filhos?"))
    }

    @Test fun reportUsesOnlyTheSelectedLinkedChild() = runTest {
        val source = Source()
        var prompt = ""
        ParentAnalysisRepository(source) { prompt = it; "Relatório" }.analyze("Gere o relatório", "b")
        assertEquals(listOf("b"), source.read)
        assertTrue(prompt.contains("Jovem B"))
        assertFalse(prompt.contains("Jovem A"))
    }

    @Test fun missingOrRevokedLinkStopsTransmission() = runTest {
        for (revoked in listOf(false, true)) {
            val source = Source().apply { this.revoked = revoked }
            var transmitted = false
            val repository = ParentAnalysisRepository(source) { transmitted = true; "Resposta" }
            try {
                repository.analyze("Pergunta", if (revoked) "a" else "outro")
                fail("Deveria bloquear falta de vínculo")
            } catch (_: IllegalStateException) { }
            assertFalse(transmitted)
        }
    }

    @Test fun missingHistoryIsExplicitAndLargeContextIsBounded() {
        val child = AnalysisChild("a", "Jovem")
        val empty = ParentAnalysisPrompt.build("Relatório", listOf(ChildEvidence(child, "Sem dados", emptyList(), "Histórico indisponível")))
        assertTrue(empty.contains("Histórico indisponível"))
        val large = ParentAnalysisPrompt.build("Pergunta", listOf(ChildEvidence(child, "Uso", (0..200).map {
            AnalysisChatMessage("2026-10-18", "user", if (it == 200) "Fala mais recente" else "x".repeat(4000), it.toLong())
        }, "Contexto recente")))
        assertTrue(large.contains("Contexto abreviado"))
        assertTrue(large.contains("Fala mais recente"))
        assertTrue(large.length < 14_000)
    }
}
