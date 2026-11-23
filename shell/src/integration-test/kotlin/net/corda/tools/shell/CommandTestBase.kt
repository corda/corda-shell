package net.corda.tools.shell

import com.google.common.io.Files
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.PortAllocation
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.node.User
import org.bouncycastle.util.io.Streams
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.hamcrest.core.StringContains
import org.junit.ClassRule
import kotlin.test.assertTrue

abstract class CommandTestBase : IntegrationTest() {

    companion object {
        val sshPortIncrementer = PortAllocation.defaultAllocator

        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(ALICE_NAME)
    }

    protected fun testCommand(session: Session, command: String, expected: String, mustExclude: String? = null) {
        val channel = session.openChannel("exec") as ChannelExec
        channel.setCommand(command)
        // Get the output stream before connecting: JSch drops output that arrives before it is requested, and a quick
        // command can finish and close the channel before connect() returns, so channel.isConnected can already be false.
        val output = channel.inputStream
        channel.connect(5000)
        val response = String(Streams.readAll(output))
        channel.disconnect()
        MatcherAssert.assertThat(response, StringContains.containsString(expected))
        mustExclude?.let {
            MatcherAssert.assertThat(response, CoreMatchers.not(StringContains.containsString(mustExclude)))
        }
    }

    protected fun connectToShell(user: User, node: NodeHandle): Session {
        val sshdPort = sshPortIncrementer.nextPort()
        val conf = ShellConfiguration(
            commandsDirectory = Files.createTempDir().toPath(),
            user = user.username, password = user.password,
            hostAndPort = node.rpcAddress,
            sshdPort = sshdPort
        )

        InteractiveShell.startShell(conf)
        InteractiveShell.nodeInfo()

        val session = JSch().getSession(user.username, "localhost", sshdPort)
        session.setConfig("StrictHostKeyChecking", "no")
        session.setPassword(user.password)
        session.connect()

        assertTrue(session.isConnected)
        return session
    }
}