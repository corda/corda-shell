package net.corda.tools.shell

import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.client.rpc.GracefulReconnect
import net.corda.client.rpc.RPCConnection
import net.corda.client.rpc.ext.RPCConnectionListener
import net.corda.client.rpc.internal.RPCClient
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.RPCOps
import net.corda.core.utilities.contextLogger

internal interface RPCOpsProducer {
    /**
     * Returns [RPCConnection] of underlying proxy. Proxy can be obtained at any time by calling [RPCConnection.proxy]
     */
    operator fun <T : RPCOps> invoke(username: String?, credential: String?, rpcOpsClass: Class<T>,
                                     disconnectCallback: (Class<T>) -> Unit = {} ): RPCConnection<T>
}

internal class DefaultRPCOpsProducer(
    private val configuration: ShellConfiguration,
    private val classLoader: ClassLoader? = null,
    private val standalone: Boolean
) : RPCOpsProducer {

    private companion object {
        val log = contextLogger()
    }

    override fun <T : RPCOps> invoke(username: String?, credential: String?, rpcOpsClass: Class<T>,
                                     disconnectCallback: (Class<T>) -> Unit): RPCConnection<T> {

        return if (rpcOpsClass == CordaRPCOps::class.java) {
            // For CordaRPCOps we are using CordaRPCClient
            val connection = if (standalone) {
                CordaRPCClient(
                    configuration.hostAndPort,
                    configuration.ssl,
                    classLoader
                ).start(username!!, credential!!, gracefulReconnect = GracefulReconnect())
            } else {
                CordaRPCClient(
                    hostAndPort = configuration.hostAndPort,
                    configuration = CordaRPCClientConfiguration.DEFAULT.copy(
                        maxReconnectAttempts = 1
                    ),
                    sslConfiguration = configuration.ssl,
                    classLoader = classLoader
                ).start(username!!, credential!!)
            }
            @Suppress("UNCHECKED_CAST")
            connection as RPCConnection<T>
        } else {
            // For other types "plain" RPCClient is used
            val rpcClient = RPCClient<T>(configuration.hostAndPort, configuration.ssl)
            rpcClient.addConnectionListener(object : RPCConnectionListener<T> {
                override fun onConnect(context: RPCConnectionListener.ConnectionContext<T>) {
                    log.info("RPC connection established for ${rpcOpsClass.simpleName}")
                }

                override fun onDisconnect(context: RPCConnectionListener.ConnectionContext<T>) {
                    log.info("RPC connection lost for ${rpcOpsClass.simpleName}")
                    disconnectCallback(rpcOpsClass)
                }

                override fun onPermanentFailure(context: RPCConnectionListener.ConnectionContext<T>) {
                    log.info("RPC connection permanent failure for ${rpcOpsClass.simpleName}")
                }
            })
            val connection = rpcClient.start(rpcOpsClass, username!!, credential!!)
            connection
        }
    }
}