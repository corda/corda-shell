package net.corda.tools.shell

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import net.corda.client.jackson.StringToClassParser
import net.corda.client.jackson.getOrReport
import net.corda.client.rpc.proxy.NodeFlowStatusRpcOps
import net.corda.core.internal.VisibleForTesting
import net.corda.core.messaging.CordaRPCOps
import net.corda.nodeapi.flow.hospital.FlowStatusQueryV2
import org.crsh.cli.Argument
import org.crsh.cli.Command
import org.crsh.cli.Man
import org.crsh.cli.Usage
import org.crsh.command.InvocationContext
import java.io.PrintWriter
import java.time.Instant
import java.time.format.DateTimeFormatter

@VisibleForTesting
internal class FlowStatusQueryCommand : MultiRpcInteractiveShellCommand() {
    @Command
    @Man(
        "Lists all flows with a checkpoint in the node that have not completed\n" +
                "available arguments are: \n" +
                "\tcompatibleWithCurrentCordaRuntime: Boolean [true/false] - whether the flow has been marked as incompatible with current corda runtime\n" +
                "\t cordapp: String [fragment of cordapp name] - the name of the cordapp which the flow was present in\n" +
                "\t flowClass: String [fragment of FlowLogic class] - the name of the class which implements the flow\n" +
                "\t flowStartFrom: String [ISO8601 DateTime] - the start of a time window where the flow was started - if not present taken to be 0 unix timestamp\n" +
                "\t flowStartUntil: String [ISO8601 DateTime] - the end of a time window where the flow was started - if not present taken to be the current utc unix timestamp\n" +
                "\t flowStates: String [state of the flow on latest checkpoint] - one of RUNNABLE, FAILED, COMPLETED, HOSPITALIZED, KILLED, PAUSED, can be specified as an array like [KILLED, PAUSED] - refer to documentation\n" +
                "\t progressStep: String [fragment of user defined progress step] - if the flow implements progress tracking, the latest step that was encountered before checkpointing\n" +
                "\t suspensionDuration: Duration [Java duration] - the minimum time that a flow must have been checkpointed. This is entered in format \"<size>, unit\" where unit is one of SECONDS, MINUTES, HOURS or DAYS\n" +
                "\t invocationSources: String [how the flow was started] - one of RPC, SERVICE, SCHEDULED, INITIATED, can be specified as an array like [RPC, SERVICE] - refer to documentation\n" +
                "\t startedBy: String - user that started the flow\n" +
                "\t suspensionSources: String [reason why the flow was suspended] - one of SEND, RECEIVE, SLEEP, EXTERNAL_OPERATION, CLOSE_SESSIONS, WAIT_FOR_LEDGER_COMMIT, GET_FLOW_INFO, WAIT_FOR_SESSIONS_CONFIRMATIONS, SEND_AND_RECEIVE, UNKNOWN, can be specified as an array like [SEND, SLEEP] - refer to documentation\n" +
                "\t counterParties: String [X509 name, like O=PartyA,L=London,C=GB] - the name of the counter party which the flow is waiting for, , can be specified as an array like [\"O=PartyA,L=London,C=GB\", \"O=PartyB,L=London,C=GB\"]\n"
    )
    @Usage(
        "Lists all flows with a checkpoint in the node that have not completed\n" +
                "\t\tavailable arguments are: \n" +
                "\t\tcompatibleWithCurrentCordaRuntime: Boolean [true/false] - whether the flow has been marked as incompatible with current corda runtime\n" +
                "\t\tcordapp: String [fragment of cordapp name] - the name of the cordapp which the flow was present in\n" +
                "\t\tflowClass: String [fragment of FlowLogic class] - the name of the class which implements the flow\n" +
                "\t\tflowStartFrom: String [ISO8601 DateTime] - the start of a time window where the flow was started - if not present taken to be 0 unix timestamp\n" +
                "\t\tflowStartUntil: String [ISO8601 DateTime] - the end of a time window where the flow was started - if not present taken to be the current utc unix timestamp\n" +
                "\t\tflowStates: String [state of the flow on latest checkpoint] - one of RUNNABLE, FAILED, COMPLETED, HOSPITALIZED, KILLED, PAUSED, can be specified as an array like [KILLED, PAUSED] - refer to documentation\n" +
                "\t\tprogressStep: String [fragment of user defined progress step] - if the flow implements progress tracking, the latest step that was encountered before checkpointing\n" +
                "\t\tsuspensionDuration: Duration [Java duration] - the minimum time that a flow must have been checkpointed. This is entered in format \"<size>, unit\" where unit is one of SECONDS, MINUTES, HOURS or DAYS\n" +
                "\t\tinvocationSources: String [how the flow was started] - one of RPC, SERVICE, SCHEDULED, INITIATED, can be specified as an array like [RPC, SERVICE] - refer to documentation\n" +
                "\t\tstartedBy: String - user that started the flow\n" +
                "\t\tsuspensionSources: String [reason why the flow was suspended] - one of SEND, RECEIVE, SLEEP, EXTERNAL_OPERATION, CLOSE_SESSIONS, WAIT_FOR_LEDGER_COMMIT, GET_FLOW_INFO, WAIT_FOR_SESSIONS_CONFIRMATIONS, SEND_AND_RECEIVE, UNKNOWN, can be specified as an array like [SEND, SLEEP] - refer to documentation\n" +
                "\t\tcounterParties: String [X509 name, like O=PartyA,L=London,C=GB] - the name of the counter party which the flow is waiting for, , can be specified as an array like [\"O=PartyA,L=London,C=GB\", \"O=PartyB,L=London,C=GB\"]\n"
    )
    @SuppressWarnings("unused")
    fun queryFlows(context: InvocationContext<Map<Any, Any>>, @Argument(unquote = true) input: List<String>?) {
        queryFlows(context.writer, input, ops(NodeFlowStatusRpcOps::class.java), ops(CordaRPCOps::class.java))
    }

    @Command
    @Man("Lists flow information for check pointed flow by ID")
    @Usage(
        "Lists flow information for check pointed flow by ID\n" +
                "queryById <id1> <id2> <id3> will return checkpoint data on id1, id2, id3"
    )
    fun queryById(context: InvocationContext<Map<Any, Any>>, @Argument(unquote = true) input: List<String>?) {
        queryById(context.writer, input, ops(NodeFlowStatusRpcOps::class.java))
    }

    companion object {
        val INSTANT_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT
        private val INSTANT_JSON_SERIALIZER: JsonSerializer<Instant> = object : JsonSerializer<Instant>() {
            override fun serialize(value: Instant, gen: JsonGenerator, serializers: SerializerProvider) {
                gen.writeStartObject()
                gen.writeStringField("readable", INSTANT_FORMATTER.format(value))
                gen.writeStringField("epochMillis", value.toEpochMilli().toString())
                gen.writeEndObject()
            }
        }

        fun queryFlows(writer: PrintWriter, input: List<String>?, ops: NodeFlowStatusRpcOps, partyOps: CordaRPCOps) {
            val query = StringToClassParser(FlowStatusQueryV2::class.java)
                .parse(input.flattenInput(), InteractiveShell.createYamlInputMapper(partyOps))
                .getOrReport(writer)
            val result = query?.let { ops.getFlowsMatchingV2(it) } ?: emptyList()
            result.forEach { id ->
                writer.write("\t$id")
            }
        }

        @Suppress("TooGenericExceptionCaught")
        fun queryById(writer: PrintWriter, input: List<String>?, ops: NodeFlowStatusRpcOps) {
            val results = input?.mapNotNull { id ->
                ops.getFlowStatus(id)
            } ?: listOf()
            if (results.isEmpty()) {
                writer.println("No Results")
            } else {
                try {
                    val additionalSerializers = SimpleModule()
                    additionalSerializers.addSerializer(Instant::class.java, INSTANT_JSON_SERIALIZER)
                    val mapper = ObjectMapper(YAMLFactory())
                    mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                    mapper.registerModule(JavaTimeModule())
                    mapper.registerModule(additionalSerializers)
                    mapper.writeValue(writer, results)
                } catch (e: Exception) {
                    writer.println("failed to print query result to console")
                    throw e
                }
            }
        }
    }
}