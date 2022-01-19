package net.corda.tools.shell

import net.corda.core.internal.uncheckedCast
import net.corda.core.messaging.ClientRpcSslOptions
import net.corda.core.utilities.NetworkHostAndPort
import java.nio.file.Path

data class ShellConfiguration(
    val commandsDirectory: Path,
    val cordappsDirectory: Path? = null,
    var user: String = "",
    var password: String = "",
    var permissions: Set<String>? = null,
    var localShellAllowExitInSafeMode: Boolean = false,
    var localShellUnsafe: Boolean = false,
    var manAllowed: Boolean = false,
    val hostAndPort: NetworkHostAndPort,
    val ssl: ClientRpcSslOptions? = null,
    val sshdPort: Int? = null,
    val sshHostKeyDirectory: Path? = null,
    val noLocalShell: Boolean = false
) {
    companion object {
        const val COMMANDS_DIR = "shell-commands"
        const val CORDAPPS_DIR = "cordapps"
        const val SSHD_HOSTKEY_DIR = "ssh"

        private inline fun <reified T> Map<String, Any?>.getAndCast(key: String): T? {
            return uncheckedCast<Any?, T?>(this[key])
        }
    }

    constructor(map: Map<String, Any?>) : this(
        commandsDirectory = map.getAndCast<Path>("commandsDirectory")!!,
        cordappsDirectory = map.getAndCast<Path>("cordappsDirectory"),
        user = map.getAndCast<String>("user") ?: "",
        password = map.getAndCast<String>("password") ?: "",
        permissions = map.getAndCast<Set<String>>("permissions"),
        localShellAllowExitInSafeMode = map.getAndCast<Boolean>("localShellAllowExitInSafeMode") ?: false,
        localShellUnsafe = map.getAndCast<Boolean>("localShellUnsafe") ?: false,
        manAllowed = map.getAndCast<Boolean>("manAllowed") ?: false,
        hostAndPort = map.getAndCast<NetworkHostAndPort>("hostAndPort")!!,
        ssl = map.getAndCast<ClientRpcSslOptions>("ssl"),
        sshdPort = map.getAndCast<Int>("sshdPort"),
        sshHostKeyDirectory = map.getAndCast<Path>("sshHostKeyDirectory"),
        noLocalShell = map.getAndCast<Boolean>("noLocalShell") ?: false
    )
}
