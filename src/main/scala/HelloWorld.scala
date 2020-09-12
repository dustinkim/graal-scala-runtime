import cats.effect.IO
import io.circe.generic.auto._

final case class Request(key1: String, key2: String, key3: String)
final case class Response(key1: String)

object HelloWorld extends LambdaFunction[Request, Response] {
  override def run(a: Request): IO[Response] = IO(Response("Hello"))
}
