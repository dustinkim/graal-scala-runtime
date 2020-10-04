import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import io.circe.{Decoder, Encoder}

import scala.sys.process._

final private case class ParseError(rawInput: String, jsonError: String)

final private case class Wrapper(body: String)

abstract class LambdaFunction[Input: Decoder, Err: Encoder, Output: Encoder] {
  def run(a: Input): IO[Either[Err, Output]]

  final def main(args: Array[String]): Unit = {
    val arg = args.headOption.getOrElse("")
    val unwrappedBody =
      decode[Input](arg)
    lazy val wrappedBody =
      decode[Wrapper](arg)
        .flatMap(r => decode[Input](r.body))
    val req: Either[io.circe.Error, Input] =
      unwrappedBody.orElse(wrappedBody).orElse(unwrappedBody)

    val RUNTIME_API_ADDRESS = sys.env.get("RUNTIME_API_ADDRESS")
    val INVOCATION_ID = sys.env.get("INVOCATION_ID")

    req
      .traverse(run)
      .map {
        case Left(err) =>
          (RUNTIME_API_ADDRESS, INVOCATION_ID) match {
            case (Some(r), Some(i)) =>
              Seq(
                s"curl",
                "-sS",
                "-X",
                "POST",
                s"$r/$i/error",
                "-d",
                ParseError(arg, err.getMessage).asJson.toString
                  .replace("\n", " ")).!
            case _ =>
              pprint.log(ParseError(arg, err.getMessage).asJson.toString)
          }
          System.exit(1)
        case Right(Left(value)) =>
          (RUNTIME_API_ADDRESS, INVOCATION_ID) match {
            case (Some(r), Some(i)) =>
              Seq(
                s"curl",
                "-sS",
                "-X",
                "POST",
                s"$r/$i/error",
                "-d",
                value.asJson.toString.replace("\n", " ")).!
            case _ => pprint.log(value.asJson.toString)
          }
          System.exit(1)
        case Right(Right(value)) =>
          (RUNTIME_API_ADDRESS, INVOCATION_ID) match {
            case (Some(r), Some(i)) =>
              Seq(
                s"curl",
                "-sS",
                "-X",
                "POST",
                s"$r/$i/response",
                "-d",
                value.asJson.toString.replace("\n", " ")).!
            case _ => pprint.log(value.asJson.toString)
          }
          System.exit(0)
      }
      .unsafeRunSync()
  }
}
