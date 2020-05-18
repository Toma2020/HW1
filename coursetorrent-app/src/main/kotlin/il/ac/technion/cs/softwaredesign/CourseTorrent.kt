package il.ac.technion.cs.softwaredesign

import il.ac.technion.cs.softwaredesign.exceptions.TrackerException
import Bencoding
import DB_Mananger
import NetHandler
import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import kotlin.random.Random

import com.google.gson.Gson
import com.google.inject.Guice

/**
 * This is the class implementing CourseTorrent, a BitTorrent client.
 *
 * Currently specified:
 * + Parsing torrent metainfo files (".torrent" files)
 * + Communication with trackers (announce, scrape).
 */
class CourseTorrent  @Inject constructor(private val fac : SecureStorageFactory, private val netHandler : NetHandler) {

    private val announceDB = DB_Mananger(fac.open("announceDB".toByteArray()))
    private val peersDB = DB_Mananger(fac.open("peersDB".toByteArray()))
    private val scrapeDB = DB_Mananger(fac.open("scrapeDB".toByteArray()))
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

        if (!Torrent_Mananger.isValidMetaInfo(metaInfo)){
            throw IllegalArgumentException()
        }

        val infohash : String = Torrent_Mananger.getInfoHash(torrent)
        if (announceDB.DBContains(infohash)){
            throw IllegalStateException()
        }

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
        if (!announceDB.DBContains(infohash)){
            throw IllegalArgumentException()
        }
        announceDB.removeFromDB(infohash)
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
    fun announces(infohash: String): List<List<String>>
    {
        if (!announceDB.DBContains(infohash))
        {
            throw IllegalArgumentException()
        }
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
/*
        if(!announceDB.DBContains(infohash)) throw IllegalArgumentException()

        var announce_list = this.announces(infohash)
        var peerID = Torrent_Mananger.SHA1(("-CS1000-204289318879"+
                List(6) {Random.nextInt(0,9)}.joinToString("")).toByteArray())


        if(event == TorrentEvent.STARTED) {
            announce_list = announce_list.shuffled()
            var announce_list_gson = gson.toJson(announce_list).toByteArray()
            announceDB.loadToDB(infohash, announce_list_gson)
        }

        var compact = 1
        var errorMessage = ""
        var trackerSucceededFlag = false
        for (t in announce_list){
            var tracker : String = t.first()
            if(!tracker.endsWith("/announce")) tracker = tracker+"/announce"

            tracker = tracker + "?"
            tracker = tracker + "info_hash" + "=" + infohash
            tracker = tracker + "&" + "peer_id" + "=" + peerID
            tracker = tracker + "&" + "port" + "=" + 6881.toString()
            tracker = tracker + "&" + "uploaded" + "=" + uploaded.toString()
            tracker = tracker + "&" + "downloaded" + "=" + downloaded.toString()
            tracker = tracker + "&" + "left" + "=" + left.toString()
            tracker = tracker + "&" + "compact" + "=" + compact.toString()
            tracker = tracker + "&" + "event" + "=" + event.toString()

            print("\n\n\n\n\n\n!!!!!!!!!!!!!!!!!!!"+tracker+"!!!!!!!!!!!!!!!!!!!!!!!!\n" +
                    "\n" +
                    "\n" +
                    "\n" +
                    "\n" +
                    "\n")

            var bencoded_dict = HttpHandler.sendGetRequest(tracker).toString()
            var dict = Bencoding.decodeValue(bencoded_dict.toByteArray()) as Map<*,*>
            
            if(dict.containsKey("failure reason")){
                errorMessage = dict["failure reason"] as String
                continue
            }

            trackerSucceededFlag = true

            var list : MutableList<KnownPeer> = ArrayList<KnownPeer>()
            if(dict["peers"] is ArrayList<*>){
                  for (d in (dict["peers"] as ArrayList<*>)){
                      d as Map<*,*>
                      var new_peer = KnownPeer(ip = d["ip"] as String, port = d["port"] as Int, peerId = (d["peer id"] ?: null) as String?)
                      list.add(new_peer)
                  }
            } else {
                var peersss = (dict["peers"] as String)
                while (peersss.length > 0){
                    var new_peer = KnownPeer(peersss.substring(0,4), peersss.get(4).toInt()*256+peersss.get(5).toInt(), peerId = null)
                    list.add(new_peer)
                    peersss = peersss.substring(6)
                }
            }

            val jason = gson.toJson(list).toByteArray()
            peersDB.loadToDB(infohash, jason)
            return dict["interval"] as Int
        }
        
        if(!trackerSucceededFlag){
            throw TrackerException(errorMessage)
        }
*/
        return -1;
    }

    fun ip_selector(p: KnownPeer): Int = p.ip[0].toInt()*256*256*256+ p.ip[1].toInt()*256*256+ p.ip[2].toInt()*256+ p.ip[3].toInt()

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
        /*if(!scrapeDB.DBContains(infohash)) throw IllegalArgumentException()

        val announce_list = this.announces(infohash)

        val scrapeMap = HashMap<String,ScrapeData>()

        for (t in announce_list) {
            var tracker: String = t.first()


            val last_index = tracker.lastIndexOf(char = '/')
            if ((tracker.length - 1 < last_index + 8) || (tracker.substring(
                    last_index + 1,
                    last_index + 9
                ) != "announce")
            ) continue
            println("before:" + tracker)
            tracker = tracker.replaceRange(IntRange(last_index + 1, last_index + 9), "scrape")
            println("after:" + tracker)

            tracker = tracker + "?"
            tracker = tracker + "info_hash" + "=" + infohash

            val bencoded_dict = HttpHandler.sendGetRequest(tracker).toString()
            val fileDict = Bencoding.decodeValue(bencoded_dict.toByteArray()) as Map<*, *>

            for (file in fileDict.keys) {
                val scrape = fileDict[file] as Map<*, *>
                val complete = scrape["complete"] as Int
                val downloaded = scrape["downloaded"] as Int
                val incomplete = scrape["incomplete"] as Int
                val name = scrape["name"] as String?
                val new_scrape = Scrape(complete, downloaded, incomplete, name)
                scrapeMap[tracker] = new_scrape
            }
        }

        val scarpeJson = gson.toJson(scrapeMap).toByteArray()
        scrapeDB.loadToDB(infohash, scarpeJson)*/
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
        val list : ArrayList<KnownPeer> = this.knownPeers(infohash) as ArrayList

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
        var peerListJson = peersDB.loadFromDB(infohash).toString()
        return gson.fromJson<ArrayList<KnownPeer>>(peerListJson,  ArrayList::class.java)
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
        val jsonMap = scrapeDB.loadFromDB(infohash).toString()
        return gson.fromJson<HashMap<String,ScrapeData>>(jsonMap,  HashMap::class.java)
    }
}