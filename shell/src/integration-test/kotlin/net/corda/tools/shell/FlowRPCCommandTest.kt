package net.corda.tools.shell

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.PartyAndReference
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.flows.DistributionList.SenderDistributionList
import net.corda.core.identity.Party
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.millis
import net.corda.failingflows.workflows.HospitalizerFlow
import net.corda.node.services.Permissions
import net.corda.node.services.api.ServiceHubInternal
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import net.corda.testing.node.internal.cordappWithPackages
import net.corda.testing.node.internal.findCordapp
import org.junit.Test
import java.time.Duration
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

@SuppressWarnings("TooGenericExceptionCaught")
class FlowRPCCommandTest : CommandTestBase() {

    val customCordapp = cordappWithPackages("net.corda.failingflows.workflows", "net.corda.tools.shell")

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
        driver(
            DriverParameters(
                cordappsForAllNodes = listOf(customCordapp, findCordapp("net.corda.testing.contracts"))
            )
        ) {
            val alice =
                startNode(providedName = ALICE_NAME, rpcUsers = listOf(user), startInSameProcess = true).getOrThrow()
            val bob =
                startNode(providedName = BOB_NAME, rpcUsers = listOf(user), startInSameProcess = true).getOrThrow()
            val stateAndRef = alice.rpc.startFlow(::IssueFlow, defaultNotaryIdentity).returnValue.getOrThrow()
            val handle = alice.rpc.startFlow(::TransferFlow, stateAndRef, bob.nodeInfo.legalIdentities.first())
            try {
                handle.returnValue.getOrThrow()
                fail()
            } catch (e: FinalityFlowException) {
                println("FinalityFlowException: $${e.message}")
                val session = connectToShell(user, alice)
                testCommand(
                    session,
                    command = "flowStatus queryFinalityById ${e.flowId}",
                    expected = "status: \"IN_FLIGHT\""
                )
                testCommand(
                    session,
                    command = "flowStatus queryFinalityByTxnId ${e.txnId}",
                    expected = "status: \"IN_FLIGHT\""
                )
                testCommand(
                    session,
                    command = "flow recoverFinalityByTxnId ${e.txnId}",
                    expected = "Recovered finality flow ${e.txnId}"
                )
            }
        }
    }

    @StartableByRPC
    class PauseFlow : FlowLogic<Unit>() {
        companion object {
            var beforePause: Semaphore? = null
        }

        @Suspendable
        override fun call() {
            beforePause!!.acquire()
            sleep(10.millis)
        }
    }

    @StartableByRPC
    class IssueFlow(val notary: Party) : FlowLogic<StateAndRef<DummyContract.SingleOwnerState>>() {

        @Suspendable
        override fun call(): StateAndRef<DummyContract.SingleOwnerState> {
            val partyAndReference = PartyAndReference(ourIdentity, OpaqueBytes.of(1))
            val txBuilder = DummyContract.generateInitial(Random().nextInt(), notary, partyAndReference)
            val signedTransaction = serviceHub.signInitialTransaction(txBuilder, ourIdentity.owningKey)
            val notarised = subFlow(FinalityFlow(signedTransaction, emptySet<FlowSession>()))
            return notarised.coreTransaction.outRef(0)
        }
    }

    @InitiatingFlow
    @StartableByRPC
    class TransferFlow(
        private val stateAndRef: StateAndRef<DummyContract.SingleOwnerState>,
        private val newOwner: Party
    ) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val txBuilder = DummyContract.move(stateAndRef, newOwner)
            val signedTransaction = serviceHub.signInitialTransaction(txBuilder, ourIdentity.owningKey)
            (serviceHub as ServiceHubInternal).recordUnnotarisedTransaction(signedTransaction)
            (serviceHub as ServiceHubInternal).recordSenderTransactionRecoveryMetadata(
                signedTransaction.id,
                TransactionMetadata(
                    initiator = ourIdentity.name,
                    distributionList = SenderDistributionList(
                        senderStatesToRecord = StatesToRecord.ONLY_RELEVANT,
                        peersToStatesToRecord = mapOf(newOwner.name to StatesToRecord.ONLY_RELEVANT)
                    )
                )
            )
            sleep(Duration.ZERO)    // force checkpoint / txn commit
            throw FinalityFlowException(signedTransaction.id, runId.uuid)
        }
    }

    class FinalityFlowException(val txnId: SecureHash, val flowId: UUID) :
        FlowException("Finality flow $flowId failed for transaction id: $txnId")
}
