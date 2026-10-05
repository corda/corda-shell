package net.corda.tools.shell

import net.corda.client.rpc.proxy.NodeFlowStatusRpcOps
import net.corda.nodeapi.flow.hospital.FlowTimeWindow
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.ext.api.flow.FlowStatusQueryProvider
import net.corda.extensions.node.rpc.NodeFlowStatusRpcOpsImpl
import net.corda.nodeapi.flow.hospital.FlowState
import net.corda.nodeapi.flow.hospital.FlowStatusQueryV2
import net.corda.nodeapi.flow.hospital.InvocationSource
import net.corda.nodeapi.flow.hospital.SuspensionSource
import org.crsh.command.InvocationContext
import org.crsh.shell.impl.command.CRaSHSession
import org.crsh.text.RenderPrintWriter
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.argThat
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.ArrayList

class FlowStatusQueryCommandTest {
    private val mockInvocationContext: InvocationContext<Map<Any, Any>> = mock()
    private lateinit var queryCommand: FlowStatusQueryCommand
    private lateinit var queryProvider: FlowStatusQueryProvider

    @Before
    fun setup() {
        val session = Mockito.mock(CRaSHSession::class.java)
        val authInfo = Mockito.mock(SshAuthInfo::class.java)
        Mockito.`when`(mockInvocationContext.session).thenReturn(session)
        Mockito.`when`(session.authInfo).thenReturn(authInfo)
        val statusRpcOps = NodeFlowStatusRpcOpsImpl()
        val partyInfoRpcOps = Mockito.mock(CordaRPCOps::class.java)
        Mockito.`when`<Any>(authInfo.getOrCreateRpcOps(ArgumentMatchers.any())).thenAnswer { a: InvocationOnMock ->
            when (a.getArgument<Class<*>>(0)) {
                NodeFlowStatusRpcOps::class.java -> return@thenAnswer statusRpcOps
                CordaRPCOps::class.java -> return@thenAnswer partyInfoRpcOps
                else -> return@thenAnswer null
            }
        }
        queryProvider = Mockito.mock(FlowStatusQueryProvider::class.java)
        statusRpcOps.flowStatusQueryProvider = queryProvider
        queryCommand = FlowStatusQueryCommand()
        queryCommand.context = mockInvocationContext
        val printWriter = Mockito.mock(RenderPrintWriter::class.java)
        Mockito.doAnswer { invocation: InvocationOnMock ->
            println(invocation.getArgument<Any>(0).toString())
            null
        }.`when`(printWriter).println(ArgumentMatchers.any(String::class.java))
        Mockito.`when`(mockInvocationContext.writer).thenReturn(printWriter)
    }

    @Test(timeout = 30_000)
    fun testMultipleParamsAreParsedCorrectly() {
        val queryStrings = listOf(
            """
            flowStates: PAUSED, flowClass: flowClass, cordapp: accounts, progressStep: "Signing Tx", compatibleWithCurrentCordaRuntime: true,
            suspensionDuration: "10 MINUTES", flowStart: {fromTime: "2020-03-31T09:59:51.312Z"}, invocationSources: [RPC],
            startedBy: partyA, suspensionSources: [RECEIVE, SEND], counterParties: ["$ALICE_NAME", "$BOB_NAME"]
    """.trimIndent()
        )

        queryCommand.queryFlows(mockInvocationContext, queryStrings)

        Mockito.verify(queryProvider).getFlowsMatchingV2(
            argThat {
                flowClass.equals("flowClass")
                        && flowStates!!.size == 1
                        && flowStates!!.contains(FlowState.PAUSED)
                        && progressStep.equals("Signing Tx")
                        && cordapp.equals("accounts")
                        && compatibleWithCurrentCordaRuntime == true
                        && suspensionDuration!!.compareTo(Duration.of(10L, ChronoUnit.MINUTES)) == 0
                        && flowStart!!.fromTime == Instant.parse("2020-03-31T09:59:51.312Z")
                        && invocationSources!!.size == 1
                        && invocationSources!!.contains(InvocationSource.RPC)
                        && startedBy.equals("partyA")
                        && suspensionSources!!.size == 2
                        && suspensionSources!!.contains(SuspensionSource.RECEIVE)
                        && suspensionSources!!.contains(SuspensionSource.SEND)
                        && counterParties!!.size == 2
                        && counterParties!!.contains(BOB_NAME)
                        && counterParties!!.contains(ALICE_NAME)
            }
        )
    }

    @Test(timeout = 30_000)
    fun testNoParamsResultsInEmptyQueryObject() {
        val queryStrings: List<String> = ArrayList()
        queryCommand.queryFlows(mockInvocationContext, queryStrings)
        Mockito.verify(queryProvider).getFlowsMatchingV2(argThat { equals(FlowStatusQueryV2()) })
    }

    @Test(timeout = 30_000)
    fun testFlowStartBetween() {
        val queryStrings = listOf(
            "flowStart: {fromTime: \"2007-12-04T10:15:30.00Z\", untilTime: \"2007-12-05T10:15:30.00Z\"}"
        )
        queryCommand.queryFlows(mockInvocationContext, queryStrings)
        Mockito.verify(queryProvider).getFlowsMatchingV2(argThat {
            equals(
                FlowStatusQueryV2(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    FlowTimeWindow.between(Instant.parse("2007-12-04T10:15:30.00Z"), Instant.parse("2007-12-05T10:15:30.00Z")),
                    null,
                    null,
                    null,
                    null
                )
            )
        })
    }

    @Test(timeout = 30_000)
    fun testFlowStartUntil() {
        val queryStrings = listOf("flowStart: {untilTime: \"2007-12-04T10:15:30.00Z\"}")
        queryCommand.queryFlows(mockInvocationContext, queryStrings)
        Mockito.verify(queryProvider).getFlowsMatchingV2(argThat {
            equals(
                FlowStatusQueryV2(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    FlowTimeWindow.untilOnly(Instant.parse("2007-12-04T10:15:30.00Z")),
                    null,
                    null,
                    null,
                    null
                )
            )
        })
    }

    companion object {
        private val ALICE_NAME = CordaX500Name("Alice Corp", "Madrid", "ES")
        private val BOB_NAME = CordaX500Name("Bob Corp", "London", "GB")
    }
}
