package net.corda.tools.shell

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.millis
import net.corda.failingflows.workflows.HospitalizerFlow
import net.corda.node.services.Permissions
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import net.corda.testing.node.internal.cordappWithPackages
import org.junit.Test
import java.time.Duration
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@SuppressWarnings("TooGenericExceptionCaught")
class FlowRPCCommandTest : CommandTestBase() {

    val customCordapp = cordappWithPackages("net.corda.failingflows.workflows")

    @Test(timeout = 300_000)
    fun `Hospitalised flows can be retried via the shell`() {
        val user = User("username", "password", setOf(Permissions.all()))
        driver(DriverParameters(notarySpecs = emptyList(), cordappsForAllNodes = listOf(customCordapp))) {
            val nodeFuture = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user), startInSameProcess = true)
            val node = nodeFuture.getOrThrow()
            val handle = node.rpc.startTrackedFlow(::HospitalizerFlow)
            var timesThrown = 0
            handle.progress.subscribe {
                if (it == HospitalizerFlow.THROWING.label) {
                    timesThrown++
                }
            }
            val id = handle.id
            assertFailsWith<TimeoutException> { handle.returnValue.getOrThrow(timeout = Duration.ofSeconds(30)) }
            assertEquals(1, timesThrown)
            val session = connectToShell(user, node)
            testCommand(session, command = "flowStatus queryById ${id.uuid.toString().toLowerCase()}", expected = "HOSPITALIZED")
            testCommand(session, command = "flow retry ${id.uuid.toString().toLowerCase()}", expected = "Retried flow $id")
            assertFailsWith<TimeoutException> { handle.returnValue.getOrThrow(timeout = Duration.ofSeconds(10)) }
            assertEquals(2, timesThrown)
            session.disconnect()
        }
    }

    @Test(timeout = 300_000)
    fun `A flow can be paused and resumed via the shell`() {
        val user = User("username", "password", setOf(Permissions.all()))
        driver(DriverParameters(notarySpecs = emptyList(), cordappsForAllNodes = listOf(customCordapp))) {
            val node = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user), startInSameProcess = true).getOrThrow()
            PauseFlow.beforePause = Semaphore(0)
            val handle = node.rpc.startFlow(::PauseFlow)
            val id = handle.id
            val session = connectToShell(user, node)
            testCommand(session, command = "flow pause ${id.uuid.toString().toLowerCase()}", expected = "Paused flow $id")
            PauseFlow.beforePause!!.release()
            Thread.sleep(5000) //Wait for the Statemachine to go through a pause transition.
            testCommand(session, command = "flowStatus queryById ${id.uuid.toString().toLowerCase()}", expected = "PAUSED")
            testCommand(session, command = "flow retry ${id.uuid.toString().toLowerCase()}", expected = "Retried flow $id")
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