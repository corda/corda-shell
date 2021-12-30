package net.corda.tools.shell

import net.corda.core.messaging.RPCOps
import org.crsh.command.BaseCommand
import org.crsh.shell.impl.command.CRaSHSession

/**
 * Extends CRaSH BaseCommand to add access to multiple RPC ops classes.
 *
 * Some commands may need to access to multiple [RPCOps] interfaces to function.
 */
internal abstract class MultiRpcInteractiveShellCommand : BaseCommand() {

    fun <T : RPCOps> ops(clazz: Class<T>): T {
        val cRaSHSession = context.session as CRaSHSession
        val authInfo = cRaSHSession.authInfo as SshAuthInfo
        return authInfo.getOrCreateRpcOps(clazz)
    }

    fun ansiProgressRenderer() = ((context.session as CRaSHSession).authInfo as CordaSSHAuthInfo).ansiProgressRenderer

    fun isSsh() = ((context.session as CRaSHSession).authInfo as CordaSSHAuthInfo).isSsh
}