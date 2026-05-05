package org.medialiteracy.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalysisStagesTest {

    @Test
    fun testSummaryStagePromptBuilding() {
        val article = "Test Article"
        val prompt = SummaryStage.buildPrompt(article)
        assertTrue(prompt.contains(article))
    }

    @Test
    fun testSummaryStageParsingValidJson() {
        val rawJson = """
            {
              "summary": "This is a summary.",
              "objectivityScore": 85,
              "logicScore": 90,
              "evidenceQuality": 80,
              "credibilityScore": 88,
              "credibility": "Highly Credible",
              "primaryStrength": "Logic",
              "observationArea": "None"
            }
        """.trimIndent()
        
        val result = SummaryStage.parse(rawJson)
        assertEquals("This is a summary.", result.summary)
        assertEquals(85, result.objectivityScore)
    }

    @Test
    fun testSummaryStageParsingMarkdownWrapped() {
        val rawMarkdown = """
            Sure, here is the analysis:
            ```json
            {
              "summary": "Markdown summary.",
              "objectivityScore": 50,
              "logicScore": 50,
              "evidenceQuality": 50,
              "credibilityScore": 50,
              "credibility": "Neutral",
              "primaryStrength": "N/A",
              "observationArea": "N/A"
            }
            ```
            I hope this helps!
        """.trimIndent()

        val result = SummaryStage.parse(rawMarkdown)
        assertEquals("Markdown summary.", result.summary)
        assertEquals(50, result.objectivityScore)
    }

    @Test
    fun testFallacyStageParsing() {
        val rawFallacies = """
            #### 1. Slippery Slope
            * **Instance:** "If we allow X, then Y will surely follow."
            * **Analysis:** If we do A, B will happen.
            
            #### 2. Ad Hominem
            * **Instance:** "The author is clearly biased."
            * **Analysis:** Attacking the person.
        """.trimIndent()

        val fallacies = FallacyStage.parse(rawFallacies)
        assertEquals(2, fallacies.size)
        assertEquals("Slippery Slope", fallacies[0].type)
        assertEquals("Ad Hominem", fallacies[1].type)
        assertTrue(fallacies[0].evidence.contains("If we allow X"))
    }
}
