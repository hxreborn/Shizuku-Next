package moe.shizuku.manager.start

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether a start has to wait for a network, which is the difference between a start that
 * happens on a device with no Wi-Fi and one that sits there until a network appears.
 */
class StartTransportTest {

    @Test
    fun `a classic port with TCP mode on needs no network`() {
        assertFalse(StartTransport.wifiRequired(tcpPort = 5555, tcpMode = true))
    }

    @Test
    fun `TCP mode off closes the port, so the network is needed again`() {
        assertTrue(StartTransport.wifiRequired(tcpPort = 5555, tcpMode = false))
    }

    @Test
    fun `no port means discovery, which means the network`() {
        assertTrue(StartTransport.wifiRequired(tcpPort = -1, tcpMode = true))
        assertTrue(StartTransport.wifiRequired(tcpPort = 0, tcpMode = true))
    }

    @Test
    fun `the classic port is a fallback whenever there is one`() {
        assertEquals(5555, StartTransport.classicPortFallback(5555))
    }

    @Test
    fun `there is nothing to fall back to without a port`() {
        assertNull(StartTransport.classicPortFallback(-1))
        assertNull(StartTransport.classicPortFallback(0))
    }

    @Test
    fun `the experiment does not wait for a network either`() {
        // Waiting would gate the start the setting exists to make possible.
        assertFalse(StartTransport.wifiRequired(tcpPort = -1, tcpMode = false, forceWireless = true))
        assertFalse(StartTransport.wifiRequired(tcpPort = 5555, tcpMode = false, forceWireless = true))
    }

    // -- which port a start uses ----------------------------------------------------------------

    @Test
    fun `a start with TCP mode on and a live port uses that port`() {
        // The transport the promise above is about: the port TCP mode keeps open is the one the
        // start should take, rather than looking for a wireless one over the network it said it
        // did not need.
        assertTrue(
            StartTransport.classicPortInUse(
                usbMethod = false,
                tlsSupported = true,
                tcpPort = 5555,
                tcpMode = true,
                portListening = true
            )
        )
    }

    @Test
    fun `TCP mode without a port leaves the wireless search alone`() {
        // A mode is not the port itself: with nothing listening there is nothing to use, and
        // discovery is the only way to a port at all.
        assertFalse(
            StartTransport.classicPortInUse(
                usbMethod = false,
                tlsSupported = true,
                tcpPort = -1,
                tcpMode = true,
                portListening = false
            )
        )
        assertFalse(
            StartTransport.classicPortInUse(
                usbMethod = false,
                tlsSupported = true,
                tcpPort = 0,
                tcpMode = true,
                portListening = false
            )
        )
    }

    @Test
    fun `a port that is only a number in the setting is not taken`() {
        // Issue #71. `service.adb.tcp.port` outlives adbd, so a phone whose debugging toggles were
        // turned off when Shizuku stopped still reads the port the daemon was using. Taken, the
        // start connects to nothing and never reaches the search that writes wireless debugging
        // back on; left to discovery, the search brings the daemon and the toggle back with it.
        assertFalse(
            StartTransport.classicPortInUse(
                usbMethod = false,
                tlsSupported = true,
                tcpPort = 5555,
                tcpMode = true,
                portListening = false
            )
        )
    }

    // -- which ports a start asks before searching ---------------------------------------------

    @Test
    fun `the platform's port, the last one used and ours are all tried, in that order`() {
        // The order is the point: the platform's answer is a fact, the last port is a good guess
        // (a wireless port is random per boot), and the app's own port is where it would open one.
        assertEquals(
            listOf(5555, 43279, 5556),
            StartTransport.portCandidates(configured = 5555, lastStart = 43279, appTcpPort = 5556)
        )
    }

    @Test
    fun `the same port named twice is asked once`() {
        assertEquals(
            listOf(5555, 43279),
            StartTransport.portCandidates(configured = 5555, lastStart = 43279, appTcpPort = 5555)
        )
    }

    @Test
    fun `a start that has recorded nothing has only the platform's port to try`() {
        assertEquals(
            listOf(5555),
            StartTransport.portCandidates(configured = 5555, lastStart = -1, appTcpPort = 0)
        )
    }

    @Test
    fun `nothing to try means the search, which is what an empty list asks for`() {
        assertTrue(StartTransport.portCandidates(configured = -1, lastStart = -1, appTcpPort = 0).isEmpty())
        assertTrue(StartTransport.portCandidates(configured = 0, lastStart = 0, appTcpPort = 0).isEmpty())
    }

    @Test
    fun `a port left open with TCP mode off is not taken by a wireless start`() {
        // Unchanged from before: the mode is what says a port is wanted, and one left behind is not
        // an invitation to skip discovery.
        assertFalse(
            StartTransport.classicPortInUse(
                usbMethod = false,
                tlsSupported = true,
                tcpPort = 5555,
                tcpMode = false,
                portListening = true
            )
        )
    }

    @Test
    fun `the USB method and platforms without TLS use the classic port as before`() {
        assertTrue(
            StartTransport.classicPortInUse(
                usbMethod = true,
                tlsSupported = true,
                tcpPort = -1,
                tcpMode = false,
                portListening = false
            )
        )
        assertTrue(
            StartTransport.classicPortInUse(
                usbMethod = false,
                tlsSupported = false,
                tcpPort = -1,
                tcpMode = false,
                portListening = false
            )
        )
    }

    @Test
    fun `the USB method keeps its port with the daemon down`() {
        // A USB start is the port or nothing: it never hands over to wireless, so a dead one is the
        // failure it reports rather than a reason to look for another transport.
        assertTrue(
            StartTransport.classicPortInUse(
                usbMethod = true,
                tlsSupported = true,
                tcpPort = 5555,
                tcpMode = false,
                portListening = false
            )
        )
    }

    @Test
    fun `the two answers agree when a port is what removes the need for a network`() {
        val tcpPort = 5555
        val tcpMode = true

        assertFalse(StartTransport.wifiRequired(tcpPort, tcpMode))
        assertTrue(
            StartTransport.classicPortInUse(
                usbMethod = false,
                tlsSupported = true,
                tcpPort = tcpPort,
                tcpMode = tcpMode,
                portListening = true
            )
        )
    }

    @Test
    fun `candidates keep the order and say where each came from`() {
        assertEquals(
            listOf(
                StartTransport.PortCandidate(5555, StartTransport.PortSource.CLASSIC_PROPERTY),
                StartTransport.PortCandidate(40001, StartTransport.PortSource.TLS_PROPERTY),
                StartTransport.PortCandidate(43279, StartTransport.PortSource.REMEMBERED),
                StartTransport.PortCandidate(5556, StartTransport.PortSource.APP_SETTING)
            ),
            StartTransport.candidatePorts(
                configured = 5555, tlsProperty = 40001, remembered = 43279, appTcpPort = 5556
            )
        )
    }

    @Test
    fun `a port two guesses agree on is asked once, under the first`() {
        assertEquals(
            listOf(StartTransport.PortCandidate(5555, StartTransport.PortSource.CLASSIC_PROPERTY)),
            StartTransport.candidatePorts(
                configured = 5555, tlsProperty = -1, remembered = 5555, appTcpPort = 5555
            )
        )
    }

    @Test
    fun `candidates without a port are dropped`() {
        assertTrue(
            StartTransport.candidatePorts(
                configured = -1, tlsProperty = 0, remembered = null, appTcpPort = 0
            ).isEmpty()
        )
    }

    @Test
    fun `a wireless port from an earlier boot is not remembered`() {
        assertEquals(
            StartTransport.RememberedPort.OTHER_BOOT,
            StartTransport.rememberedPortVerdict(
                lastPort = 36371, lastWasClassic = false, lastBoot = 41, currentBoot = 42, appTcpPort = 5555
            )
        )
    }

    @Test
    fun `a wireless port from this boot is remembered`() {
        assertEquals(
            StartTransport.RememberedPort.USABLE,
            StartTransport.rememberedPortVerdict(
                lastPort = 36371, lastWasClassic = false, lastBoot = 42, currentBoot = 42, appTcpPort = 5555
            )
        )
    }

    @Test
    fun `a wireless port with no boot recorded is not trusted`() {
        assertEquals(
            StartTransport.RememberedPort.UNKNOWN_BOOT,
            StartTransport.rememberedPortVerdict(
                lastPort = 36371, lastWasClassic = false, lastBoot = -1, currentBoot = 42, appTcpPort = 5555
            )
        )
        assertEquals(
            StartTransport.RememberedPort.UNKNOWN_BOOT,
            StartTransport.rememberedPortVerdict(
                lastPort = 36371, lastWasClassic = false, lastBoot = 42, currentBoot = -1, appTcpPort = 5555
            )
        )
    }

    @Test
    fun `the classic port survives a restart`() {
        assertEquals(
            StartTransport.RememberedPort.USABLE,
            StartTransport.rememberedPortVerdict(
                lastPort = 5555, lastWasClassic = true, lastBoot = 41, currentBoot = 42, appTcpPort = 5555
            )
        )
        // Saved by a version that did not record the transport's boot, but it is the app's own port.
        assertEquals(
            StartTransport.RememberedPort.USABLE,
            StartTransport.rememberedPortVerdict(
                lastPort = 5555, lastWasClassic = false, lastBoot = -1, currentBoot = 42, appTcpPort = 5555
            )
        )
    }

    @Test
    fun `nothing recorded is nothing remembered`() {
        assertEquals(
            StartTransport.RememberedPort.NONE,
            StartTransport.rememberedPortVerdict(
                lastPort = -1, lastWasClassic = false, lastBoot = -1, currentBoot = 42, appTcpPort = 5555
            )
        )
    }
}
