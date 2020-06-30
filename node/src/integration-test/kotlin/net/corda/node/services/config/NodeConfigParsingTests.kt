package net.corda.node.services.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.core.internal.delete
import net.corda.core.utilities.getOrThrow
import net.corda.node.logging.logFile
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.incrementalPortAllocation
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class NodeConfigParsingTests {
    private var baseDir: Path? = null

    @Before
    fun setup() {
        baseDir = Files.createTempDirectory("corda_config")
    }

    @After
    fun cleanup() {
        baseDir?.delete()
    }

    @Test(timeout = 3_000)
    fun `config is overriden by underscore variable`() {
        val sshPort: Long = 9000

        // Verify the port isn't set when not provided
        var config = loadConfig()
        Assert.assertFalse("SSH port should not be configured when not provided", config!!.hasPath("sshd.port"))

        config = loadConfig("corda_sshd_port" to sshPort)
        Assert.assertEquals(sshPort, config?.getLong("sshd.port"))
    }

    @Test(timeout = 3_000)
    fun `config is overriden by case insensitive underscore variable`() {
        val sshPort: Long = 10000
        val config = loadConfig("CORDA_sshd_port" to sshPort)
        Assert.assertEquals(sshPort, config?.getLong("sshd.port"))
    }

    @Test(timeout = 3_000)
    fun `config is overriden by case insensitive dot variable`() {
        val sshPort: Long = 11000
        val config = loadConfig("CORDA.sshd.port" to sshPort,
                "corda.devMode" to true.toString())
        Assert.assertEquals(sshPort, config?.getLong("sshd.port"))
    }

    @Test(timeout = 3_000, expected = ShadowingException::class)
    fun `shadowing is forbidden`() {
        val sshPort: Long = 12000
        loadConfig("CORDA_sshd_port" to sshPort.toString(),
                "corda.sshd.port" to sshPort.toString())
    }

    /**
     * Load the node configuration with the given environment variable
     * overrides.
     *
     * @param environmentVariables pairs of keys and values for environment variables
     * to simulate when loading the configuration.
     */
    @Suppress("SpreadOperator")
    private fun loadConfig(vararg environmentVariables: Pair<String, Any>): Config? {
        return baseDir?.let {
            ConfigHelper.loadConfig(
                    baseDirectory = it,
                    allowMissingConfig = true,
                    rawSystemOverrides = ConfigFactory.empty(),
                    rawEnvironmentOverrides = ConfigFactory.empty().plus(
                            mapOf(*environmentVariables)
                    )
            )
        }
    }

    @Test(timeout = 300_000)
    fun `bad keys are ignored and warned for`() {
        val portAllocator = incrementalPortAllocation()
        driver(DriverParameters(
                environmentVariables = mapOf(
                        "corda_bad_key" to "2077"),
                startNodesInProcess = false,
                portAllocation = portAllocator,
                notarySpecs = emptyList())) {

            val hasWarning = startNode()
                    .getOrThrow()
                    .logFile()
                    .readLines()
                    .any {
                        it.contains("(property or environment variable) cannot be mapped to an existing Corda")
                    }
            assertTrue(hasWarning)
        }
    }
}