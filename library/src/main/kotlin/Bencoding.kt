import java.lang.IllegalArgumentException
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


class Bencoding
{
    companion object {

        fun decodeValue(str:ByteArray):Any? =
            when (str[0].toChar()) {
                'i' -> decodeInt(str)
                'l' -> decodeList(str)
                'd' -> decodeDictionary(str)
                in ('0'..'9') -> decodeString(str)
                else -> throw IllegalArgumentException()
            }

        fun decodeFlatDictionary (str:ByteArray):HashMap<String, ByteArray>
        {
            if(str[0].toChar()!='d' || str[str.size-1].toChar()!='e')
            {
                throw IllegalArgumentException()
            }

            val torrentMap = HashMap<String, ByteArray>()

            var curIndex = 1
            while(curIndex < str.size-1){

                val keyRange : IntRange= extractRangeOfFirstElement(str, curIndex)
                val key : String = decodeString(str.slice(keyRange).toByteArray()) // returns key string

                curIndex = keyRange.endInclusive + 1
                if (curIndex >= str.size)
                {
                    throw IllegalArgumentException()
                }

                val valueRange : IntRange = extractRangeOfFirstElement(str, curIndex)
                val value : ByteArray = str.slice(valueRange).toByteArray()
                curIndex = valueRange.endInclusive + 1

                if(torrentMap.containsKey(key))
                {
                    throw IllegalArgumentException()
                }
                torrentMap[key] = value
            }
            return torrentMap
        }

        private fun decodeInt (str:ByteArray) : Long{
            if(str[0].toChar()!='i' || str[str.size-1].toChar()!='e'){
                throw IllegalArgumentException()
            }

            var currentIndex = 1
            if(str[currentIndex].toChar()=='-') currentIndex++

            while (str[currentIndex].toChar()!='e'){
                if( !(str[currentIndex].toChar() in ('0'..'9')) || currentIndex == str.size-1){
                    throw IllegalArgumentException()
                }
                currentIndex++
            }

            val numStr = str.toString(charset = Charsets.UTF_8).substring(1, str.size-1)
            return numStr.toLong()
        }

        private fun decodeList (str:ByteArray) : List<Any?>{
            if(str[0].toChar()!='l' || str[str.size-1].toChar()!='e'){
                throw IllegalArgumentException()
            }

            val torrentList = ArrayList<Any?>()

            var curIndex = 1
            while(curIndex < str.size-1){

                val valueRange : IntRange = extractRangeOfFirstElement(str, curIndex)
                val value : Any? = decodeValue(str.slice(valueRange).toByteArray())
                curIndex = valueRange.endInclusive + 1

                torrentList.add(value)
            }
            return torrentList
        }

        private fun decodeDictionary (str:ByteArray):HashMap<String, Any?>{
            if(str[0].toChar()!='d' || str[str.size-1].toChar()!='e'){
                throw IllegalArgumentException()
            }

            val torrentMap = HashMap<String, Any?>()

            var curIndex = 1
            while(curIndex < str.size-1){

                val keyRange : IntRange= extractRangeOfFirstElement(str, curIndex)
                val key : String = decodeString(str.slice(keyRange).toByteArray()) // returns key string

                curIndex = keyRange.endInclusive + 1
                if (curIndex >= str.size){
                    throw IllegalArgumentException()
                }

                val valueRange : IntRange = extractRangeOfFirstElement(str, curIndex)
                val value : Any? = decodeValue(str.slice(valueRange).toByteArray())
                curIndex = valueRange.endInclusive + 1

                if(torrentMap.containsKey(key)){
                    throw IllegalArgumentException()
                }
                torrentMap[key] = value
            }
            return torrentMap
        }

        private fun decodeString (str:ByteArray): String{
            if( !(str[0].toChar() in ('0'..'9'))){
                throw IllegalArgumentException()
            }
            val indexOfColon = str.toString(charset = Charsets.UTF_8).indexOf(':')
            val length =  str.toString(charset = Charsets.UTF_8).substring(0,indexOfColon).toLong()

            // str should be in the form "<length>:<string>", so it's length should be the length of <string>
            // which is length ,plus the length of ":" which is 1, plus the length of "length" which is
            // length.toString().length
            if((length.toString().length + 1 + length) != str.size.toLong()) throw IllegalArgumentException()
            return str.toString(charset = Charsets.UTF_8).substring(indexOfColon+1)
        }

        private fun extractRangeOfFirstElement (str:ByteArray, startIndex:Int) =
            when(str[startIndex].toChar()){
                'i' -> extractIntRange(str, startIndex)
                'l' -> extractEvenBracketsRange(str, startIndex)
                'd' -> extractEvenBracketsRange(str, startIndex)
                in ('0'..'9') -> extractStringRange(str, startIndex)
                else -> throw IllegalArgumentException()
            }

        private fun extractIntRange(str: ByteArray, startIndex:Int):IntRange{

            if (str[startIndex].toChar()!='i'){
                throw IllegalArgumentException()
            }

            var currentIndex = startIndex + 1

            if(str[currentIndex].toChar()=='-') currentIndex++

            while (str[currentIndex].toChar()!='e'){
                if( !(str[currentIndex].toChar() in ('0'..'9')) || currentIndex == str.size-1){
                    throw IllegalArgumentException()
                }
                currentIndex++
            }
            return IntRange(startIndex, currentIndex)

        }

        private fun extractEvenBracketsRange (str: ByteArray, startIndex: Int):IntRange{
            var cntBrackets = 1
            var current = startIndex + 1
            while (cntBrackets != 0 && current < str.size){
                val ch = str[current].toChar()
                when (ch){
                    'i' -> { val intRange = extractIntRange(str, current)
                            current = intRange.endInclusive }
                    'l' -> cntBrackets++
                    'd' -> cntBrackets++
                    'e' -> cntBrackets--
                    in ('0'..'9') -> { val stringRange = extractStringRange(str, current)
                        current = stringRange.endInclusive}
                }
                current++
            }
            return IntRange(startIndex, current-1)
        }

        private fun extractStringRange(str: ByteArray, startIndex: Int):IntRange{

            if (!(str[startIndex].toChar() in ('0'..'9'))){
                throw IllegalArgumentException()
            }

            var currentIndex = startIndex

            while (str[currentIndex].toChar()!=':'){
                if( !(str[currentIndex].toChar() in ('0'..'9')) || currentIndex == str.size-1){
                    throw IllegalArgumentException()
                }
                currentIndex++
            }

            val strLength = getNumberBeforeColon(str, startIndex, currentIndex-1)
            return IntRange(startIndex, currentIndex + strLength )
            
        }
        
        private fun getNumberBeforeColon(str: ByteArray, startIndex: Int, endIndex:Int):Int{
            // 48 is ascii coding of 0.
            val strLength = str.slice(IntRange(startIndex, endIndex)).map{it.toInt()-48}.joinToString(separator = "", prefix = "", postfix = "").toInt()
            return strLength
        }
    }
}


