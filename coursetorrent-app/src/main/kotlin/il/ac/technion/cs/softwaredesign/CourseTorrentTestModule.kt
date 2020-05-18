package il.ac.technion.cs.softwaredesign

import HttpHandler
import HttpMock
import NetHandler
import dev.misfitlabs.kotlinguice4.KotlinModule
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import SecureStorageFactoryDummy

class CourseTorrentTestModule : KotlinModule()
{
    override fun configure()
    {
        bind<SecureStorageFactory>().to<SecureStorageFactoryDummy>()
        bind<NetHandler>().to<HttpMock>()
    }
}