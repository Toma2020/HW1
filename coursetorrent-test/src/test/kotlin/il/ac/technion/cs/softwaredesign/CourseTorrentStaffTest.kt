package il.ac.technion.cs.softwaredesign

import DB_Mananger
import SecureStorageDummy
import com.google.inject.Guice
import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import dev.misfitlabs.kotlinguice4.getInstance
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import com.google.gson.Gson

class CourseTorrentStaffTest {
    private val injector = Guice.createInjector(CourseTorrentModule())
    private val torrent = injector.getInstance<CourseTorrent>()
    private val debian = this::class.java.getResource("/debian-10.3.0-amd64-netinst.iso.torrent").readBytes()
    private val lame = this::class.java.getResource("/lame.torrent").readBytes()

    fun sendGetRequest()
    {

        val mURL = URL("http://bt1.archive.org:6969/announce?info_hash=%ac%c3%b2%e43%d7%c7GZ%bbYA%b5h%1c%b7%a1%ea%26%e2&peer_id=ABCDEFGHIJKLMNOPQRST&ip=80.11.255.166&port=6881&downloaded=0&left=970")


        println("Response : " + "suka blat")
        with(mURL.openConnection() as HttpURLConnection)
        {
            // optional default is GET
            requestMethod = "GET"

            println("URL : $url")
            println("Response Code : $responseCode")

            BufferedReader(InputStreamReader(inputStream)).use {
                val response = StringBuffer()

                var inputLine = it.readLine()
                while (inputLine != null) {
                    response.append(inputLine)
                    inputLine = it.readLine()
                }
                it.close()
                val a = response.toString()
                println("Response : $a")
            }
        }
    }

    @Test
    fun `blat2`()
    {

        var list : MutableList<KnownPeer> = ArrayList<KnownPeer>()
        var new_peer = KnownPeer("1234", 100, peerId = "cusrabak")
        list.add(new_peer)
        var new_peer2 = KnownPeer("abcd", 56000, peerId = null)
        list.add(new_peer2)
        var new_peer3 = KnownPeer("pizd", 15, peerId = "a")
        list.add(new_peer3)

        val gson = Gson()
        val jason = gson.toJson(list).toByteArray()
        val db = DB_Mananger(SecureStorageDummy())
        db.loadToDB("infohash", jason)

        /*val data = db.loadFromDB("infohash")
        var peerListJson = db.loadFromDB("infohash").toString()
        val list2 = gson.fromJson<ArrayList<KnownPeer>>(peerListJson,  ArrayList::class.java)
*/
        val list3 = gson.fromJson<ArrayList<KnownPeer>>(String(jason),  ArrayList::class.java)
        println("yay")

    }


    @Test
    fun `blat`()
    {

        val peersss = "01"
        println(peersss.get(0).toInt()*256+peersss.get(1).toInt())
        val gson = Gson()
        val numbers = mutableListOf(1, 2, 3, 4)
        //gson.toJson(numbers).toby

        val x = 5
        var h : HashMap<Int, Int> = HashMap<Int, Int> ()
        h[3]
        val t = h[5]
        println("hash : $t")
        sendGetRequest()
        assertThat(x, equalTo(5))

    }

    @Test
    fun `after load, infohash calculated correctly`() {
        val infohash = torrent.load(debian)

        assertThat(infohash, equalTo("5a8062c076fa85e8056451c0d9aa04349ae27909"))
    }

    @Test
    fun `after load, announce is correct`() {
        val infohash = torrent.load(debian)

        val announces = assertDoesNotThrow { torrent.announces(infohash) }

        assertThat(announces, allElements(hasSize(equalTo(1))))
        assertThat(announces, hasSize(equalTo(1)))
        assertThat(announces, allElements(hasElement("http://bttracker.debian.org:6969/announce")))
    }

    @Test
    fun `client announces to tracker`() {
        val infohash = torrent.load(lame)

        /* interval is 360 */
        val interval = torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 0)

        assertThat(interval, equalTo(360))
        /* Assertion to verify that the tracker was actually called */
    }

    @Test
    fun `client scrapes tracker and updates statistics`() {
        val infohash = torrent.load(lame)

        /* Tracker has infohash, 0 complete, 0 downloaded, 0 incomplete, no name key */
        assertDoesNotThrow { torrent.scrape(infohash) }

        assertThat(
            torrent.trackerStats(infohash),
            equalTo(mapOf(Pair("http://127.0.0.1:8082", Scrape(0, 0, 0, null) as ScrapeData)))
        )
        /* Assertion to verify that the tracker was actually called */
    }

    @Test
    fun `after announce, client has up-to-date peer list`() {
        val infohash = torrent.load(lame)

        /* Returned peer list is: [("127.0.0.22", 6887)] */
        torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 2703360)
        /* Returned peer list is: [("127.0.0.22", 6887), ("127.0.0.21", 6889)] */
        torrent.announce(infohash, TorrentEvent.REGULAR, 0, 81920, 2621440)


        assertThat(
            torrent.knownPeers(infohash),
            anyElement(has(KnownPeer::ip, equalTo("127.0.0.22")) and has(KnownPeer::port, equalTo(6887)))
        )
        assertThat(
            torrent.knownPeers(infohash),
            anyElement(has(KnownPeer::ip, equalTo("127.0.0.21")) and has(KnownPeer::port, equalTo(6889)))
        )
        assertThat(
            torrent.knownPeers(infohash), equalTo(torrent.knownPeers(infohash).distinct())
        )
    }

    @Test
    fun `peers are invalidated correctly`() {
        val infohash = torrent.load(lame)
        /* Returned peer list is: [("127.0.0.22", 6887)] */
        torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 2703360)
        /* Returned peer list is: [("127.0.0.22", 6887), ("127.0.0.21", 6889)] */
        torrent.announce(infohash, TorrentEvent.REGULAR, 0, 81920, 2621440)

        torrent.invalidatePeer(infohash, KnownPeer("127.0.0.22", 6887, null))

        assertThat(
            torrent.knownPeers(infohash),
            anyElement(has(KnownPeer::ip, equalTo("127.0.0.21")) and has(KnownPeer::port, equalTo(6889))).not()
        )
    }
}