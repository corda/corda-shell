package net.corda.tools.shell;

import net.corda.client.rpc.proxy.FlowRPCOps;
import net.corda.core.flows.StateMachineRunId;
import org.crsh.command.RuntimeContext;
import org.jetbrains.annotations.NotNull;

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
}
