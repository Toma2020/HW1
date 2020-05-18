package il.ac.technion.cs.softwaredesign

import il.ac.technion.cs.softwaredesign.exceptions.TrackerException
import Bencoding
import DB_Mananger
import URL_Utils
import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import kotlin.random.Random
import java.lang.reflect.Type
import com.google.common.reflect.TypeToken

import com.google.gson.Gson

import java.net.URL

/**
 * This is the class implementing CourseTorrent, a BitTorrent client.
 *
 * Currently specified:
 * + Parsing torrent metainfo files (".torrent" files)
 * + Communication with trackers (announce, scrape).
 */
class CourseTorrent  @Inject constructor(private val fac : SecureStorageFactory) {

    private val announceDB = DB_Mananger(fac.open("announceDB".toByteArray()))
    private val peersDB = DB_Mananger(fac.open("peersDB".toByteArray()))
    private val scrapeDB = DB_Mananger(fac.open("scrapeDB".toByteArray()))
    private val charset = Charsets.UTF_8
    private val gson = Gson()

    /**
     * Load in the torrent metainfo file from [torrent]. The specification for these files can be found here:
     * [Metainfo File Structure](https://wiki.theory.org/index.php/BitTorrentSpecification#Metainfo_File_Structure).
     *
     * After loading a torrent, it will be available in the system, and queries on it will succeed.
     *
     * This is a *create* command.
     *
     * @throws IllegalArgumentException If [torrent] is not a valid metainfo file.
     * @throws IllegalStateException If the infohash of [torrent] is already loaded.
     * @return The infohash of the torrent, i.e., the SHA-1 of the `info` key of [torrent].
     */
    fun load(torrent: ByteArray): String {

        val metaInfo = Bencoding.decodeValue(torrent)

        if (!Torrent_Mananger.isValidMetaInfo(metaInfo))throw IllegalArgumentException()

        val infohash : String = Torrent_Mananger.getInfoHash(torrent)
        if (announceDB.DBContains(infohash)) throw IllegalStateException()


        val shallowTorrentMap = Bencoding.decodeFlatDictionary(torrent)
        val announce = shallowTorrentMap["announce-list"] ?: shallowTorrentMap["announce"]

        announceDB.loadToDB(infohash, announce!!)

        return infohash
    }

    /**
     * Remove the torrent identified by [infohash] from the system.
     *
     * This is a *delete* command.
     *
     * @throws IllegalArgumentException If [infohash] is not loaded.
     */
    fun unload(infohash: String): Unit {
        if (!announceDB.DBContains(infohash)) throw IllegalArgumentException()

        announceDB.removeFromDB(infohash)
        peersDB.removeFromDB(infohash)
        scrapeDB.removeFromDB(infohash)

    }

    /**
     * Return the announce URLs for the loaded torrent identified by [infohash].
     *
     * See [BEP 12](http://bittorrent.org/beps/bep_0012.html) for more information. This method behaves as follows:
     * * If the "announce-list" key exists, it will be used as the source for announce URLs.
     * * If "announce-list" does not exist, "announce" will be used, and the URL it contains will be in tier 1.
     * * The announce URLs should *not* be shuffled.
     *
     * This is a *read* command.
     *
     * @throws IllegalArgumentException If [infohash] is not loaded.
     * @return Tier lists of announce URLs.
     */
    fun announces(infohash: String): List<List<String>> {
        if (!announceDB.DBContains(infohash)) throw IllegalArgumentException()

        val announce_bencoding = announceDB.loadFromDB(infohash)
        val announce = Bencoding.decodeValue(announce_bencoding)
        if(announce is String){
            return listOf(listOf(announce))
        } else if(announce is List<*>){
            return announce  as List<List<String>>
        }
        throw IllegalArgumentException()
    }

    /**
     * Send an "announce" HTTP request to a single tracker of the torrent identified by [infohash], and update the
     * internal state according to the response. The specification for these requests can be found here:
     * [Tracker Protocol](https://wiki.theory.org/index.php/BitTorrentSpecification#Tracker_HTTP.2FHTTPS_Protocol).
     *
     * If [event] is [TorrentEvent.STARTED], shuffle the announce-list before selecting a tracker (future calls to
     * [announces] should return the shuffled list). See [BEP 12](http://bittorrent.org/beps/bep_0012.html) for more
     * information on shuffling and selecting a tracker.
     *
     * [event], [uploaded], [downloaded], and [left] should be included in the tracker request.
     *
     * The "compact" parameter in the request should be set to "1", and the implementation should support both compact
     * and non-compact peer lists.
     *
     * Peer ID should be set to "-CS1000-{Student ID}{Random numbers}", where {Student ID} is the first 6 characters
     * from the hex-encoded SHA-1 hash of the student's ID numbers (i.e., `hex(sha1(student1id + student2id))`), and
     * {Random numbers} are 6 random characters in the range [0-9a-zA-Z] generated at instance creation.
     *
     * If the connection to the tracker failed or the tracker returned a failure reason, the next tracker in the list
     * will be contacted and the announce-list will be updated as per
     * [BEP 12](http://bittorrent.org/beps/bep_0012.html).
     * If the final tracker in the announce-list has failed, then a [TrackerException] will be thrown.
     *
     * This is an *update* command.
     *
     * @throws TrackerException If the tracker returned a "failure reason". The failure reason will be the exception
     * message.
     * @throws IllegalArgumentException If [infohash] is not loaded.
     * @return The interval in seconds that the client should wait before announcing again.
     */
    fun announce(infohash: String, event: TorrentEvent, uploaded: Long, downloaded: Long, left: Long): Int {

        // check if info hash is in the DB
        if(!announceDB.DBContains(infohash)) throw IllegalArgumentException()


        // get announceList
        var announceListList = this.announces(infohash)


        // if event is STARTED then shuffle
        if(event == TorrentEvent.STARTED) {
            announceListList = announceListList.shuffled()
            //if shuffled, we also need to update in the announceDB
            val jsonAnnounceList = Bencoding.encode(announceListList)
            announceDB.loadToDB(infohash, jsonAnnounceList.toByteArray())
        }

        lateinit var scrapeMap : HashMap<String, ScrapeData>
        try{
            val jsonScrapeList = scrapeDB.loadFromDB(infohash).toString(charset)
            //scrapeMap = gson.fromJson<HashMap<String, ScrapeData>>(jsonScrapeList, HashMap::class.java)

            val type: Type = object : TypeToken<HashMap<String,Scrape>>() {}.getType()
            scrapeMap = gson.fromJson(jsonScrapeList, type)

        } catch (e : IllegalArgumentException){

            scrapeMap = HashMap<String, ScrapeData>()

        }


        // parameters for the 'for' loop
        var trackerFoundFlag = false
        var resultInterval = -1

        // parameters for request
        val peerID = "-CS1000-" + Torrent_Mananger.SHA1(("204289"+"318879").toString().toByteArray()).slice(IntRange(0,5)) +
                List(6) {Random.nextInt(0,9)}.joinToString("")

        val compact = 1
        var errorMessage = ""
        val port = 6881

        for (announceList in announceListList){

            if (trackerFoundFlag) break

            for (t in announceList){

                // parameters for url
                var tracker = t
                val scrapeID = URL_Utils.removeAnnounceFromUrl(tracker)

                val httpRequest = HttpHandler.announceRequestFormat(tracker, infohash, peerID, port, uploaded, downloaded, left, compact, event)

                //print("\n\n\n !!!!!" +httpRequest+ "\n\n\n!!!!")

                val httpResponse= HttpHandler.sendGetRequest(httpRequest)

                val dict = Bencoding.decodeValue(httpResponse) as Map<*,*>

                // if request failed, update error reason and continue for next loop
                if(dict.containsKey("failure reason")){

                    errorMessage = dict["failure reason"] as String
                    val failure = Failure(errorMessage)

                    scrapeMap[scrapeID] = failure

                    continue
                }

                // if we get here, the request succeed
                trackerFoundFlag = true

                val flatDict=Bencoding.decodeFlatDictionary(httpResponse) as Map<*,*>
                val peersList : ArrayList<KnownPeer> = getPeersList(flatDict["peers"] as ByteArray)

                val jsonPeersList = gson.toJson(peersList).toByteArray()
                peersDB.loadToDB(infohash, jsonPeersList)

                updateScrapeStatsFromAnnounceResponse(dict["complete"] as Int?, dict["incomplete"] as Int? , scrapeMap ,scrapeID)

                resultInterval = (dict["interval"] as Long).toInt()

                break
            }
        }


        // update the scrape
        val scarpeLiJson = gson.toJson(scrapeMap).toByteArray()
        scrapeDB.loadToDB(infohash, scarpeLiJson)


        if (trackerFoundFlag) return resultInterval
        else throw TrackerException(errorMessage)

        // should not get here
        return -1
    }

    /**
     * Scrape all trackers identified by a torrent, and store the statistics provided. The specification for the scrape
     * request can be found here:
     * [Scrape Protocol](https://wiki.theory.org/index.php/BitTorrentSpecification#Tracker_.27scrape.27_Convention).
     *
     * All known trackers for the torrent will be scraped.
     *
     * This is an *update* command.
     *
     * @throws IllegalArgumentException If [infohash] is not loaded.
     */
    fun scrape(infohash: String): Unit {
        if(!announceDB.DBContains(infohash)) throw IllegalArgumentException()

        val announceListList = this.announces(infohash)
        val announceList = URL_Utils.announceUrls(announceListList)

        val scrapeMap = HashMap<String,ScrapeData>()

        for (t in announceList) {
            var tracker: String = t
            val scrapeID = URL_Utils.removeAnnounceFromUrl(tracker)

            val lastIndexOfBackslash = tracker.lastIndexOf(char = '/')
            if(!tracker.substring(lastIndexOfBackslash+1).startsWith("announce")) continue
            tracker = tracker.replaceRange(IntRange(lastIndexOfBackslash + 1, lastIndexOfBackslash + 8), "scrape")

            val request = HttpHandler.scrapeRequestFormat(tracker, infohash)

            val bencoded_dict = HttpHandler.sendGetRequest(request)
            val fileDict = Bencoding.decodeValue(bencoded_dict) as Map<*, *>

            val fffDict = fileDict["files"] as Map<*,*>

            for (file in fffDict.keys) {

                val scrape = fffDict[file] as Map<*, *>

                val complete = (scrape["complete"] as Long).toInt()
                val downloaded = (scrape["downloaded"] as Long).toInt()
                val incomplete = (scrape["incomplete"] as Long).toInt()
                val name = scrape["name"] as String?

                val new_scrape = Scrape(complete, downloaded, incomplete, name)
                scrapeMap[scrapeID] = new_scrape
            }
        }

        val jsonScrapeList = gson.toJson(scrapeMap).toByteArray()
        scrapeDB.loadToDB(infohash, jsonScrapeList)
    }

    /**
     * Invalidate a previously known peer for this torrent.
     *
     * If [peer] is not a known peer for this torrent, do nothing.
     *
     * This is an *update* command.
     *
     * @throws IllegalArgumentException If [infohash] is not loaded.
     */
    fun invalidatePeer(infohash: String, peer: KnownPeer): Unit {

        // this check is already done in knownPeers
        // if(!announceDB.DBContains(infohash)) throw IllegalArgumentException()
        val list : ArrayList<KnownPeer> = this.knownPeers(infohash) as ArrayList<KnownPeer>

        var isChanged = false
        for (p in list){
            if (p == peer){
                isChanged = true
                list.remove(p)
            }
        }
        if(isChanged){
            val jsonlist = gson.toJson(list).toByteArray()
            peersDB.loadToDB(infohash, jsonlist)
        }
    }

    /**
     * Return all known peers for the torrent identified by [infohash], in sorted order. This list should contain all
     * the peers that the client can attempt to connect to, in ascending numerical order. Note that this is not the
     * lexicographical ordering of the string representation of the IP addresses: i.e., "127.0.0.2" should come before
     * "127.0.0.100".
     *
     * The list contains unique peers, and does not include peers that have been invalidated.
     *
     * This is a *read* command.
     *
     * @throws IllegalArgumentException If [infohash] is not loaded.
     * @return Sorted list of known peers.
     */

    fun knownPeers(infohash: String): List<KnownPeer> {
        if(!announceDB.DBContains(infohash)) throw IllegalArgumentException()

        lateinit var peerList : ArrayList<KnownPeer>
        try{
            val peerListJson = peersDB.loadFromDB(infohash).toString(charset)
            peerList =  gson.fromJson<ArrayList<KnownPeer>>(peerListJson,  ArrayList::class.java) as ArrayList<KnownPeer>
        } catch (e : IllegalArgumentException){
            peerList = ArrayList<KnownPeer>()
        }

        return peerList.sortedBy {ipSelector(it)}
    }

    /**
     * Return all known statistics from trackers of the torrent identified by [infohash]. The statistics displayed
     * represent the latest information seen from a tracker.
     *
     * The statistics are updated by [announce] and [scrape] calls. If a response from a tracker was never seen, it
     * will not be included in the result. If one of the values of [ScrapeData] was not included in any tracker response
     * (e.g., "downloaded"), it would be set to 0 (but if there was a previous result that did include that value, the
     * previous result would be shown).
     *
     * If the last response from the tracker was a failure, the failure reason would be returned ([ScrapeData] is
     * defined to allow for this). If the failure was a failed connection to the tracker, the reason should be set to
     * "Connection failed".
     *
     * This is a *read* command.
     *
     * @throws IllegalArgumentException If [infohash] is not loaded.
     * @return A mapping from tracker announce URL to statistics.
     */
    fun trackerStats(infohash: String): Map<String, ScrapeData> {

        if(!announceDB.DBContains(infohash)) throw IllegalArgumentException()

        val jsonScrapeList = scrapeDB.loadFromDB(infohash).toString(charset)
        //scrapeMap = gson.fromJson<HashMap<String, ScrapeData>>(jsonScrapeList, HashMap::class.java)

        val type: Type = object : TypeToken<HashMap<String,Scrape>>() {}.getType()
        val scrapeMap : HashMap<String,Scrape> = gson.fromJson(jsonScrapeList, type)
        return scrapeMap
    }


    private fun getPeersList(peers:ByteArray):ArrayList<KnownPeer>{
        val decodedPeers = Bencoding.decodeValue(peers)

        if(decodedPeers is HashMap<*,*>) return getPeersListFromDict(decodedPeers)
        else if(decodedPeers is String){
            val colonIndex = peers.indexOfFirst { it == ':'.toByte() }
            val ppeers = peers.sliceArray(IntRange(colonIndex + 1, peers.lastIndex))
            return getPeersListFromBinary(ppeers)
        }
        else throw IllegalArgumentException()
    }


    private fun getPeersListFromDict(peers : HashMap<*,*>):ArrayList<KnownPeer>{
        val list : ArrayList<KnownPeer> = ArrayList<KnownPeer>()
        for (d in peers){
            d as Map<*,*>
            val peerIP = d["ip"] as String
            val peerPort = d["port"] as Int
            val peerID2 = (d["peer id"] ?: null) as String?
            val newPeer = KnownPeer(peerIP, peerPort, peerID2)
            list.add(newPeer)
        }
        return list
    }


    private fun getPeersListFromBinary(peers : ByteArray):ArrayList<KnownPeer>{

        val list : ArrayList<KnownPeer> = ArrayList<KnownPeer>()

        var peersss = peers

        while (peersss.size > 0){
            var new_peer = KnownPeer(byte2int(peersss[0]).toString(10) + "." + byte2int(peersss[1]).toString(10) + "." + byte2int(peersss[2]).toString(10) + "." + byte2int(peersss[3]).toString(10),
                    byte2int(peersss[4])*256+byte2int(peersss[5]), peerId = null)
            list.add(new_peer)
            peersss = peersss.sliceArray(IntRange(6, peersss.lastIndex))
        }
        return ArrayList<KnownPeer>()
    }


    fun ipSelector(p: KnownPeer):  Long {
        val ip = p.ip.split('.')
        return ip[0].toLong()*256*256*256+ ip[1].toLong()*256*256+ ip[2].toLong()*256+ ip[3].toLong()
    }

    private fun updateScrapeStatsFromAnnounceResponse
            (complete:Int?, incomplete:Int?, scrapeMap:HashMap<String, ScrapeData> ,scrapeID:String):Unit{

        if(complete == null && incomplete == null) return

        val curScrape : ScrapeData? = scrapeMap[scrapeID]

        if (curScrape == null || curScrape is Failure){

            val newComplete: Int = complete ?: 0
            val newDownloaded: Int = 0
            val newIncomplete: Int = incomplete ?: 0
            val newName: String? = null
            val newScrape = Scrape (newComplete, newDownloaded, newIncomplete, newName)
            scrapeMap[scrapeID] = newScrape

        } else if(curScrape is Scrape){

            val newComplete: Int = complete ?: curScrape.complete
            val newDownloaded: Int = curScrape.downloaded
            val newIncomplete: Int = incomplete ?: curScrape.incomplete
            val newName: String? = curScrape.name
            val newScrape = Scrape (newComplete, newDownloaded, newIncomplete, newName)
            scrapeMap[scrapeID] = newScrape

        }
    }

    private fun byte2int(b : Byte) : Int {
        return b.toInt() and 0xff
    }

}


