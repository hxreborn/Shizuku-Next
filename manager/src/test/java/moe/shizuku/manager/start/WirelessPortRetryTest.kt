package moe.shizuku.manager.start

import java.io.EOFException
import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import moe.shizuku.manager.adb.AdbPairingRequiredException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a start turns to a fresh wireless port, and when it must not.
 *
 * The cost of getting this wrong runs both ways: a start that does not look again gives up on a
 * port adbd has since replaced, and one that looks again after the starter went out starts a second
 * server over the first.
 */
class WirelessPortRetryTest {

    @Test
    fun `a wireless port nothing answers on is searched for again`() {
        assertEquals(
            PortRetryDecision.SEARCH_AGAIN,
            decideBeforeConnect(wireless = true, listening = false, searchesUsed = 0)
        )
    }

    @Test
    fun `a port that answers is used as it is`() {
        assertEquals(
            PortRetryDecision.USE_AS_IS,
            decideBeforeConnect(wireless = true, listening = true, searchesUsed = 0)
        )
    }

    @Test
    fun `the classic port is never searched for`() {
        assertEquals(
            PortRetryDecision.USE_AS_IS,
            decideBeforeConnect(wireless = false, listening = false, searchesUsed = 0)
        )
    }

    @Test
    fun `once the searches are used the port is left to say what is wrong`() {
        assertEquals(
            PortRetryDecision.USE_AS_IS,
            decideBeforeConnect(wireless = true, listening = false, searchesUsed = MAX_FRESH_PORT_SEARCHES)
        )
    }

    @Test
    fun `a failed connection to a wireless port asks for another one, up to the limit`() {
        for (used in 0 until MAX_FRESH_PORT_SEARCHES) {
            assertEquals(
                PortRetryDecision.SEARCH_AGAIN,
                decideAfterConnectFailure(true, false, ConnectFailureKind.REFUSED_OR_CLOSED, used)
            )
            assertEquals(
                PortRetryDecision.SEARCH_AGAIN,
                decideAfterConnectFailure(true, false, ConnectFailureKind.TLS, used)
            )
        }
        assertEquals(
            PortRetryDecision.GIVE_UP,
            decideAfterConnectFailure(
                true, false, ConnectFailureKind.REFUSED_OR_CLOSED, MAX_FRESH_PORT_SEARCHES
            )
        )
    }

    @Test
    fun `nothing is retried once the starter has gone out`() {
        assertEquals(
            PortRetryDecision.GIVE_UP,
            decideAfterConnectFailure(true, true, ConnectFailureKind.REFUSED_OR_CLOSED, 0)
        )
    }

    @Test
    fun `a rejected key is not a reason to look for another port`() {
        assertEquals(
            PortRetryDecision.GIVE_UP,
            decideAfterConnectFailure(true, false, ConnectFailureKind.PAIRING, 0)
        )
        assertEquals(
            PortRetryDecision.GIVE_UP,
            decideAfterConnectFailure(true, false, ConnectFailureKind.OTHER, 0)
        )
    }

    @Test
    fun `a classic port has no other port to find`() {
        assertEquals(
            PortRetryDecision.GIVE_UP,
            decideAfterConnectFailure(false, false, ConnectFailureKind.REFUSED_OR_CLOSED, 0)
        )
    }

    @Test
    fun `failures are told apart by what they say about the port`() {
        assertEquals(ConnectFailureKind.REFUSED_OR_CLOSED, classifyConnectFailure(ConnectException("refused")))
        assertEquals(ConnectFailureKind.REFUSED_OR_CLOSED, classifyConnectFailure(EOFException()))
        assertEquals(ConnectFailureKind.REFUSED_OR_CLOSED, classifyConnectFailure(SocketTimeoutException()))
        assertEquals(ConnectFailureKind.TLS, classifyConnectFailure(SSLException("handshake")))
        assertEquals(ConnectFailureKind.OTHER, classifyConnectFailure(IllegalStateException()))
    }

    @Test
    fun `a handshake that failed for another reason is not pairing`() {
        val wrongProtocol = SSLException("WRONG_VERSION_NUMBER")
        assertEquals(
            ConnectFailureKind.TLS,
            classifyConnectFailure(AdbPairingRequiredException(wrongProtocol.message, wrongProtocol))
        )
    }

    @Test
    fun `the device refusing the key is pairing`() {
        val refused = SSLException("SSLV3_ALERT_CERTIFICATE_UNKNOWN")
        assertEquals(
            ConnectFailureKind.PAIRING,
            classifyConnectFailure(AdbPairingRequiredException(refused.message, refused))
        )
        assertTrue(isPairingRejection("tlsv1 alert: CERTIFICATE_UNKNOWN"))
        assertFalse(isPairingRejection("Connection closed by peer"))
        assertFalse(isPairingRejection(null))
    }
}
