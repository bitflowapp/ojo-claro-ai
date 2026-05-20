package com.ojoclaro.android.agent.apps

import com.ojoclaro.android.agent.task.AgentTaskRiskLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AppCapabilityRegistryTest {

    private val registry = AppCapabilityRegistry()

    @Test
    fun registryKnowsUberAsRideHailing() {
        val uber = registry.findByAppName("Uber")

        assertNotNull(uber)
        assertEquals(AppCapabilityType.RIDE_HAILING, uber.type)
        assertEquals(AppCapabilityRegistry.UBER_PACKAGE, uber.packageName)
        assertTrue(uber.canOpenSafely)
    }

    @Test
    fun registryKnowsCabifyAsRideHailing() {
        val cabify = registry.findByAppName("Cabify")

        assertNotNull(cabify)
        assertEquals(AppCapabilityType.RIDE_HAILING, cabify.type)
        assertEquals(AppCapabilityRegistry.CABIFY_PACKAGE, cabify.packageName)
        assertTrue(cabify.canOpenSafely)
    }

    @Test
    fun registryMarksPaymentsAndBanksHighRisk() {
        val mercadoPago = registry.findByAppName("Mercado Pago")
        val banco = registry.findByAppName("Banco Galicia")

        assertNotNull(mercadoPago)
        assertEquals(AppCapabilityType.PAYMENTS, mercadoPago.type)
        assertEquals(AgentTaskRiskLevel.HIGH, mercadoPago.riskLevel)
        assertTrue(mercadoPago.requiresConfirmationToOpen)

        assertNotNull(banco)
        assertEquals(AppCapabilityType.PAYMENTS, banco.type)
        assertEquals(AgentTaskRiskLevel.HIGH, banco.riskLevel)
    }

    @Test
    fun fakeResolverDetectsInstalledApp() {
        val resolver = FakeInstalledAppResolver(setOf(AppCapabilityRegistry.UBER_PACKAGE))

        assertTrue(resolver.isPackageInstalled(AppCapabilityRegistry.UBER_PACKAGE))
        assertEquals(
            "Uber",
            registry.firstInstalledRideApp(resolver)?.appName
        )
    }

    @Test
    fun fakeResolverReturnsNotInstalled() {
        val resolver = FakeInstalledAppResolver(emptySet())

        assertTrue(registry.installedCapabilities(resolver).isEmpty())
    }
}

internal class FakeInstalledAppResolver(
    private val installedPackages: Set<String>
) : InstalledAppResolver {
    override fun isPackageInstalled(packageName: String): Boolean =
        installedPackages.any { it.equals(packageName, ignoreCase = true) }
}
