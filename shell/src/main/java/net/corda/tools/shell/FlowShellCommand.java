package net.corda.tools.shell;

// See the comments at the top of run.java

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import net.corda.client.rpc.proxy.FlowRPCOps;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.StateMachineRunId;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.tools.shell.utlities.ANSIProgressRenderer;
import net.corda.tools.shell.utlities.CRaSHANSIProgressRenderer;
import org.crsh.cli.*;
import org.crsh.command.InvocationContext;
import org.crsh.text.Color;
import org.crsh.text.Decoration;
import org.crsh.text.RenderPrintWriter;
import org.crsh.text.ui.TableElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static net.corda.tools.shell.InteractiveShell.killFlowById;
import static net.corda.tools.shell.InteractiveShell.parseStateMachineRunId;
import static net.corda.tools.shell.InteractiveShell.runFlowByNameFragment;
import static net.corda.tools.shell.InteractiveShell.runStateMachinesView;
import static net.corda.tools.shell.utlities.FlowRecoveryParserKt.queryRecoveryFlows;

@Man(
    "Allows you to start, kill, pause, retry and recover flows, list the ones available and to watch flows currently running on the node.\n\n" +
        "Starting flow is the primary way in which you command the node to change the ledger.\n\n" +
        "This command is generic, so the right way to use it depends on the flow you wish to start. You can use the 'flow start'\n" +
        "command with either a full class name, or a substring of the class name that's unambiguous. The parameters to the \n" +
        "flow constructors (the right one is picked automatically) are then specified using the same syntax as for the run command."
)
@Named("flow")
public class FlowShellCommand extends CordaRpcOpsShellCommand {

    private static final Logger logger = LoggerFactory.getLogger(FlowShellCommand.class);

    @VisibleForTesting
    FlowRPCShellCommand rpcShellCommand;

    private FlowRPCShellCommand getFlowRPCShellCommand() {
        if (rpcShellCommand == null) {
            rpcShellCommand = new FlowRPCShellCommand(context);
        }
        return rpcShellCommand;
    }

    @Command
    @Usage("Start a (work)flow on the node. This is how you can change the ledger.\n\n" +
            "\t\t    Starting flow is the primary way in which you command the node to change the ledger.\n" +
            "\t\t    This command is generic, so the right way to use it depends on the flow you wish to start. You can use the 'flow start'\n" +
            "\t\t    command with either a full class name, or a substring of the class name that's unambiguous. The parameters to the\n" +
            "\t\t    flow constructors (the right one is picked automatically) are then specified using the same syntax as for the run command.\n")
    public void start(
            @Usage("The class name of the flow to run, or an unambiguous substring") @Argument String name,
            @Usage("The data to pass as input") @Argument(unquote = false) List<String> input
    ) {
        logger.info("Executing command \"flow start {} {}\",", name, (input != null) ? String.join(" ", input) : "<no arguments>");
        startFlow(name, input, out, ops(), ansiProgressRenderer(), objectMapper(null));
    }

    // TODO Limit number of flows shown option?
    @Command
    @Usage("Watch information about state machines running on the node with result information.")
    @SuppressWarnings("unused")
    public void watch(InvocationContext<TableElement> context) {
        logger.info("Executing command \"flow watch\".");
        runStateMachinesView(out, ops());
    }

    static void startFlow(
            @Usage("The class name of the flow to run, or an unambiguous substring") @Argument String name,
            @Usage("The data to pass as input") @Argument(unquote = false) List<String> input,
            RenderPrintWriter out,
            CordaRPCOps rpcOps,
            ANSIProgressRenderer ansiProgressRenderer,
            ObjectMapper om
    ) {
        if (name == null) {
            out.println("You must pass a name for the flow. Example: \"start Yo target: Some other company\"", Decoration.bold, Color.red);
            return;
        }
        String inp = input == null ? "" : String.join(" ", input).trim();
        runFlowByNameFragment(
                name,
                inp,
                out,
                rpcOps,
                ansiProgressRenderer != null ? ansiProgressRenderer : new CRaSHANSIProgressRenderer(out),
                om
        );
    }

    @Command
    @Usage("List flows that user can start.")
    public void list(InvocationContext<String> context) throws Exception {
        logger.info("Executing command \"flow list\".");
        for (String name : ops().registeredFlows()) {
            context.provide(name + System.lineSeparator());
        }
    }

    @Command
    @Usage("Kill a flow that is running on this node.")
    public void kill(
            @Usage("The UUID for the flow that we wish to kill") @Argument String id
    ) {
        logger.info("Executing command \"flow kill {}\".", id);
        killFlowById(id, out, ops(), objectMapper(null));
    }

    @Command
    @Usage("Pause a flow that is running on this node.")
    public void pause(
            @Usage("The UUID for the flow that we wish to pause") @Argument String id
    ) {
        logger.info("Executing command \"flow pause {}\".", id);
        StateMachineRunId parsedId = parseStateMachineRunId(id, out, objectMapper(null));
        if (getFlowRPCShellCommand().pause(parsedId)) {
            out.println("Paused flow " + parsedId, Decoration.bold, Color.yellow);
        } else {
            out.println("Failed to pause flow " + parsedId, Decoration.bold, Color.red);
        }
    }

    @Command
    @Usage("Pause all flows that are running on this node.")
    public void pauseAll() {
        logger.info("Executing command \"flow pauseAll\".");
        if (getFlowRPCShellCommand().pauseAll()) {
            out.println("Pausing all flows succeeded.", Decoration.bold, Color.yellow);
        } else {
            out.println("Failed to pause one or more flows.", Decoration.bold, Color.red);
        }
    }

    @Command
    @Usage("Pause all Hospitalized flows that are running on this node.")
    public void pauseAllHospitalized() {
        logger.info("Executing command \"flow pauseAllHospitalized\".");
        if (getFlowRPCShellCommand().pauseAllHospitalized()) {
            out.println("Pausing all Hospitalized flows succeeded.", Decoration.bold, Color.yellow);
        } else {
            out.println("Failed to pause one or more Hospitalized flows.", Decoration.bold, Color.red);
        }
    }

    @Command
    @Usage("Retry a flow that is running on this node.")
    public void retry(
            @Usage("The UUID for the flow that we wish to retry") @Argument String id
    ) {
        logger.info("Executing command \"flow retry {}\".", id);
        StateMachineRunId parsedId = parseStateMachineRunId(id, out, objectMapper(null));
        if (getFlowRPCShellCommand().retry(parsedId)) {
            out.println("Retried flow " + parsedId, Decoration.bold, Color.yellow);
        } else {
            out.println("Failed to retry flow " + parsedId, Decoration.bold, Color.red);
        }
    }

    @Command
    @Usage("Retry all Paused flows.")
    public void retryAllPaused() {
        logger.info("Executing command \"flow retryAllPaused\".");
        if (getFlowRPCShellCommand().retryAllPaused()) {
            out.println("Retrying all paused flows succeeded.", Decoration.bold, Color.yellow);
        } else {
            out.println("One or more paused flows failed to retry.", Decoration.bold, Color.red);
        }
    }

    @Command
    @Usage("Retry all Paused flows which were hospitalized before paused.")
    public void retryAllPausedHospitalized() {
        logger.info("Executing command \"flow retryAllPausedHospitalized\".");
        if (getFlowRPCShellCommand().retryAllPausedHospitalized()) {
            out.println("Retrying all paused hospitalized flows succeeded.", Decoration.bold, Color.yellow);
        } else {
            out.println("One or more paused hospitalized flows failed to retry.", Decoration.bold, Color.red);
        }
    }

    @Command
    @Usage("Recover a finality flow by flow uuid.")
    public void recover(
            @Usage("The UUID for the flow that we wish to recover") @Argument String id,
            @Usage("Flag to force recovery of flows that are in a HOSPITALIZED or PAUSED state.")
            @Option(names = { "f", "force-recover" }) Boolean forceRecover
    ) {
        logger.info("Executing command \"flow recover {}\".", id);
        StateMachineRunId parsedId = parseStateMachineRunId(id, out, objectMapper(null));

        Boolean recoveryResult;
        if (forceRecover != null)
            recoveryResult = getFlowRPCShellCommand().recoverFinalityFlow(parsedId, forceRecover);
        else
            recoveryResult = getFlowRPCShellCommand().recoverFinalityFlow(parsedId);

        if (recoveryResult) {
            out.println("Recovered finality flow " + parsedId, Decoration.bold, Color.yellow);
        } else {
            out.println("Failed to recover finality flow " + parsedId, Decoration.bold, Color.red);
        }
    }

    @Command
    @Usage("Recover a finality flow by transaction id.")
    public void recoverByTxnId(
            @Usage("The SecureHash of the transaction that we wish to recover") @Argument String id,
            @Usage("Flag to force recovery of flows that are in a HOSPITALIZED or PAUSED state.")
            @Option(names = { "f", "force-recover" }) Boolean forceRecover
    ) {
        logger.info("Executing command \"flow recoverByTxnId {}\".", id);
        SecureHash txIdHashParsed;
        try {
            txIdHashParsed = SecureHash.create(id);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The provided string is neither a valid SHA-256 hash value or a supported hash algorithm");
        }
        Boolean recoveryResult;
        if (forceRecover != null)
            recoveryResult = getFlowRPCShellCommand().recoverFinalityFlowByTxnId(txIdHashParsed, forceRecover);
        else
            recoveryResult = getFlowRPCShellCommand().recoverFinalityFlowByTxnId(txIdHashParsed);

        if (recoveryResult) {
            out.println("Recovered finality flow " + txIdHashParsed, Decoration.bold, Color.yellow);
        } else {
            out.println("Failed to recover finality flow " + txIdHashParsed, Decoration.bold, Color.red);
        }
    }

    @Command
    @Usage("Recover all finality flows that have failed on this node.")
    public void recoverAll(
        @Usage("Flag to force recovery of flows that are in a HOSPITALIZED or PAUSED state.")
        @Option(names = { "f", "force-recover" }) Boolean forceRecover
    ) {
        logger.info("Executing command \"flow recoverAll {}\".");

        Map<StateMachineRunId, Boolean> recoveredFlows;
        if (forceRecover != null)
            recoveredFlows = getFlowRPCShellCommand().recoverAllFinalityFlows(forceRecover);
        else
            recoveredFlows = getFlowRPCShellCommand().recoverAllFinalityFlows();

        if (!recoveredFlows.isEmpty()) {
            out.println("Recovered finality flow(s) ", Decoration.bold, Color.yellow);
            out.println("Results: " + Arrays.toString(recoveredFlows.entrySet().toArray()), Decoration.bold, Color.yellow );
        } else {
            out.println("Failed to recover finality flow(s) ", Decoration.bold, Color.red);
        }
    }

    @Command
    @Usage("Recover all finality flows that have failed on this node and match the search query criteria.\n" +
            "\tAvailable search criteria arguments are: \n" +
            "\t\tflowStartFromTime: String [ISO8601 DateTime] - the start of a time window where the flow was started - if not present taken to be 0 unix timestamp\n" +
            "\t\tflowStartUntilTime: String [ISO8601 DateTime] - the end of a time window where the flow was started - if not present taken to be the current utc unix timestamp\n" +
            "\t\tinitiatedBy: String [X509 name, like O=PartyA,L=London,C=GB] - the name of the party that initiated the flow\n" +
            "\t\tcounterParties: String [X509 name, like O=PartyA,L=London,C=GB] - the name of any counter party peers receiving the flow, , can be specified as an array like [\"O=PartyA,L=London,C=GB\", \"O=PartyB,L=London,C=GB\"]\n"
    )
    public void recoverMatching(
        InvocationContext<Map> context,
        @Argument(unquote = true) List<String> input,
        @Usage("Flag to force recovery of flows that are in a HOSPITALIZED or PAUSED state.")
        @Option(names = { "f", "force-recover" }) Boolean forceRecover
    ) {
        logger.info("Executing command \"flow recoverMatching {}\".");

        Map<StateMachineRunId, Boolean> recoveredFlows;
        if (forceRecover != null)
            recoveredFlows = queryRecoveryFlows(context.getWriter(), input, ops(FlowRPCOps.class), ops(CordaRPCOps.class), forceRecover);
        else
            recoveredFlows = queryRecoveryFlows(context.getWriter(), input, ops(FlowRPCOps.class), ops(CordaRPCOps.class));

        if (!recoveredFlows.isEmpty()) {
            out.println("Recovered finality flow(s) ", Decoration.bold, Color.yellow);
            out.println("Results: " + Arrays.toString(recoveredFlows.entrySet().toArray()), Decoration.bold, Color.yellow );
        } else {
            out.println("Failed to recover finality flow(s) ", Decoration.bold, Color.red);
        }
    }
}