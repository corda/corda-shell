package net.corda.tools.shell;

import net.corda.client.rpc.proxy.FlowRPCOps;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.StateMachineRunId;
import org.crsh.command.RuntimeContext;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class FlowRPCShellCommand extends InteractiveShellCommand<FlowRPCOps> {

    FlowRPCShellCommand(RuntimeContext context) {
        this.context = context;
    }

    @NotNull
    @Override
    public Class<FlowRPCOps> getRpcOpsClass() {
        return FlowRPCOps.class;
    }

    Boolean pause(StateMachineRunId id) {
        return ops().pauseFlow(id);
    }

    Boolean pauseAll() {
        return ops().pauseAllFlows();
    }

    Boolean pauseAllHospitalized() {
        return ops().pauseAllHospitalizedFlows();
    }

    Boolean retry(StateMachineRunId id) {
        return ops().retryFlow(id);
    }

    Boolean retryAllPaused() {
        return ops().retryAllPausedFlows();
    }

    Boolean retryAllPausedHospitalized() {
        return ops().retryAllPausedHospitalizedFlows();
    }

    Boolean recoverFinalityFlow(StateMachineRunId id, Boolean forceRecover) { return ops().recoverFinalityFlow(id, forceRecover); }
    Boolean recoverFinalityFlow(StateMachineRunId id) { return ops().recoverFinalityFlow(id, false); }

    Boolean recoverFinalityFlowByTxnId(SecureHash txnId, Boolean forceRecover) { return ops().recoverFinalityFlowByTxnId(txnId, forceRecover); }
    Boolean recoverFinalityFlowByTxnId(SecureHash txnId) { return ops().recoverFinalityFlowByTxnId(txnId, false); }

    Map<StateMachineRunId, Boolean> recoverAllFinalityFlows(Boolean forceRecover) { return ops().recoverAllFinalityFlows(forceRecover); }
    Map<StateMachineRunId, Boolean> recoverAllFinalityFlows() { return ops().recoverAllFinalityFlows(false); }
}
