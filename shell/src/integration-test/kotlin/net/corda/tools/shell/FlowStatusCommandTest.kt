package net.corda.tools.shell

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.millis
import net.corda.core.utilities.seconds
import net.corda.node.services.Permissions
import net.corda.testing.common.internal.eventually
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import net.corda.testing.node.internal.cordappWithPackages
import org.junit.Test
import java.util.concurrent.Semaphore

@SuppressWarnings("TooGenericExceptionCaught")
class FlowStatusCommandTest : CommandTestBase() {

    val customCordapp = cordappWithPackages("net.corda.failingflows.workflows")

    @Test(timeout = 300_000)
    fun `flow status can be queried via the shell`() {
        val user = User("username", "password", setOf(Permissions.all()))
        driver(DriverParameters(notarySpecs = emptyList(), cordappsForAllNodes = listOf(customCordapp))) {
            val node = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user), startInSameProcess = true).getOrThrow()
            PauseFlow.beforePause = Semaphore(0)
            val handle = node.rpc.startFlow(::PauseFlow)
            val id = handle.id
            val session = connectToShell(user, node)
            testCommand(session, command = "flow pause ${id.uuid.toString().lowercase()}", expected = "Paused flow $id")
            PauseFlow.beforePause!!.release()
            eventually(5.seconds) {
                testCommand(
                    session = session,
                    command = "flowStatus queryFlows flowStates: [PAUSED, FAILED]",
                    expected = id.uuid.toString()
                )
            }
            testCommand(session, command = "flow retry ${id.uuid.toString().lowercase()}", expected = "Retried flow $id")
            handle.returnValue.getOrThrow()
        }
    }

    @StartableByRPC
    class PauseFlow : FlowLogic<Unit>() {
        companion object {
            var beforePause: Semaphore? = null;
        }

        @Suspendable
        override fun call() {
            beforePause!!.acquire()
            sleep(10.millis)
        }
    }
}