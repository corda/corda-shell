package net.corda.tools.shell.jackson

import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.google.common.collect.testing.Helpers
import net.corda.client.jackson.JacksonSupport
import net.corda.client.jackson.StringToClassParser
import net.corda.client.jackson.getOrReport
import net.corda.core.contracts.TimeWindow
import net.corda.core.identity.CordaX500Name
import org.crsh.text.RenderPrintWriter
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StringToClassParserTest {
    private val printWriter: RenderPrintWriter = Mockito.mock(RenderPrintWriter::class.java)
    private val cut = StringToClassParser(TestQuery::class.java)

    @Before
    fun setup() {
        Mockito.doAnswer { invocation: InvocationOnMock ->
            println(invocation.getArgument<Any>(0).toString())
            null
        }.`when`(printWriter).println(ArgumentMatchers.any(String::class.java))
    }

    @Test(timeout = 30_000)
    fun `should parse multiple parameters`() {
        val input = """
            flowStates: PAUSED, flowClass: flowClass, compatibleWithCurrentCordaRuntime: true,
            suspensionDuration: PT10M, flowStart: {fromTime: ${Instant.parse("2020-03-31T09:59:51Z").epochSecond}},
            suspensionSources: [RECEIVE, SEND, SLEEP], counterParties: ["$ALICE_NAME", "$BOB_NAME"]
        """.trimIndent()

        val query = cut.parse(input, JacksonSupport.createNonRpcMapper(YAMLFactory())).getOrReport(printWriter)

        assertNotNull(query)
        assertEquals("flowClass", query!!.flowClass)
        assertEquals(1, query.flowStates?.size)
        assertEquals(TestState.PAUSED, query.flowStates?.get(0))
        assertTrue(query.compatibleWithCurrentCordaRuntime == true)
        assertEquals(Duration.of(10L, ChronoUnit.MINUTES), query.suspensionDuration)
        assertTrue(query.flowStart!!.contains(Instant.parse("2020-03-31T09:59:51Z")))
        assertEquals(3, query.suspensionSources!!.size)
        Helpers.assertContains(query.suspensionSources, TestSource.RECEIVE)
        Helpers.assertContains(query.suspensionSources, TestSource.SEND)
        Helpers.assertContains(query.suspensionSources, TestSource.SLEEP)
        assertEquals(2, query.counterParties!!.size)
        Helpers.assertContains(query.counterParties, BOB_NAME)
        Helpers.assertContains(query.counterParties, ALICE_NAME)
    }

    @Test(timeout = 30_000)
    fun `should return query with default values when there is no input parameters`() {
        val query = cut.parse("", JacksonSupport.createNonRpcMapper(YAMLFactory())).getOrThrow()

        assertEquals(TestQuery(), query)
    }

    @Test(timeout = 30_000)
    fun `should properly parse time window with upper and lower boundaries`() {
        val input = "flowStart: {fromTime: ${Instant.parse("2007-12-04T10:15:30Z").epochSecond}, " +
                "untilTime: ${Instant.parse("2007-12-05T10:15:30Z").epochSecond}}"

        val query = cut.parse(input, JacksonSupport.createNonRpcMapper(YAMLFactory())).getOrReport(printWriter)

        assertNotNull(query)
        assertNull(query!!.flowClass)
        assertNull(query.flowStates)
        assertNull(query.compatibleWithCurrentCordaRuntime)
        assertNull(query.suspensionDuration)
        assertEquals(TimeWindow.between(Instant.parse("2007-12-04T10:15:30Z"), Instant.parse("2007-12-05T10:15:30Z")), query.flowStart!!)
        assertNull(query.suspensionSources)
        assertNull(query.counterParties)
        assertNull(query.counterParties)
    }

    @Test(timeout = 30_000)
    fun `should properly parse time window with upper boundary`() {
        val input = "flowStart: {untilTime: ${Instant.parse("2007-12-04T10:15:30Z").epochSecond}}"

        val query = cut.parse(input, JacksonSupport.createNonRpcMapper(YAMLFactory())).getOrReport(printWriter)

        assertNotNull(query)
        assertNull(query!!.flowClass)
        assertNull(query.flowStates)
        assertNull(query.compatibleWithCurrentCordaRuntime)
        assertNull(query.suspensionDuration)
        assertEquals(TimeWindow.untilOnly(Instant.parse("2007-12-04T10:15:30Z")), query.flowStart!!)
        assertNull(query.suspensionSources)
        assertNull(query.counterParties)
    }

    @Test(timeout = 30_000)
    fun `should report unknown property`() {
        val input = "absolutelyUnknownProperty: RPC"

        val query = cut.parse(input, JacksonSupport.createNonRpcMapper(YAMLFactory())).getOrReport(printWriter)

        assertNull(query)
        Mockito.verify(printWriter, Mockito.atLeast(1))
            .println(ArgumentMatchers.any(String::class.java))
    }

    @Test(timeout = 30_000, expected = StringToClassParser.MappingException.UnknownParameter::class)
    fun `should throw exception for unknown property`() {
        val input = "absolutelyUnknownProperty: RPC"

        val query = cut.parse(input, JacksonSupport.createNonRpcMapper(YAMLFactory())).getOrThrow()

        assertNull(query)
        Mockito.verify(printWriter, Mockito.atLeast(1))
            .println(ArgumentMatchers.any(String::class.java))
    }

    companion object {
        private val ALICE_NAME = CordaX500Name("Alice Corp", "Madrid", "ES")
        private val BOB_NAME = CordaX500Name("Bob Corp", "London", "GB")
    }

    enum class TestState {
        FAILED,
        PAUSED
    }

    enum class TestSource {
        SEND,
        RECEIVE,
        SLEEP
    }

    data class TestQuery(
        val flowClass: String? = null,
        val flowStates: List<TestState>? = null,
        val compatibleWithCurrentCordaRuntime: Boolean? = null,
        val suspensionDuration: Duration? = null,
        val flowStart: TimeWindow? = null,
        val suspensionSources: List<TestSource>? = null,
        val counterParties: List<CordaX500Name>? = null
    )
}