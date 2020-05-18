




class HttpMock  : NetHandler
{

    companion object{
        private lateinit var response : StringBuffer

        fun setResponse(res : StringBuffer)
        {
            response = res;
        }
    }


    override fun sendGetRequest(requestURL: String): StringBuffer
    {
        return response
    }


}