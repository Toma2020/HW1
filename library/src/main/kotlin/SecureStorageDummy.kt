import il.ac.technion.cs.softwaredesign.storage.SecureStorage



class SecureStorageDummy  : SecureStorage
{
    var hashMap : HashMap<String, ByteArray> = HashMap<String, ByteArray> ()

    override fun read(key: ByteArray): ByteArray?
    {
        return hashMap[String(key)]
    }

    override fun write(key: ByteArray, value: ByteArray)
    {
        hashMap[String(key)] = value
    }

}