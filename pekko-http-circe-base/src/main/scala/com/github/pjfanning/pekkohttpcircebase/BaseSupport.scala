/*
 * Copyright 2015 Heiko Seeberger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.pjfanning.pekkohttpcircebase

import org.apache.pekko.http.scaladsl.common.{ EntityStreamingSupport, JsonEntityStreamingSupport }
import org.apache.pekko.http.scaladsl.marshalling.ToEntityMarshaller
import org.apache.pekko.http.scaladsl.marshalling.{ Marshaller, Marshalling }
import org.apache.pekko.http.scaladsl.model.MediaTypes.`application/json`
import org.apache.pekko.http.scaladsl.model.{
  ContentType,
  ContentTypeRange,
  HttpEntity,
  MediaType,
  MessageEntity
}
import org.apache.pekko.http.scaladsl.unmarshalling.{
  FromEntityUnmarshaller,
  Unmarshal,
  Unmarshaller
}
import org.apache.pekko.http.scaladsl.util.FastFuture
import org.apache.pekko.stream.scaladsl.{ Flow, Source }
import org.apache.pekko.util.ByteString
import cats.data.{ NonEmptyList, ValidatedNel }
import cats.syntax.either._
import cats.syntax.show._
import io.circe.{ Json, ParsingFailure }
import io.circe.{ Decoder, DecodingFailure, Encoder, Printer }

import scala.concurrent.Future
import scala.util.control.NonFatal

trait BaseSupport {

  /**
    * HTTP entity => `Json`
    *
    * @return
    *   unmarshaller for `Json`
    */
  implicit def jsonUnmarshaller: FromEntityUnmarshaller[Json]

  /**
    * HTTP entity => `Either[io.circe.ParsingFailure, Json]`
    *
    * @return
    *   unmarshaller for `Either[io.circe.ParsingFailure, Json]`
    */
  implicit def safeJsonUnmarshaller: FromEntityUnmarshaller[Either[ParsingFailure, Json]]

  def byteStringJsonUnmarshaller: Unmarshaller[ByteString, Json]

  type SourceOf[A] = Source[A, _]

  def unmarshallerContentTypes: Seq[ContentTypeRange] =
    mediaTypes.map(ContentTypeRange.apply)

  protected val defaultMediaTypes: Seq[MediaType.WithFixedCharset] = List(`application/json`)
  def mediaTypes: Seq[MediaType.WithFixedCharset]                  = defaultMediaTypes

  protected def sourceByteStringMarshaller(
      mediaType: MediaType.WithFixedCharset
  ): Marshaller[SourceOf[ByteString], MessageEntity] =
    Marshaller[SourceOf[ByteString], MessageEntity] { implicit ec => value =>
      try
        FastFuture.successful {
          Marshalling.WithFixedContentType(
            mediaType,
            () => HttpEntity(contentType = mediaType, data = value)
          ) :: Nil
        }
      catch {
        case NonFatal(e) => FastFuture.failed(e)
      }
    }

  protected val jsonSourceStringMarshaller =
    Marshaller.oneOf(mediaTypes: _*)(sourceByteStringMarshaller)

  protected def jsonSource[A](entitySource: SourceOf[A])(implicit
      encoder: Encoder[A],
      printer: Printer,
      support: JsonEntityStreamingSupport
  ): SourceOf[ByteString] =
    entitySource
      .map(encoder.apply)
      .map(printer.printToByteBuffer)
      .map(ByteString(_))
      .via(support.framingRenderer)

  /**
    * `ByteString` => `A`
    *
    * @tparam A
    *   type to decode
    * @return
    *   unmarshaller for any `A` value
    */
  implicit def fromByteStringUnmarshaller[A: Decoder]: Unmarshaller[ByteString, A]

  /**
    * `Json` => HTTP entity
    *
    * @return
    *   marshaller for JSON value
    */
  implicit final def jsonMarshaller(implicit
      printer: Printer = Printer.noSpaces
  ): ToEntityMarshaller[Json] =
    Marshaller.oneOf(mediaTypes: _*) { mediaType =>
      Marshaller.withFixedContentType(ContentType(mediaType)) { json =>
        HttpEntity(
          mediaType,
          ByteString(printer.printToByteBuffer(json, mediaType.charset.nioCharset()))
        )
      }
    }

  /**
    * `A` => HTTP entity
    *
    * @tparam A
    *   type to encode
    * @return
    *   marshaller for any `A` value
    */
  implicit final def marshaller[A: Encoder](implicit
      printer: Printer = Printer.noSpaces
  ): ToEntityMarshaller[A] =
    jsonMarshaller(printer).compose(Encoder[A].apply)

  /**
    * HTTP entity => `A`
    *
    * @tparam A
    *   type to decode
    * @return
    *   unmarshaller for `A`
    */
  implicit def unmarshaller[A: Decoder]: FromEntityUnmarshaller[A]

  /**
    * HTTP entity => `Source[A, _]`
    *
    * @tparam A
    *   type to decode
    * @return
    *   unmarshaller for `Source[A, _]`
    */
  implicit def sourceUnmarshaller[A: Decoder](implicit
      support: JsonEntityStreamingSupport = EntityStreamingSupport.json()
  ): FromEntityUnmarshaller[SourceOf[A]] =
    Unmarshaller
      .withMaterializer[HttpEntity, SourceOf[A]] { implicit ec => implicit mat => entity =>
        def asyncParse(bs: ByteString) =
          Unmarshal(bs).to[A]

        def ordered =
          Flow[ByteString].mapAsync(support.parallelism)(asyncParse)

        def unordered =
          Flow[ByteString].mapAsyncUnordered(support.parallelism)(asyncParse)

        Future.successful {
          entity.dataBytes
            .via(support.framingDecoder)
            .via(if (support.unordered) unordered else ordered)
        }
      }
      .forContentTypes(unmarshallerContentTypes: _*)

  /**
    * `SourceOf[A]` => HTTP entity
    *
    * @tparam A
    *   type to encode
    * @return
    *   marshaller for any `SourceOf[A]` value
    */
  implicit def sourceMarshaller[A](implicit
      writes: Encoder[A],
      printer: Printer = Printer.noSpaces,
      support: JsonEntityStreamingSupport = EntityStreamingSupport.json()
  ): ToEntityMarshaller[SourceOf[A]] =
    jsonSourceStringMarshaller.compose(jsonSource[A])
}

trait FailFastSupport extends BaseSupport {
  override implicit final def fromByteStringUnmarshaller[A: Decoder]: Unmarshaller[ByteString, A] =
    byteStringJsonUnmarshaller
      .map(Decoder[A].decodeJson)
      .map(_.fold(throw _, identity))

  override implicit final def unmarshaller[A: Decoder]: FromEntityUnmarshaller[A] =
    jsonUnmarshaller
      .map(Decoder[A].decodeJson)
      .map(_.fold(throw _, identity))

  implicit final def safeUnmarshaller[A: Decoder]
      : FromEntityUnmarshaller[Either[io.circe.Error, A]] =
    safeJsonUnmarshaller.map(_.flatMap(Decoder[A].decodeJson))
}

trait ErrorAccumulatingSupport extends BaseSupport {
  private def decode[A: Decoder](json: Json) =
    Decoder[A].decodeAccumulating(json.hcursor)

  override implicit final def fromByteStringUnmarshaller[A: Decoder]: Unmarshaller[ByteString, A] =
    byteStringJsonUnmarshaller
      .map(decode[A])
      .map(
        _.fold(failures => throw ErrorAccumulatingSupport.DecodingFailures(failures), identity)
      )

  override implicit final def unmarshaller[A: Decoder]: FromEntityUnmarshaller[A] =
    jsonUnmarshaller
      .map(decode[A])
      .map(
        _.fold(failures => throw ErrorAccumulatingSupport.DecodingFailures(failures), identity)
      )

  implicit final def safeUnmarshaller[A: Decoder]
      : FromEntityUnmarshaller[ValidatedNel[io.circe.Error, A]] =
    safeJsonUnmarshaller.map(_.toValidatedNel andThen decode[A])
}

object ErrorAccumulatingSupport {
  final case class DecodingFailures(failures: NonEmptyList[DecodingFailure]) extends Exception {
    override def getMessage: String = failures.toList.map(_.show).mkString("\n")
  }
}
