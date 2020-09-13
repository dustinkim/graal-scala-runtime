import cats.effect.IO
import cats.implicits.toTraverseOps
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import io.circe.{Decoder, Encoder}

final private case class ParseError(
    message: String = "could not parse input into required shape")

final private case class Wrapper(body: String)

abstract class LambdaFunction[Input: Decoder, Err: Encoder, Output: Encoder] {
  def run(a: Input): IO[Either[Err, Output]]

  final def main(args: Array[String]): Unit = {
    val arg = args.headOption
    val unwrappedBody: Option[Input] =
      arg.flatMap((decode[Input] _).andThen(_.toOption))
    lazy val wrappedBody: Option[Input] =
      arg.flatMap((decode[Wrapper] _).andThen(_.toOption))
          .flatMap(r => decode[Input](r.body).toOption)
    val req = unwrappedBody.orElse(wrappedBody)

    req
      .traverse(run)
      .map {
        case None =>
          Console.print(ParseError().asJson)
          System.exit(1)
        case Some(Left(value)) =>
          Console.print(value.asJson)
          System.exit(1)
        case Some(Right(value)) =>
          Console.print(value.asJson)
          System.exit(0)
      }
      .unsafeRunSync()
  }
}
