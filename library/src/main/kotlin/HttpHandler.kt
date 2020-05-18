import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL


class HttpHandler : NetHandler
{

    override fun sendGetRequest(requestURL: String): StringBuffer
    {
        //val mURL = URL("https://postman-echo.com/get?foo1=bar1&foo2=bar2")
        val mURL = URL(requestURL)

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
                println("Response : $response")
                return response
            }
        }
    }


}