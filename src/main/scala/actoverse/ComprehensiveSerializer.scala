package actoverse

import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import scala.reflect.runtime.{universe => ru}

class ComprehensiveSerializer extends Serializer[Any] {
   private var lock: Boolean = false
   private def isComprehensiveClass(obj: Any) = {
      val cls: Class[_] = obj.getClass
      val m = ru.runtimeMirror(cls.getClassLoader) // RuntimeMirror
      val annotations = m.classSymbol(cls).annotations
      annotations.exists(_.tree.tpe =:= ru.typeOf[Comprehensive])
   }
   private def isTuple(cls: Class[_]) = {
     cls.getSimpleName.startsWith("Tuple")
   }

   def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), Any] = Map()

   def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
     case x if isComprehensiveClass(x) && !lock =>
       lock = true
       val obj = JArray(List(JString(x.getClass.getSimpleName), Extraction.decompose(x)))
       lock = false
       obj
     case x: Product if isTuple(x.getClass) =>
       JArray(x.productIterator.toList map Extraction.decompose)
   }
}
