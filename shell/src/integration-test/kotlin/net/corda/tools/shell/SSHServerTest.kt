package net.corda.tools.shell

import co.paralleluniverse.fibers.Suspendable
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.messaging.ClientRpcSslOptions
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.node.services.Permissions.Companion.invokeRpc
import net.corda.node.services.Permissions.Companion.startFlow
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import net.corda.testing.node.internal.enclosedCordapp
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.util.io.Streams
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Starts the [InteractiveShell] manually because the driver cannot start the SSH server itself anymore.
 */
class SSHServerTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test(timeout = 300_000)
    fun `ssh server starts when configured`() {
        val user = User("u", "p", setOf())
        driver(DriverParameters(notarySpecs = emptyList(), cordappsForAllNodes = emptyList())) {
            val node = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()

            startShell(node, ssl = null, sshdPort = 2222)

            val session = JSch().getSession("u", "localhost", 2222)
            session.setConfig("StrictHostKeyChecking", "no")
            session.setPassword("p")

            session.connect()

            assertTrue(session.isConnected)

            InteractiveShell.stop()
        }
    }

    @Test(timeout = 300_000)
    fun `ssh server verify credentials`() {
        val user = User("u", "p", setOf())
        driver(DriverParameters(notarySpecs = emptyList(), cordappsForAllNodes = emptyList())) {
            val node = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()

            startShell(node, ssl = null, sshdPort = 2222)

            val session = JSch().getSession("u", "localhost", 2222)
            session.setConfig("StrictHostKeyChecking", "no")
            session.setPassword("p_is_bad_password")

            try {
                session.connect()
                fail("Server should reject invalid credentials")
            } catch (e: JSchException) {
                //There is no specialized exception for this
                assertTrue(e.message == "Auth fail")
            }
            InteractiveShell.stop()
        }
    }

    @Test(timeout = 300_000)
    fun `ssh respects permissions`() {
        val user = User(
            "u", "p", setOf(
                startFlow<FlowICanRun>(),
                invokeRpc(CordaRPCOps::wellKnownPartyFromX500Name)
            )
        )
        driver(DriverParameters(notarySpecs = emptyList(), cordappsForAllNodes = listOf(enclosedCordapp()))) {
            val node = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()

            startShell(node, ssl = null, sshdPort = 2222)

            val session = JSch().getSession("u", "localhost", 2222)
            session.setConfig("StrictHostKeyChecking", "no")
            session.setPassword("p")
            session.connect()

            assertTrue(session.isConnected)

            val channel = session.openChannel("exec") as ChannelExec
            channel.setCommand("start FlowICannotRun otherParty: \"$ALICE_NAME\"")
            channel.connect()
            val response = String(Streams.readAll(channel.inputStream))

            channel.disconnect()
            session.disconnect()

            assertThat(response).matches("(?s)User not authorized to perform RPC call .*")

            InteractiveShell.stop()
        }
    }

    @Ignore
    @Test(timeout = 300_000)
    fun `ssh runs flows`() {
        val user = User("u", "p", setOf(startFlow<FlowICanRun>()))
        driver(DriverParameters(notarySpecs = emptyList(), cordappsForAllNodes = listOf(enclosedCordapp()))) {
            val node = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()

            startShell(node, ssl = null, sshdPort = 2222)

            val session = JSch().getSession("u", "localhost", 2222)
            session.setConfig("StrictHostKeyChecking", "no")
            session.setPassword("p")
            session.connect()

            assertTrue(session.isConnected)

            val channel = session.openChannel("exec") as ChannelExec
            channel.setCommand("start FlowICanRun")
            channel.connect(5000)

            assertTrue(channel.isConnected)

            val response = String(Streams.readAll(channel.inputStream))

            val linesWithDoneCount = response.lines().filter { line -> line.contains("Done") }

            channel.disconnect()
            session.disconnect()

            // There are ANSI control characters involved, so we want to avoid direct byte to byte matching.
            assertThat(linesWithDoneCount).size().isGreaterThanOrEqualTo(1)
        }
    }

    private fun startShell(node: NodeHandle, ssl: ClientRpcSslOptions? = null, sshdPort: Int? = null) {
        val user = node.rpcUsers[0]
        startShell(user.username, user.password, node.rpcAddress, ssl, sshdPort)
    }

    private fun startShell(
        user: String,
        password: String,
        address: NetworkHostAndPort,
        ssl: ClientRpcSslOptions? = null,
        sshdPort: Int? = null
    ) {
        val conf = ShellConfiguration(
            commandsDirectory = tempFolder.newFolder().toPath(),
            user = user,
            password = password,
            hostAndPort = address,
            ssl = ssl,
            sshdPort = sshdPort
        )
        InteractiveShell.startShell(conf)
    }

    @StartableByRPC
    @InitiatingFlow
    class FlowICanRun : FlowLogic<String>() {

        private val HELLO_STEP = ProgressTracker.Step("Hello")

        @Suspendable
        override fun call(): String {
            progressTracker?.currentStep = HELLO_STEP
            return "bambam"
        }

        override val progressTracker: ProgressTracker? = ProgressTracker(HELLO_STEP)
    }

    @Suppress("unused")
    @StartableByRPC
    @InitiatingFlow
    class FlowICannotRun(private val otherParty: Party) : FlowLogic<String>() {
        @Suspendable
        override fun call(): String = initiateFlow(otherParty).receive<String>().unwrap { it }

        override val progressTracker: ProgressTracker? = ProgressTracker()
    }
}