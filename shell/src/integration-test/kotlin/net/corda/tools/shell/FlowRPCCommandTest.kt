package net.corda.tools.shell

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.CordaRuntimeException
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.millis
import net.corda.failingflows.workflows.HospitalizerFlow
import net.corda.node.services.Permissions
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.TestCordapp
import net.corda.testing.node.User
import net.corda.testing.node.internal.cordappWithPackages
import org.junit.Test
import java.time.Duration
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@SuppressWarnings("TooGenericExceptionCaught")
class FlowRPCCommandTest : CommandTestBase() {

    val customCordapp = cordappWithPackages("net.corda.failingflows.workflows",
        "net.corda.tools.shell")

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
            testCommand(
                session,
                command = "flowStatus queryById ${id.uuid.toString().toLowerCase()}",
                expected = "HOSPITALIZED"
            )
            testCommand(
                session,
                command = "flow retry ${id.uuid.toString().toLowerCase()}",
                expected = "Retried flow $id"
            )
            assertFailsWith<TimeoutException> { handle.returnValue.getOrThrow(timeout = Duration.ofSeconds(10)) }
            assertEquals(2, timesThrown)
            session.disconnect()
        }
    }

    @Test(timeout = 300_000)
    fun `A flow can be paused and resumed via the shell`() {
        val user = User("username", "password", setOf(Permissions.all()))
        driver(DriverParameters(notarySpecs = emptyList(), cordappsForAllNodes = listOf(customCordapp))) {
            val node =
                startNode(providedName = ALICE_NAME, rpcUsers = listOf(user), startInSameProcess = true).getOrThrow()
            PauseFlow.beforePause = Semaphore(0)
            val handle = node.rpc.startFlow(::PauseFlow)
            val id = handle.id
            val session = connectToShell(user, node)
            testCommand(
                session,
                command = "flow pause ${id.uuid.toString().toLowerCase()}",
                expected = "Paused flow $id"
            )
            PauseFlow.beforePause!!.release()
            Thread.sleep(5000) //Wait for the Statemachine to go through a pause transition.
            testCommand(
                session,
                command = "flowStatus queryById ${id.uuid.toString().toLowerCase()}",
                expected = "PAUSED"
            )
            testCommand(
                session,
                command = "flow retry ${id.uuid.toString().toLowerCase()}",
                expected = "Retried flow $id"
            )
            handle.returnValue.getOrThrow()
        }
    }

    @Test(timeout = 300_000)
    fun `A finality flow can be recovered via the shell`() {
        val user = User("username", "password", setOf(Permissions.all()))
        driver(DriverParameters(cordappsForAllNodes = listOf(customCordapp))) {
            val alice =
                startNode(providedName = ALICE_NAME, rpcUsers = listOf(user),
                    defaultParameters = NodeParameters(additionalCordapps =
                        setOf(TestCordapp.findCordapp("net.corda.testing.contracts"))),
                    startInSameProcess = true).getOrThrow()
            val bob =
                startNode(providedName = BOB_NAME, rpcUsers = listOf(user), startInSameProcess = true).getOrThrow()
            PauseFlow.beforePause = Semaphore(0)
            val handle = alice.rpc.startFlow(::CreateTransactionFlow, bob.nodeInfo.legalIdentities.first())
            val id = handle.id
            try { handle.returnValue.getOrThrow() }
            catch (e: FinalityFlowException) {
                println(e.message)
                val session = connectToShell(user, alice)
//            testCommand(
//                session,
//                command = "flowStatus queryById ${id.uuid.toString().toLowerCase()}",
//                expected = "PAUSED"
//            )
//            testCommand(
//                session,
//                command = "flow recover ${id.uuid.toString().toLowerCase()}",
//                expected = "Recovered finality flow $id"
//            )
                testCommand(
                    session,
                    command = "flow recoverByTxnId ${e.txnId}",
                    expected = "Recovered finality flow $id"
                )
            }
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

    @InitiatingFlow
    @StartableByRPC
    class CreateTransactionFlow(private val peer: Party) : FlowLogic<StateAndRef<DummyState>>() {
        @Suspendable
        override fun call(): StateAndRef<DummyState> {
            val tx = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first()).apply {
                addOutputState(DummyState(participants = listOf(ourIdentity)))
                addCommand(DummyContract.Commands.Create(), listOf(ourIdentity.owningKey, peer.owningKey))
            }
            val stx = serviceHub.signInitialTransaction(tx)
            val session = initiateFlow(peer)
            try {
                val ftx = subFlow(CollectSignaturesFlow(stx, listOf(session)))
                subFlow(FinalityFlow(ftx, session))
                return ftx.coreTransaction.outRef(0)
            }
            catch (e: UnexpectedFlowEndException) {
                throw FinalityFlowException(stx.id, runId.uuid)
            }
        }
    }

    @InitiatedBy(CreateTransactionFlow::class)
    class CreateTransactionResponderFlow(private val session: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val stx = subFlow(object : SignTransactionFlow(session) {
                override fun checkTransaction(stx: SignedTransaction) {

                }
            })
//            throw FinalityFlowException(stx.id, runId.uuid)
            subFlow(ReceiveFinalityFlow(session, stx.id))
        }
    }

    class FinalityFlowException(val txnId: SecureHash, val flowId: UUID)
        : FlowException("Finality flow $flowId failed for transaction id: $txnId")
}
