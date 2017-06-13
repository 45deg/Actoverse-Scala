
import org.scalatest.FlatSpec
import actoverse._
import net.liftweb.json._
import net.liftweb.json.JsonDSL._

@Comprehensive
case class Hoge(a: Int, b: Int)

class SerializerSpec extends FlatSpec {
  it should "work with the custom serializer" in {
     val obj = Hoge(1,2)
     implicit val formats = DefaultFormats + new ComprehensiveSerializer
     assert(compact(render(Extraction.decompose(obj))) == "[\"Hoge\",{\"a\":1,\"b\":2}]")
     assert(compact(render(Extraction.decompose(("a" -> 2)))) == "[\"a\",2]")
  }
}
