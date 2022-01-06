package net.corda.tools.shell.standalone

import com.jcabi.manifests.Manifests
import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.ExitCodes
import net.corda.cliutils.start
import net.corda.core.internal.exists
import net.corda.core.internal.isRegularFile
import net.corda.core.internal.list
import net.corda.core.utilities.contextLogger
import net.corda.tools.shell.InteractiveShell
import net.corda.tools.shell.ShellConfiguration
import org.fusesource.jansi.Ansi
import org.fusesource.jansi.AnsiConsole
import org.slf4j.bridge.SLF4JBridgeHandler
import picocli.CommandLine.Mixin
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import kotlin.streams.toList

fun main(args: Array<String>) {
    StandaloneShell().start(args)
}

class StandaloneShell : CordaCliWrapper("corda-shell", "The Corda standalone shell.") {
    companion object {
        private val logger by lazy { contextLogger() }

        val logo = """
R   ______               __       B  _____ _   _ _____ _____ ____  ____  ____  ___ ____  _____
R  / ____/     _________/ /___ _  B | ____| \ | |_   _| ____|  _ \|  _ \|  _ \|_ _/ ___|| ____|
R / /     __  / ___/ __  / __ `/  B |  _| |  \| | | | |  _| | |_) | |_) | |_) || |\___ \|  _|
R/ /___  /_/ / /  / /_/ / /_/ /   B | |___| |\  | | | | |___|  _ <|  __/|  _ < | | ___) | |___
R\____/     /_/   \__,_/\__,_/    B |_____|_| \_| |_| |_____|_| \_\_|   |_| \_\___|____/|_____|
D""".trimStart()
    }

    @Mixin
    var cmdLineOptions = ShellCmdLineOptions()

    lateinit var configuration: ShellConfiguration

    private fun getCordappsInDirectory(cordappsDir: Path?): List<URL> =
        if (cordappsDir == null || !cordappsDir.exists()) {
            emptyList()
        } else {
            cordappsDir.list {
                it.filter { it.isRegularFile() && it.toString().endsWith(".jar") }.map { it.toUri().toURL() }.toList()
            }
        }

    //Workaround in case console is not available
    @Throws(IOException::class)
    private fun readLine(format: String, vararg args: Any): String {
        if (System.console() != null) {
            return System.console().readLine(format, *args)
        }
        print(String.format(format, *args))
        val reader = BufferedReader(InputStreamReader(System.`in`))
        return reader.readLine()
    }

    @Throws(IOException::class)
    private fun readPassword(format: String, vararg args: Any) =
        if (System.console() != null) System.console().readPassword(format, *args) else this.readLine(format, *args).toCharArray()

    private fun getManifestEntry(key: String) = if (Manifests.exists(key)) Manifests.read(key) else "Unknown"

    override fun initLogging(): Boolean {
        super.initLogging()
        SLF4JBridgeHandler.removeHandlersForRootLogger() // The default j.u.l config adds a ConsoleHandler.
        SLF4JBridgeHandler.install()
        return true
    }

    override fun runProgram(): Int {
        configuration = try {
            cmdLineOptions.toConfig()
        } catch (e: Exception) {
            println("Configuration exception: ${e.message}")
            return ExitCodes.FAILURE
        }

        val cordappJarPaths = getCordappsInDirectory(configuration.cordappsDirectory)
        val classLoader: ClassLoader = URLClassLoader(cordappJarPaths.toTypedArray(), javaClass.classLoader)
        with(configuration) {
            if (user.isEmpty()) {
                user = readLine("User:")
            }
            if (password.isEmpty()) {
                password = String(readPassword("Password:"))
            }
        }
        InteractiveShell.startShell(configuration, classLoader, true)
        try {
            //connecting to node by requesting node info to fail fast
            val nodeInfo = InteractiveShell.nodeInfo()
            logger.info("Connected to ${nodeInfo.legalIdentities.first().name} at ${configuration.hostAndPort}")
        } catch (e: Exception) {
            logger.error("Cannot login to ${configuration.hostAndPort}, reason: \"${e.message}\"")
            println("Cannot login to ${configuration.hostAndPort}, reason: \"${e.message}\"")
            return ExitCodes.FAILURE
        }

        val exit = CountDownLatch(1)
        AnsiConsole.systemInstall()

        drawLogo()

        InteractiveShell.runLocalShell {
            exit.countDown()
        }

        exit.await()
        // because we can't clean certain Crash Shell threads that block on read()
        return ExitCodes.SUCCESS
    }

    private fun drawLogo() {
        val colourLogo = addColours(logo)
        val banner = colourLogo +
                System.lineSeparator() +
                Ansi.ansi().fgBrightDefault().bold().a(generateVersionString()).reset() +
                System.lineSeparator() +
                Ansi.ansi().a("Standalone Shell connected to ${configuration.hostAndPort}")
        println(banner)
    }

    private fun addColours(logo: String): String {
        // Replace the R and B letters with their colour code escapes to make the banner prettier.
        val red = Ansi.ansi().fgBrightRed().toString()
        val blue = Ansi.ansi().fgBrightBlue().toString()
        val default = Ansi.ansi().reset().toString()
        return logo.replace("R", red).replace("B", blue).replace("D", default)
    }

    private fun generateVersionString(): String {
        val versionString = "--- ${getManifestEntry("Corda-Vendor")} " +
                "${getManifestEntry("Corda-Release-Version")} ---"
        // Make sure the version string is padded to be the same length as the logo
        val paddingLength = Math.max(93 - versionString.length, 0)
        return versionString + "-".repeat(paddingLength) + System.lineSeparator()
    }
}
