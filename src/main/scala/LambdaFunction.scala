import cats.effect.IO
import cats.implicits.toTraverseOps
import io.circe.parser.decode
import io.circe.{Decoder, Encoder}
import io.circe.syntax._


abstract class LambdaFunction[A: Decoder, B: Encoder] {
  def run(a: A): IO[B]

  final def main(args: Array[String]): Unit = {
    val req = args.headOption.flatMap((decode[A] _).andThen(_.toOption))
    req
      .traverse(run)
      .map(_.asJson)
      .map(Console.print)
      .unsafeRunSync()
  }
}
