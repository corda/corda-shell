package net.corda.tools.shell

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.readValue
import net.corda.core.contracts.TimeWindow
import net.corda.core.contracts.UniqueIdentifier
import net.corda.nodeapi.flow.hospital.FlowTimeWindow
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.test.assertEquals

class CustomTypeJsonParsingTests {
    lateinit var objectMapper: ObjectMapper

    //Dummy classes for testing.
    data class State(val linearId: UniqueIdentifier) {
        constructor() : this(UniqueIdentifier("required-for-json-deserializer"))
    }

    data class UuidState(val uuid: UUID) {
        //Default constructor required for json deserializer.
        constructor() : this(UUID.randomUUID())
    }

    data class Query(
        val suspensionDuration: Duration? = null,
        val flowStart: TimeWindow? = null,
        val flowStartAsFlowTimeWindow: FlowTimeWindow? = null
    )

    @Before
    fun setup() {
        objectMapper = ObjectMapper()
        val simpleModule = SimpleModule()
        simpleModule.addDeserializer(UniqueIdentifier::class.java, UniqueIdentifierDeserializer)
        simpleModule.addDeserializer(Duration::class.java, InputDurationDeserializer)
        simpleModule.addDeserializer(TimeWindow::class.java, InputTimeWindowDeserializer)
        simpleModule.addDeserializer(FlowTimeWindow::class.java, InputFlowTimeWindowDeserializer)
        objectMapper.registerModule(simpleModule)
    }

    @Test(timeout = 300_000)
    fun `Deserializing UniqueIdentifier by parsing string`() {
        val id = "26b37265-a1fd-4c77-b2e0-715917ef619f"
        val json = """{"linearId":"$id"}"""
        val state = objectMapper.readValue<State>(json)

        assertEquals(id, state.linearId.id.toString())
    }

    @Test(timeout = 300_000)
    fun `Deserializing UniqueIdentifier by parsing string with underscore`() {
        val json = """{"linearId":"extkey564_26b37265-a1fd-4c77-b2e0-715917ef619f"}"""
        val state = objectMapper.readValue<State>(json)

        assertEquals("extkey564", state.linearId.externalId)
        assertEquals("26b37265-a1fd-4c77-b2e0-715917ef619f", state.linearId.id.toString())
    }

    @Test(expected = JsonMappingException::class, timeout = 300_000)
    fun `Deserializing by parsing string contain invalid uuid with underscore`() {
        val json = """{"linearId":"extkey564_26b37265-a1fd-4c77-b2e0"}"""
        objectMapper.readValue<State>(json)
    }

    @Test(timeout = 300_000)
    fun `Deserializing UUID by parsing string`() {
        val json = """{"uuid":"26b37265-a1fd-4c77-b2e0-715917ef619f"}"""
        val state = objectMapper.readValue<UuidState>(json)

        assertEquals("26b37265-a1fd-4c77-b2e0-715917ef619f", state.uuid.toString())
    }

    @Test(expected = JsonMappingException::class, timeout = 300_000)
    fun `Deserializing UUID by parsing invalid uuid string`() {
        val json = """{"uuid":"26b37265-a1fd-4c77-b2e0"}"""
        objectMapper.readValue<UuidState>(json)
    }

    @Test(timeout = 30_000)
    fun `Deserializing Duration by parsing human readable string`() {
        val json = """{
            "suspensionDuration":"15 SECONDS"
        }""".trimIndent()

        val query = objectMapper.readValue<Query>(json)

        assertEquals(Duration.of(15L, ChronoUnit.SECONDS), query.suspensionDuration)
    }

    @Test(timeout = 30_000)
    fun `Deserializing Duration by parsing ISO 8601 string`() {
        val json = """{
            "suspensionDuration":"PT10M"
        }""".trimIndent()

        val query = objectMapper.readValue<Query>(json)

        assertEquals(Duration.of(10L, ChronoUnit.MINUTES), query.suspensionDuration)
    }

    @Test(timeout = 30_000)
    fun `Deserializing TimeWindow by parsing string`() {
        val json = """{
            "flowStart": {
                "fromTime": "2007-12-04T10:15:30.00Z", 
                "untilTime": "2007-12-05T10:15:30.00Z"
            }
        }""".trimIndent()

        val query = objectMapper.readValue<Query>(json)

        assertEquals(
            TimeWindow.between(Instant.parse("2007-12-04T10:15:30.00Z"), Instant.parse("2007-12-05T10:15:30.00Z")),
            query.flowStart
        )
    }

    @Test(timeout = 30_000)
    fun `Deserializing TimeWindow by parsing string without Z qualifier but still assuming UTC`() {
        val json = """{
            "flowStart": {
                "fromTime": "2007-12-04T10:15:30.00", 
                "untilTime": "2007-12-05T10:15:30.00"
            }
        }""".trimIndent()

        val query = objectMapper.readValue<Query>(json)

        assertEquals(
            TimeWindow.between(Instant.parse("2007-12-04T10:15:30.00Z"), Instant.parse("2007-12-05T10:15:30.00Z")),
            query.flowStart
        )
    }

    @Test(timeout = 30_000, expected = JsonMappingException::class)
    fun `Fails deserializing TimeWindow if boundaries are not specified`() {
        val json = """{
            "flowStart": {
            }
        }""".trimIndent()

        objectMapper.readValue<Query>(json)
    }

    @Test(timeout = 30_000, expected = JsonMappingException::class)
    fun `Fails deserializing TimeWindow if boundaries are null`() {
        val json = """{
            "flowStart": {
                "fromTime": null, 
                "untilTime": null
            }
        }""".trimIndent()

        objectMapper.readValue<Query>(json)
    }

    @Test(timeout = 30_000)
    fun `Deserializing until TimeWindow by parsing string`() {
        val json = """{
            "flowStart": {
                "untilTime": "2007-12-05T10:15:30.00Z"
            }
        }""".trimIndent()

        val query = objectMapper.readValue<Query>(json)

        assertEquals(
            TimeWindow.untilOnly(Instant.parse("2007-12-05T10:15:30.00Z")),
            query.flowStart
        )
    }

    @Test(timeout = 30_000)
    fun `Deserializing from FlowTimeWindow by parsing string`() {
        val json = """{
            "flowStartAsFlowTimeWindow": {
                "fromTime": "2007-12-04T10:15:30.00Z"
            }
        }""".trimIndent()

        val query = objectMapper.readValue<Query>(json)

        assertEquals(
            FlowTimeWindow.fromOnly(Instant.parse("2007-12-04T10:15:30.00Z")),
            query.flowStartAsFlowTimeWindow
        )
    }

    @Test(timeout = 30_000)
    fun `Deserializing FlowTimeWindow by parsing string`() {
        val json = """{
            "flowStartAsFlowTimeWindow": {
                "fromTime": "2007-12-04T10:15:30.00Z", 
                "untilTime": "2007-12-05T10:15:30.00Z"
            }
        }""".trimIndent()

        val query = objectMapper.readValue<Query>(json)

        assertEquals(
            FlowTimeWindow.between(Instant.parse("2007-12-04T10:15:30.00Z"), Instant.parse("2007-12-05T10:15:30.00Z")),
            query.flowStartAsFlowTimeWindow
        )
    }

    @Test(timeout = 30_000)
    fun `Deserializing FlowTimeWindow by parsing string without Z qualifier but still assuming UTC`() {
        val json = """{
            "flowStartAsFlowTimeWindow": {
                "fromTime": "2007-12-04T10:15:30.00", 
                "untilTime": "2007-12-05T10:15:30.00"
            }
        }""".trimIndent()

        val query = objectMapper.readValue<Query>(json)

        assertEquals(
            FlowTimeWindow.between(Instant.parse("2007-12-04T10:15:30.00Z"), Instant.parse("2007-12-05T10:15:30.00Z")),
            query.flowStartAsFlowTimeWindow
        )
    }

    @Test(timeout = 30_000, expected = JsonMappingException::class)
    fun `Fails deserializing FlowTimeWindow if boundaries are not specified`() {
        val json = """{
            "flowStartAsFlowTimeWindow": {
            }
        }""".trimIndent()

        objectMapper.readValue<Query>(json)
    }

    @Test(timeout = 30_000, expected = JsonMappingException::class)
    fun `Fails deserializing FlowTimeWindow if boundaries are null`() {
        val json = """{
            "flowStartAsFlowTimeWindow": {
                "fromTime": null, 
                "untilTime": null
            }
        }""".trimIndent()

        objectMapper.readValue<Query>(json)
    }

    @Test(timeout = 30_000)
    fun `Deserializing until FlowTimeWindow by parsing string`() {
        val json = """{
            "flowStartAsFlowTimeWindow": {
                "untilTime": "2007-12-05T10:15:30.00Z"
            }
        }""".trimIndent()

        val query = objectMapper.readValue<Query>(json)

        assertEquals(
            FlowTimeWindow.untilOnly(Instant.parse("2007-12-05T10:15:30.00Z")),
            query.flowStartAsFlowTimeWindow
        )
    }

    @Test(timeout = 30_000)
    fun `Deserializing from TimeWindow by parsing string`() {
        val json = """{
            "flowStartAsFlowTimeWindow": {
                "fromTime": "2007-12-04T10:15:30.00Z"
            }
        }""".trimIndent()

        val query = objectMapper.readValue<Query>(json)

        assertEquals(
            FlowTimeWindow.fromOnly(Instant.parse("2007-12-04T10:15:30.00Z")),
            query.flowStartAsFlowTimeWindow
        )
    }
}