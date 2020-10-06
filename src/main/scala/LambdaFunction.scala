import cats.effect.{ContextShift, IO}
import cats.implicits._
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import sttp.client._
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.fs2.AsyncHttpClientFs2Backend


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
    implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)
    def opDo(implicit backend: SttpBackend[IO, fs2.Stream[IO, Byte], WebSocketHandler]) = req
      .traverse(run)
      .flatMap {
        case Left(err) =>
          (RUNTIME_API_ADDRESS, INVOCATION_ID) match {
            case (Some(r), Some(i)) =>
              IO(basicRequest.body(ParseError(arg, err.getMessage).asJson.toString)
                .post(uri"${r}/${i}/error").send()).map(_ => ())
            case _ =>
              IO(pprint.log(ParseError(arg, err.getMessage).asJson.toString))
          }
        case Right(Left(value)) =>
          (RUNTIME_API_ADDRESS, INVOCATION_ID) match {
            case (Some(r), Some(i)) =>
              IO(basicRequest.body(value.asJson.toString)
                .post(uri"${r}/${i}/error").send()).map(_ => ())
            case _ => IO(pprint.log(value.asJson.toString))
          }
        case Right(Right(value)) =>
          (RUNTIME_API_ADDRESS, INVOCATION_ID) match {
            case (Some(r), Some(i)) =>
              IO(basicRequest.body(value.asJson.toString)
                .post(uri"${r}/${i}/response").send()).map(_ => ())
            case _ => IO(pprint.log(value.asJson.toString))
          }
      }
    AsyncHttpClientFs2Backend[IO]().flatMap { implicit backend => opDo }.unsafeRunSync()
    ()
  }
}
