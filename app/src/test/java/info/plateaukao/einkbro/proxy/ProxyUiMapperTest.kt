package info.plateaukao.einkbro.proxy

import info.plateaukao.einkbro.core.mihomo.api.ControllerEndpoint
import info.plateaukao.einkbro.core.mihomo.api.MihomoException
import info.plateaukao.einkbro.core.mihomo.api.MihomoProfile
import info.plateaukao.einkbro.core.mihomo.api.MihomoSession
import info.plateaukao.einkbro.core.mihomo.api.ProxyEndpoint
import info.plateaukao.einkbro.preference.ProxyTransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyUiMapperTest {
    @Test
    fun disabledDirectIsOff() {
        val state = ProxyUiMapper.runtimeStatus(
            enabled = false,
            failClosed = true,
            transportMode = ProxyTransportMode.BROWSER_PROXY,
            browserState = MihomoBrowserState.Direct,
        )

        assertEquals(RuntimeUiStatus.Off, state)
    }

    @Test
    fun enabledDirectIsTemporaryDirect() {
        val state = ProxyUiMapper.runtimeStatus(
            enabled = true,
            failClosed = true,
            transportMode = ProxyTransportMode.BROWSER_PROXY,
            browserState = MihomoBrowserState.Direct,
        )

        assertEquals(RuntimeUiStatus.TemporaryDirect, state)
    }

    @Test
    fun failClosedFailureIsBlockedAndRedacted() {
        val state = ProxyUiMapper.runtimeStatus(
            enabled = true,
            failClosed = true,
            transportMode = ProxyTransportMode.BROWSER_PROXY,
            browserState = MihomoBrowserState.Failed(
                IllegalStateException("failed https://example.com/sub?token=secret-value")
            ),
        )

        assertTrue(state is RuntimeUiStatus.Blocked)
        assertTrue((state as RuntimeUiStatus.Blocked).reason.orEmpty().contains("secret-value").not())
    }

    @Test
    fun nonFailClosedFailureIsError() {
        val state = ProxyUiMapper.runtimeStatus(
            enabled = true,
            failClosed = false,
            transportMode = ProxyTransportMode.BROWSER_PROXY,
            browserState = MihomoBrowserState.Failed(IllegalStateException("boom")),
        )

        assertTrue(state is RuntimeUiStatus.Error)
    }

    @Test
    fun transportControlsProtectedLabel() {
        val session = MihomoSession(
            profile = MihomoProfile("id", "Japan", "/profile.yaml"),
            socksEndpoint = ProxyEndpoint("127.0.0.1", 20000),
            controllerEndpoint = ControllerEndpoint("127.0.0.1", 20001, "secret"),
        )

        val browser = ProxyUiMapper.runtimeStatus(
            enabled = true,
            failClosed = true,
            transportMode = ProxyTransportMode.BROWSER_PROXY,
            browserState = MihomoBrowserState.Proxied(session),
        )
        val vpn = ProxyUiMapper.runtimeStatus(
            enabled = true,
            failClosed = true,
            transportMode = ProxyTransportMode.STRICT_VPN,
            browserState = MihomoBrowserState.Proxied(session),
        )

        assertEquals(RuntimeUiStatus.ProtectedBrowserProxy("Japan"), browser)
        assertEquals(RuntimeUiStatus.ProtectedStrictVpn("Japan"), vpn)
    }

    @Test
    fun missingProfileErrorIsTypedAndMessageIsNotRaw() {
        val mapped = ProxyUiMapper.error(
            IllegalArgumentException("Import or select a profile before enabling Mihomo"),
            ProxyErrorCategory.RUNTIME,
        )

        assertEquals(ProxyErrorCategory.PROFILE_REQUIRED, mapped.category)
        assertEquals("", mapped.message)
    }

    @Test
    fun bridgeAbiMismatchIsAppIncompatible() {
        val mapped = ProxyUiMapper.error(
            MihomoException.BridgeAbiMismatch(expected = 3, actual = 4),
            ProxyErrorCategory.RUNTIME,
        )

        assertEquals(ProxyErrorCategory.APP_INCOMPATIBLE, mapped.category)
    }
}
