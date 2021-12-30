package net.corda.tools.shell;

import net.corda.client.rpc.proxy.FlowRPCOps;
import org.crsh.command.InvocationContext;
import org.crsh.shell.impl.command.CRaSHSession;
import org.crsh.text.Color;
import org.crsh.text.Decoration;
import org.crsh.text.RenderPrintWriter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;

import static org.mockito.Mockito.*;

public class FlowCommandMockTest {

    private SshAuthInfo authInfo = mock(SshAuthInfo.class);
    private FlowRPCOps ops = mock(FlowRPCOps.class);
    private InvocationContext mockInvocationContext = mock(InvocationContext.class);
    private CRaSHSession session = mock(CRaSHSession.class);
    private RenderPrintWriter printWriter = mock(RenderPrintWriter.class);
    private FlowShellCommand flowsShellCommand = new FlowShellCommand();

    @Before
    public void setup() {
        when(authInfo.getOrCreateRpcOps(FlowRPCOps.class)).thenReturn(ops);
        when(mockInvocationContext.getSession()).thenReturn(session);
        when(session.getAuthInfo()).thenReturn(authInfo);
        when(mockInvocationContext.getWriter()).thenReturn(printWriter);
        flowsShellCommand.pushContext(mockInvocationContext);
    }

    private void assertPrintWriter(String expectedMessage) {
        doAnswer(invocation -> {
            System.out.println(invocation.getArgument(0).toString());
            assertEquals(expectedMessage, invocation.getArgument(0).toString());
            return null;
        }).when(printWriter).println(
            ArgumentMatchers.any(String.class),
            ArgumentMatchers.any(Decoration.class),
            ArgumentMatchers.any(Color.class)
        );
    }

    @Test(timeout = 300_000)
    public void pauseAllFlowsSucceeds() {
        when(ops.pauseAllFlows()).thenReturn(true);
        assertPrintWriter("Pausing all flows succeeded.");
        flowsShellCommand.pauseAll();
    }

    @Test(timeout = 300_000)
    public void pauseAllFlowsFails() {
        when(ops.pauseAllFlows()).thenReturn(false);
        assertPrintWriter("Failed to pause one or more flows.");
        flowsShellCommand.pauseAll();
    }

    @Test(timeout = 300_000)
    public void pauseAllHospitalizedSucceeds() {
        when(ops.pauseAllHospitalizedFlows()).thenReturn(true);
        assertPrintWriter("Pausing all Hospitalized flows succeeded.");
        flowsShellCommand.pauseAllHospitalized();
    }

    @Test(timeout = 300_000)
    public void pauseAllHospitalizedFails() {
        when(ops.pauseAllHospitalizedFlows()).thenReturn(false);
        assertPrintWriter("Failed to pause one or more Hospitalized flows.");
        flowsShellCommand.pauseAllHospitalized();
    }

    @Test(timeout = 300_000)
    public void retryAllPausedFlowsSucceeds() {
        when(ops.retryAllPausedFlows()).thenReturn(true);
        assertPrintWriter("Retrying all paused flows succeeded.");
        flowsShellCommand.retryAllPaused();
    }

    @Test(timeout = 300_000)
    public void retryAllPausedFlowsFails() {
        when(ops.retryAllPausedFlows()).thenReturn(false);
        assertPrintWriter("One or more paused flows failed to retry.");
        flowsShellCommand.retryAllPaused();
    }

    @Test(timeout = 300_000)
    public void retryAllPausedHospitalizedFlowsSucceeds() {
        when(ops.retryAllPausedHospitalizedFlows()).thenReturn(true);
        assertPrintWriter("Retrying all paused hospitalized flows succeeded.");
        flowsShellCommand.retryAllPausedHospitalized();
    }

    @Test(timeout = 300_000)
    public void retryAllPausedHospitalizedFlowsFails() {
        when(ops.retryAllPausedHospitalizedFlows()).thenReturn(false);
        assertPrintWriter("One or more paused hospitalized flows failed to retry.");
        flowsShellCommand.retryAllPausedHospitalized();
    }
}
