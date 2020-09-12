import io.circe.generic.auto._
import io.circe.parser._

import scala.collection.convert.AsScalaConverters

final case class Request(key1: String, key2: String, key3: String)

object HelloWorld extends App with AsScalaConverters {

  val req = args.headOption.flatMap((decode[Request] _).andThen(_.toOption))

  println(req)

  println("Hello, world, how y'all doing?")
}
 