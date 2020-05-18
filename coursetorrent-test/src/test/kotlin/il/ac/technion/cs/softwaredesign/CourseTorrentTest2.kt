package il.ac.technion.cs.softwaredesign

import HttpMock
import com.google.inject.Guice
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.anyElement
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import dev.misfitlabs.kotlinguice4.getInstance
import org.junit.jupiter.api.Test

class CourseTorrentTest2 {

    private val injector = Guice.createInjector(CourseTorrentTestModule())
    private val torrent = injector.getInstance<CourseTorrent>()
    private val debian = this::class.java.getResource("/debian-10.3.0-amd64-netinst.iso.torrent").readBytes()
    private val lame = this::class.java.getResource("/lame.torrent").readBytes()

    @Test
    fun `announce, check peers`() {
        val infohash = torrent.load(debian)

        HttpMock.setResponse(StringBuffer("d8:completei0e10:downloadedi2e10:incompletei2e8:intervali1950e12:min intervali975e5:peers12:012301987612e"))

        /* Returned peer list is: [("127.0.0.22", 6887)] */
        val inter = torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 2703360)

        assertThat(
                torrent.knownPeers(infohash),
                anyElement(has(KnownPeer::ip, equalTo("48.49.50.51")) and has(KnownPeer::port, equalTo(0x3031)))
        )
        assertThat(
                torrent.knownPeers(infohash),
                anyElement(has(KnownPeer::ip, equalTo("57.56.55.54")) and has(KnownPeer::port, equalTo(0x3132)))
        )
        assertThat(inter, equalTo(1950))
    }

    fun a2b_hex(s : String) : String
    {
        var s3 = s
        var s2 = ""
        while (s3.isNotEmpty())
        {
            val n = s3.substring(0,2).toInt(16)
            s2 += n.toChar()
            s3 = s3.substring(2)
        }
        return (s2)
    }

    @Test
    fun `scrape, check data`()
    {
        val infohash = torrent.load(debian)

        var s = a2b_hex("5a8062c076fa85e8056451c0d9aa04349ae27909")

        HttpMock.setResponse(StringBuffer("d5:filesd20:" + s + "d8:completei5e10:downloadedi50e10:incompletei10eeee"))

        torrent.scrape(infohash)

        assertThat(
                torrent.trackerStats(infohash),
                equalTo(mapOf(Pair("http://bttracker.debian.org:6969", Scrape(5, 50, 10, null) as ScrapeData)))
        )
    }

    fun `announce, invalidate peer`() {
        val infohash = torrent.load(debian)

        HttpMock.setResponse(StringBuffer("d8:completei0e10:downloadedi2e10:incompletei2e8:intervali1950e12:min intervali975e5:peers12:012301987612e"))

        /* Returned peer list is: [("127.0.0.22", 6887)] */
        val inter = torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 2703360)

        assertThat(
                torrent.knownPeers(infohash),
                anyElement(has(KnownPeer::ip, equalTo("48.49.50.51")) and has(KnownPeer::port, equalTo(0x3031)))
        )
        assertThat(
                torrent.knownPeers(infohash),
                anyElement(has(KnownPeer::ip, equalTo("57.56.55.54")) and has(KnownPeer::port, equalTo(0x3132)))
        )
        assertThat(inter, equalTo(1950))

        torrent.invalidatePeer(infohash, KnownPeer("57.56.55.54",0x3132,null))

        assertThat(
                torrent.knownPeers(infohash),
                anyElement(has(KnownPeer::ip, equalTo("48.49.50.51")) and has(KnownPeer::port, equalTo(0x3031)))
        )
        assertThat( torrent.knownPeers(infohash).size, equalTo(1));
    }

    @Test
    fun `announce, then announce fail`() {
        val infohash = torrent.load(debian)

        HttpMock.setResponse(StringBuffer("d8:completei0e10:downloadedi2e10:incompletei2e8:intervali1950e12:min intervali975e5:peers12:012301e"))

        /* Returned peer list is: [("127.0.0.22", 6887)] */
        val inter = torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 2703360)

        assertThat(
                torrent.knownPeers(infohash),
                anyElement(has(KnownPeer::ip, equalTo("48.49.50.51")) and has(KnownPeer::port, equalTo(0x3031)))
        )
        assertThat(inter, equalTo(1950))

        HttpMock.setResponse(StringBuffer("d14:failure reason1:fe"))

        torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 2703360)
        assertThat( torrent.knownPeers(infohash).size, equalTo(0));
    }

    @Test
    fun `announce overrides scrape data`()
    {
        val infohash = torrent.load(debian)

        var s = a2b_hex("5a8062c076fa85e8056451c0d9aa04349ae27909")

        HttpMock.setResponse(StringBuffer("d5:filesd20:" + s + "d8:completei5e10:downloadedi50e10:incompletei10eeee"))

        torrent.scrape(infohash)

        assertThat(
                torrent.trackerStats(infohash),
                equalTo(mapOf(Pair("http://bttracker.debian.org:6969", Scrape(5, 50, 10, null) as ScrapeData)))
        )

        HttpMock.setResponse(StringBuffer("d8:completei0e10:downloadedi2e8:intervali1950e12:min intervali975e5:peers12:012301e"))
        val inter = torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 2703360)

        assertThat(
                torrent.trackerStats(infohash),
                equalTo(mapOf(Pair("http://bttracker.debian.org:6969", Scrape(0, 2, 10, null) as ScrapeData)))
        )

    }


}