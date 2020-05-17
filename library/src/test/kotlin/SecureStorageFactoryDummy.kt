

import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory


class SecureStorageFactoryDummy : SecureStorageFactory
{
    override fun open(name: ByteArray): SecureStorage
    {
        return SecureStorageDummy();
    }
}