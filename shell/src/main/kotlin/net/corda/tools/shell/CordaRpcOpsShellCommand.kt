package net.corda.tools.shell

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.type.TypeFactory
import net.corda.client.jackson.JacksonSupport
import net.corda.core.messaging.CordaRPCOps

internal abstract class CordaRpcOpsShellCommand : InteractiveShellCommand<CordaRPCOps>() {
    override val rpcOpsClass: Class<out CordaRPCOps> = CordaRPCOps::class.java

    fun objectMapper(classLoader: ClassLoader?): ObjectMapper {
        val om = createYamlInputMapper()
        if (classLoader != null) {
            om.typeFactory = TypeFactory.defaultInstance().withClassLoader(classLoader)
        }
        return om
    }

    private fun createYamlInputMapper(): ObjectMapper {
        val rpcOps = ops(JacksonSupport.PartyInfoRpcOps::class.java)
        return InteractiveShell.createYamlInputMapper(rpcOps)
    }
}