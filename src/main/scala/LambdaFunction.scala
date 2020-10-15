import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import org.http4s
import org.http4s.client.blaze._
import org.http4s.client._
import org.http4s._
import org.http4s.implicits.http4sLiteralsSyntax
import org.http4s.circe.{jsonEncoderOf, jsonOf}

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
    import scala.concurrent.ExecutionContext.global
    implicit val timer: Timer[IO] = IO.timer(global)
    implicit val encoderParseError: EntityEncoder[IO, ParseError] = jsonEncoderOf[IO, ParseError]
    implicit val encoderErr: EntityEncoder[IO, Err] = jsonEncoderOf[IO, Err]
    implicit val encoderOutput: EntityEncoder[IO, Output] = jsonEncoderOf[IO, Output]
    implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)

    req
      .traverse(run)
      .flatMap {
        case Left(err) =>
          (RUNTIME_API_ADDRESS, INVOCATION_ID) match {
            case (Some(r), Some(i)) =>
              BlazeClientBuilder[IO](global).resource.use { client: Client[IO] =>
                val entityBody = encoderParseError.toEntity(ParseError(arg, err.getMessage)).body
                val req: http4s.Request[IO] = org.http4s.Request.apply(method = Method.POST, uri = Uri.fromString(s"${r}/${i}/error").getOrElse(uri""), body = entityBody)
                client.run(req = req)
                  .use{_ =>
                    pprint.log("banana")
                    IO(())}
              }
            case _ =>
              IO(pprint.log(ParseError(arg, err.getMessage).asJson.toString))
          }

        case Right(Left(value)) =>
          (RUNTIME_API_ADDRESS, INVOCATION_ID) match {
            case (Some(r), Some(i)) =>
              BlazeClientBuilder[IO](global).resource.use { client: Client[IO] =>
                val errBody = encoderErr.toEntity(value).body
                val req: http4s.Request[IO] = org.http4s.Request.apply(method = Method.POST, uri = Uri.fromString(s"${r}/${i}/error").getOrElse(uri""), body = errBody)
                client.run(req = req)
                  .use{_ =>
                    pprint.log("banana")
                    IO(())}
              }
            case _ => IO(pprint.log(value.asJson.toString))
          }
        case Right(Right(value)) =>
          (RUNTIME_API_ADDRESS, INVOCATION_ID) match {
            case (Some(r), Some(i)) =>
              BlazeClientBuilder[IO](global).resource.use { client: Client[IO] =>
                val outputBody = encoderOutput.toEntity(value).body
                val req: http4s.Request[IO] = org.http4s.Request.apply(method = Method.POST, uri = Uri.fromString(s"${r}/${i}/response").getOrElse(uri""), body = outputBody)
                client.run(req = req)
                  .use{_ =>
                    pprint.log("banana")
                    IO(())}
              }
            case _ => IO(pprint.log(value.asJson.toString))
          }
      }.unsafeRunSync()
    ()
  }
}
