


import il.ac.technion.cs.softwaredesign.storage.SecureStorage



class SecureStorageDummy  : SecureStorage
{
    var hashMap : HashMap<ByteArray, ByteArray> = HashMap<ByteArray, ByteArray> ()

    override fun read(key: ByteArray): ByteArray?
    {
        return hashMap[key]
    }

    override fun write(key: ByteArray, value: ByteArray)
    {
        hashMap[key] = value
    }

}