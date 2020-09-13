import cats.effect.IO
import cats.implicits.catsSyntaxEitherId
import io.circe.generic.auto._

final case class Request(key1: String, key2: String, key3: String)
final case class Response(key1: String)

object HelloWorld extends LambdaFunction[Request, Unit, Response] {

  override def run(a: Request): IO[Either[Unit, Response]] =
    IO(Response("Hello").asRight)
}
