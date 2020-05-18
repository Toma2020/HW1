package il.ac.technion.cs.softwaredesign

import HttpHandler
import NetHandler
import dev.misfitlabs.kotlinguice4.KotlinModule
import il.ac.technion.cs.softwaredesign.storage.SecureStorageModule

class CourseTorrentModule : KotlinModule()
{
    override fun configure()
    {
        install(SecureStorageModule())
        bind<NetHandler>().to<HttpHandler>()
    }
}