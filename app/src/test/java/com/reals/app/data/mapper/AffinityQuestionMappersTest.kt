package com.reals.app.data.mapper

import com.reals.app.data.dto.AffinityAnswersResponseDto
import com.reals.app.data.dto.AffinityQuestionCatalogResponseDto
import com.reals.app.domain.model.AffinityAnswerType
import com.reals.app.testutil.testJson
import org.junit.Assert.assertEquals
import org.junit.Test

class AffinityQuestionMappersTest {
    @Test
    fun `full catalog decode preserves Spanish text and order`() {
        val dto = testJson.decodeFromString<AffinityQuestionCatalogResponseDto>(
            """
            {
              "catalogVersion": "v1",
              "categories": [
                {"id": "MUSIC", "title": "Música", "description": "Sonidos compartidos", "displayOrder": 2},
                {"id": "PLANS", "title": "Planes", "description": null, "displayOrder": 3}
              ],
              "questions": [
                {
                  "id": "MUSIC_DISCOVERY_001",
                  "semanticVersion": 1,
                  "contentVersion": 1,
                  "categoryId": "MUSIC",
                  "primaryTopic": "music_discovery",
                  "topicTags": ["music"],
                  "answerType": "ORDINAL_SCALE",
                  "prompt": "¿Qué tanto disfrutás descubrir música nueva con otra persona?",
                  "options": [
                    {"code": "LOW", "label": "Poco", "displayOrder": 1},
                    {"code": "VERY_HIGH", "label": "Mucho", "displayOrder": 2}
                  ]
                },
                {
                  "id": "PLANS_KIND_001",
                  "semanticVersion": 1,
                  "contentVersion": 1,
                  "categoryId": "PLANS",
                  "primaryTopic": "plans_kind",
                  "topicTags": [],
                  "answerType": "SINGLE_CHOICE",
                  "prompt": "¿Qué plan preferís?",
                  "options": [
                    {"code": "QUIET", "label": "Tranquilo", "displayOrder": 1}
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        val catalog = dto.toDomain()

        assertEquals("v1", catalog.catalogVersion)
        assertEquals(listOf("MUSIC", "PLANS"), catalog.categories.map { it.id })
        assertEquals("Música", catalog.categories.first().title)
        assertEquals(null, catalog.categories[1].description)
        assertEquals(listOf("MUSIC_DISCOVERY_001", "PLANS_KIND_001"), catalog.questions.map { it.id })
        assertEquals(listOf("LOW", "VERY_HIGH"), catalog.questions.first().options.map { it.code })
        assertEquals(AffinityAnswerType.OrdinalScale, catalog.questions[0].answerType)
        assertEquals(AffinityAnswerType.SingleChoice, catalog.questions[1].answerType)
    }

    @Test
    fun `unknown answer type maps to Unknown and missing optional arrays default empty`() {
        val dto = testJson.decodeFromString<AffinityQuestionCatalogResponseDto>(
            """
            {
              "catalogVersion": "v2",
              "categories": [
                {"id": "MUSIC", "title": "Música", "displayOrder": 1}
              ],
              "questions": [
                {
                  "id": "UNKNOWN_001",
                  "semanticVersion": 1,
                  "contentVersion": 1,
                  "categoryId": "MUSIC",
                  "primaryTopic": "future",
                  "answerType": "FUTURE_TYPE",
                  "prompt": "¿Pregunta futura?"
                }
              ]
            }
            """.trimIndent(),
        )

        val catalog = dto.toDomain()

        assertEquals(null, catalog.categories.single().description)
        assertEquals(emptyList<String>(), catalog.questions.single().topicTags)
        assertEquals(emptyList<String>(), catalog.questions.single().options.map { it.code })
        assertEquals(AffinityAnswerType.Unknown, catalog.questions.single().answerType)
    }

    @Test
    fun `answer list decode maps returned answers`() {
        val dto = testJson.decodeFromString<AffinityAnswersResponseDto>(
            """
            {
              "answers": [
                {
                  "questionId": "MUSIC_DISCOVERY_001",
                  "questionSemanticVersion": 1,
                  "answerCode": "VERY_HIGH",
                  "createdAt": "2026-08-04T18:00:00Z",
                  "updatedAt": "2026-08-04T18:00:00Z"
                }
              ]
            }
            """.trimIndent(),
        )

        val answer = dto.answers.single().toDomain()

        assertEquals("MUSIC_DISCOVERY_001", answer.questionId)
        assertEquals(1, answer.questionSemanticVersion)
        assertEquals("VERY_HIGH", answer.answerCode)
    }

    @Test
    fun `missing answers array defaults empty`() {
        val dto = testJson.decodeFromString<AffinityAnswersResponseDto>("{}")

        assertEquals(emptyList<String>(), dto.answers.map { it.questionId })
    }
}
