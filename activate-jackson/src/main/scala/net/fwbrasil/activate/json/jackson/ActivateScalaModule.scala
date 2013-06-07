package net.fwbrasil.activate.json.jackson

import com.fasterxml.jackson.module.scala.deser.UntypedObjectDeserializerModule
import com.fasterxml.jackson.module.scala.IterableModule
import com.fasterxml.jackson.module.scala.MapModule
import com.fasterxml.jackson.module.scala.TupleModule
import com.fasterxml.jackson.module.scala.SeqModule
import com.fasterxml.jackson.module.scala.JacksonModule
import com.fasterxml.jackson.module.scala.IteratorModule
import com.fasterxml.jackson.module.scala.introspect.ScalaClassIntrospectorModule
import com.fasterxml.jackson.module.scala.SetModule
import com.fasterxml.jackson.module.scala.EnumerationModule
import com.fasterxml.jackson.module.scala.OptionModule

sealed class ActivateScalaModule
  extends JacksonModule
     with IteratorModule
     with OptionModule
     with SeqModule
     with IterableModule
     with TupleModule
     with MapModule
     with SetModule
     with ScalaClassIntrospectorModule
     with UntypedObjectDeserializerModule
{
  override def getModuleName = "ActivateScalaModule"
}

object ActivateScalaModule extends ActivateScalaModule