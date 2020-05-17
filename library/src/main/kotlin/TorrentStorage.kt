import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import java.security.MessageDigest
import il.ac.technion.cs.softwaredesign.storage.SecureStorage

var DELETED_ENTRY : ByteArray  = "this entry deleted entry".toByteArray()


class DB_Mananger constructor(private val storageHandler : SecureStorage)
{
    private val storageIO = storageHandler;
    private var DELETED_ENTRY : ByteArray  = "this entry deleted entry".toByteArray()

    fun loadToDB(infoHash: String, announce_value: ByteArray){
        storageIO.write(key = infoHash.toByteArray(), value = announce_value)
    }

    fun removeFromDB (infoHash: String){
        storageIO.write(key = infoHash.toByteArray(),value = DELETED_ENTRY )
    }

    fun loadFromDB (infoHash: String):ByteArray{
        val inMemory : ByteArray? = storageIO.read(infoHash.toByteArray())
        if ( inMemory!=null && !inMemory.equals(DELETED_ENTRY)){
            return inMemory
        }

        throw IllegalArgumentException()
    }

    fun DBContains(infoHash : String) : Boolean {
        val inMemory : ByteArray? = storageIO.read(infoHash.toByteArray())
        if ( inMemory!=null && !inMemory.equals(DELETED_ENTRY)){
            return true
        }

        return false
    }

}

class Torrent_Mananger{
    companion object{
        fun isValidMetaInfo (metaInfo : Any?):Boolean{ // todo: check the type of the values
            if(!(metaInfo is HashMap<*,*>)) return false

            if(!metaInfo.containsKey("info") || !(metaInfo["info"] is HashMap<*,*>)) return false

            if(!metaInfo.containsKey("announce") ||  !(metaInfo["announce"] is String)) return false

            if( metaInfo.containsKey("announce-list")){
                if(!(metaInfo["announce-list"] is List<*>)) return false
                for (l in metaInfo["announce-list"] as List<*>){
                    if(!(l is List<*>)) return false
                    for(l2 in l){
                        if(!(l2 is String)) return false
                    }
                }
            }

            return true
        }

        fun getInfoHash (torrent : ByteArray) : String {
            val info = (Bencoding.decodeFlatDictionary(torrent))["info"]
            return SHA1(info)
        }

        private fun SHA1(convertme: ByteArray?): String {
            val md = MessageDigest.getInstance("SHA-1")
            return md.digest(convertme).joinToString(separator = ""){"%02x".format(it)}
        }
    }
}
