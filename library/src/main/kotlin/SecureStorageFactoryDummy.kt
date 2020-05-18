import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory


class SecureStorageFactoryDummy : SecureStorageFactory
{
    companion object
    {
        var hashMap : HashMap<String, SecureStorageDummy> = HashMap<String, SecureStorageDummy> ()
    }

    override fun open(name: ByteArray): SecureStorage
    {
        if (hashMap[name.toString()] == null)
        {
            hashMap[name.toString()] = SecureStorageDummy()
        }
        return hashMap[name.toString()] as SecureStorageDummy
    }
}