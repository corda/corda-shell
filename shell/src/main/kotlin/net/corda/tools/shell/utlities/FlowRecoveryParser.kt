package net.corda.tools.shell.utlities

import net.corda.client.jackson.StringToClassParser
import net.corda.client.jackson.getOrReport
import net.corda.client.rpc.proxy.FlowRPCOps
import net.corda.core.flows.FlowRecoveryQuery
import net.corda.core.flows.StateMachineRunId
import net.corda.core.messaging.CordaRPCOps
import net.corda.tools.shell.InteractiveShell
import net.corda.tools.shell.flattenInput
import java.io.PrintWriter

fun queryRecoveryFlows(writer: PrintWriter, input: List<String>?, ops: FlowRPCOps, partyOps: CordaRPCOps, forceRecover: Boolean? = null): Map<StateMachineRunId, Boolean> {
    val query = StringToClassParser(FlowRecoveryQuery::class.java)
        .parse(input.flattenInput(), InteractiveShell.createYamlInputMapper(partyOps))
        .getOrReport(writer)
    val result = query?.let { q ->
        forceRecover?.let { ops.recoverFinalityFlowsMatching(q, it) }
            ?: ops.recoverFinalityFlowsMatching(q) }
            ?: emptyMap()
    result.forEach { id ->
        writer.write("\t$id")
    }
    return result
}

fun queryRecoveryFlows(writer: PrintWriter, input: List<String>?, ops: FlowRPCOps, partyOps: CordaRPCOps) =
    queryRecoveryFlows(writer, input, ops, partyOps, null)