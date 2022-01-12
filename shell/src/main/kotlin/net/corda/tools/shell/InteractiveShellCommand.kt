package net.corda.tools.shell

import net.corda.core.messaging.RPCOps

/**
 * Simply extends CRaSH BaseCommand to add easy access to the RPC ops class.
 */
internal abstract class InteractiveShellCommand<T : RPCOps> : MultiRpcInteractiveShellCommand() {

    abstract val rpcOpsClass: Class<out T>

    fun ops(): T {
        return ops(rpcOpsClass)
    }
}