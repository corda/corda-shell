package net.corda.tools.shell

import net.corda.client.jackson.JacksonSupport
import net.corda.client.rpc.proxy.NodeFlowStatusRpcOps
import net.corda.core.utilities.ProgressTracker
import net.corda.nodeapi.flow.hospital.FlowCordappContext
import net.corda.nodeapi.flow.hospital.FlowInfo
import net.corda.nodeapi.flow.hospital.FlowState
import net.corda.nodeapi.flow.hospital.FlowStatusQueryV2
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.hamcrest.core.StringContains
import org.junit.Test
import org.mockito.Mockito
import java.io.CharArrayWriter
import java.io.PrintWriter
import java.time.Instant
import java.util.UUID

@SuppressWarnings("TooGenericExceptionCaught")
class FlowStatusCommandTest {

    @Test(timeout = 300_000)
    fun `it should be possible to lookup a flow by ID and view its status`() {
        val expectedFlowClass = "net.corda.failingflows.workflows.HospitalizerFlow"
        val id = UUID.randomUUID()
        val ops = Mockito.mock(NodeFlowStatusRpcOps::class.java)
        Mockito.`when`(ops.getFlowStatus(id.toString().toLowerCase()))
            .thenReturn(
                buildDummyFlowInfo(id, expectedFlowClass)
            )
        val arrayWriter = CharArrayWriter()
        val response = PrintWriter(arrayWriter).use {
            FlowStatusQueryCommand.queryById(it, listOf(id.toString().toLowerCase()), ops)
            it.flush()
            arrayWriter.toString()
        }
        MatcherAssert.assertThat(response, StringContains.containsString(FlowState.HOSPITALIZED.name))
        MatcherAssert.assertThat(response, StringContains.containsString(expectedFlowClass))
        MatcherAssert.assertThat(response, StringContains.containsString("flowId: \"${id.toString().toLowerCase()}\""))
    }

    @Test(timeout = 300_000)
    fun `it should be possible to lookup a flow by ID and have correctly formatted instants`() {
        val id = UUID.randomUUID()
        val ops = Mockito.mock(NodeFlowStatusRpcOps::class.java)
        val now = Instant.now()
        val then = now.plusSeconds(100)
        Mockito.`when`(ops.getFlowStatus(id.toString().toLowerCase()))
            .thenReturn(
                buildDummyFlowInfo(id, "net.corda.failingflows.workflows.HospitalizerFlow", flowStart = now, lastSuspend = then)
            )
        val arrayWriter = CharArrayWriter()
        val response = PrintWriter(arrayWriter).use {
            FlowStatusQueryCommand.queryById(it, listOf(id.toString().toLowerCase()), ops)
            it.flush()
            arrayWriter.toString()
        }
        MatcherAssert.assertThat(response, StringContains.containsString("flowId: \"${id.toString().toLowerCase()}\""))
        MatcherAssert.assertThat(
            response,
            StringContains.containsString("readable: \"${FlowStatusQueryCommand.INSTANT_FORMATTER.format(now)}\"")
        )
        MatcherAssert.assertThat(
            response,
            StringContains.containsString("readable: \"${FlowStatusQueryCommand.INSTANT_FORMATTER.format(then)}\"")
        )
        MatcherAssert.assertThat(response, StringContains.containsString("epochMillis: \"${now.toEpochMilli()}\""))
        MatcherAssert.assertThat(response, StringContains.containsString("epochMillis: \"${then.toEpochMilli()}\""))
    }

    @Test(timeout = 300_000)
    fun `it should be possible to query for a flow and retrieve it's id by flowState`() {
        val hospitaliseId = UUID.randomUUID()
        val failId = UUID.randomUUID()
        val ops = Mockito.mock(NodeFlowStatusRpcOps::class.java)
        val partyOps = Mockito.mock(JacksonSupport.PartyInfoRpcOps::class.java)
        Mockito.`when`(ops.getFlowsMatchingV2(FlowStatusQueryV2(flowStates = listOf(FlowState.HOSPITALIZED))))
            .thenReturn(
                listOf(hospitaliseId.toString().toLowerCase())
            )
        Mockito.`when`(ops.getFlowsMatchingV2(FlowStatusQueryV2(flowStates = listOf(FlowState.FAILED))))
            .thenReturn(
                listOf(failId.toString().toLowerCase())
            )
        Mockito.`when`(ops.getFlowsMatchingV2(FlowStatusQueryV2()))
            .thenReturn(
                listOf(
                    hospitaliseId.toString().toLowerCase(),
                    failId.toString().toLowerCase()
                )
            )
        //hospitalized only
        testQueryFlowStatus(
            ops = ops,
            input = listOf("flowStates:", "HOSPITALIZED"),
            expected = hospitaliseId.toString().toLowerCase(),
            mustExclude = failId.toString().toLowerCase(),
            partyOps = partyOps
        )
        //failed only
        testQueryFlowStatus(
            ops = ops,
            input = listOf("flowStates:", "FAILED"),
            expected = failId.toString().toLowerCase(),
            mustExclude = hospitaliseId.toString().toLowerCase(),
            partyOps = partyOps
        )
        //now check that both id's actually returned when listed without args
        testQueryFlowStatus(
            ops = ops,
            input = emptyList(),
            expected = hospitaliseId.toString().toLowerCase(),
            partyOps = partyOps
        )
        testQueryFlowStatus(
            ops = ops,
            input = emptyList(),
            expected = failId.toString().toLowerCase(),
            partyOps = partyOps
        )
    }

    @Test(timeout = 300_000)
    fun `it should be possible to query for a flow and retrieve it's id by initiating cordapp`() {
        val cordappId = "test-cordapp-id"
        val id = UUID.randomUUID()
        val ops = Mockito.mock(NodeFlowStatusRpcOps::class.java)
        val partyOps = Mockito.mock(JacksonSupport.PartyInfoRpcOps::class.java)
        Mockito.`when`(ops.getFlowsMatchingV2(FlowStatusQueryV2(cordapp = cordappId)))
            .thenReturn(
                listOf(id.toString().toLowerCase())
            )
        testQueryFlowStatus(
            ops = ops,
            partyOps = partyOps,
            input = listOf("cordapp:", cordappId),
            expected = id.toString().toLowerCase()
        )
    }

    private fun testQueryFlowStatus(
        ops: NodeFlowStatusRpcOps,
        input: List<String>,
        expected: String,
        mustExclude: String? = null,
        partyOps: JacksonSupport.PartyInfoRpcOps
    ) {
        val arrayWriter = CharArrayWriter()
        val response = PrintWriter(arrayWriter).use {
            FlowStatusQueryCommand.queryFlows(it, input, ops, partyOps)
            it.flush()
            arrayWriter.toString()
        }

        MatcherAssert.assertThat(response, StringContains.containsString(expected))
        mustExclude?.let {
            MatcherAssert.assertThat(response, CoreMatchers.not(StringContains.containsString(mustExclude)))
        }
    }

    private fun buildDummyFlowInfo(
        id: UUID,
        expectedFlowClass: String,
        flowStart: Instant = Instant.now(),
        lastSuspend: Instant = Instant.now()
    ): FlowInfo {
        val cordappContext = Mockito.mock(FlowCordappContext::class.java)
        return FlowInfo(
            id,
            flowClass = expectedFlowClass,
            flowState = FlowState.HOSPITALIZED,
            cordappContext = cordappContext,
            compatibleWithCurrentCordaRuntime = true,
            progressStep = ProgressTracker.Step("Start"),
            invocationContext = null,
            suspensionMetadata = null,
            flowStart = flowStart,
            lastCheckpoint = lastSuspend
        )
    }
}
