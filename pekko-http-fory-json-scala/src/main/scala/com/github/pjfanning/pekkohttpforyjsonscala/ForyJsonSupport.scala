/*
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

package com.github.pjfanning.pekkohttpforyjsonscala

import org.apache.pekko.http.javadsl.common.JsonEntityStreamingSupport
import org.apache.pekko.http.scaladsl.common.EntityStreamingSupport
import org.apache.pekko.http.scaladsl.marshalling.{ Marshaller, Marshalling, ToEntityMarshaller }
import org.apache.pekko.http.scaladsl.model.{
  ContentType,
  ContentTypeRange,
  HttpEntity,
  MediaType,
  MessageEntity
}
import org.apache.pekko.http.scaladsl.model.MediaTypes.`application/json`
import org.apache.pekko.http.scaladsl.unmarshalling.{ FromEntityUnmarshaller, Unmarshaller }
import org.apache.pekko.http.scaladsl.util.FastFuture
import org.apache.pekko.stream.scaladsl.{ Flow, Source }
import org.apache.pekko.util.ByteString
import org.apache.fory.json.ForyJson
import org.apache.fory.json.scala.ForyJsonScala
import org.apache.fory.reflect.TypeRef

import scala.concurrent.Future
import scala.util.control.NonFatal

/**
  * Automatic to and from JSON marshalling/unmarshalling using an in-scope Fory `TypeRef`.
  *
  * A `TypeRef` is obtained with `ScalaTypeRef`, which keeps Scala type arguments that JVM erasure
  * would otherwise lose:
  *
  * {{{
  * implicit val fooTypeRef: TypeRef[Foo] = ScalaTypeRef[Foo]
  * }}}
  *
  * Note that Fory does not populate the fields of a case class declared inside an `object`; such a
  * value decodes with all of its fields left null rather than failing. Declare the types you
  * exchange over HTTP at the top level of a file.
  */
object ForyJsonSupport extends ForyJsonSupport {

  /**
    * The `ForyJson` used when none is in implicit scope. It has the Scala module installed, so
    * Scala collections, options and enumerations are understood.
    */
  val defaultForyJson: ForyJson = ForyJsonScala.builder().build()
}

/**
  * JSON marshalling/unmarshalling using Fory's JSON support.
  */
trait ForyJsonSupport {
  type SourceOf[A] = Source[A, _]

  import ForyJsonSupport._

  private val defaultMediaTypes: Seq[MediaType.WithFixedCharset] = List(`application/json`)
  private val defaultContentTypes: Seq[ContentTypeRange]         =
    defaultMediaTypes.map(ContentTypeRange.apply)

  def unmarshallerContentTypes: Seq[ContentTypeRange] = defaultContentTypes

  def mediaTypes: Seq[MediaType.WithFixedCharset] = defaultMediaTypes

  private val byteArrayUnmarshaller: FromEntityUnmarshaller[Array[Byte]] =
    Unmarshaller.byteArrayUnmarshaller.forContentTypes(unmarshallerContentTypes: _*)

  private def sourceByteStringMarshaller(
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

  private val jsonSourceStringMarshaller =
    Marshaller.oneOf(mediaTypes: _*)(sourceByteStringMarshaller)

  private def jsonSource[A](entitySource: SourceOf[A])(implicit
      typeRef: TypeRef[A],
      foryJson: ForyJson,
      support: JsonEntityStreamingSupport
  ): SourceOf[ByteString] =
    entitySource
      .map(a => ByteString(foryJson.toJson(a, typeRef)))
      .via(support.framingRenderer)

  /**
    * HTTP entity => `A`
    *
    * @tparam A
    *   type to decode
    * @return
    *   unmarshaller for `A`
    */
  implicit def unmarshaller[A](implicit
      typeRef: TypeRef[A],
      foryJson: ForyJson = defaultForyJson
  ): FromEntityUnmarshaller[A] =
    byteArrayUnmarshaller.map { bytes =>
      if (bytes.length == 0) throw Unmarshaller.NoContentException
      foryJson.fromJson(bytes, typeRef)
    }

  /**
    * `A` => HTTP entity
    *
    * @tparam A
    *   type to encode
    * @return
    *   marshaller for any `A` value
    */
  implicit def marshaller[A](implicit
      typeRef: TypeRef[A],
      foryJson: ForyJson = defaultForyJson
  ): ToEntityMarshaller[A] = {
    val mediaType   = mediaTypes.head
    val contentType = ContentType.WithFixedCharset(mediaType)
    Marshaller.withFixedContentType(contentType) { obj =>
      HttpEntity.Strict(contentType, ByteString(foryJson.toJson(obj, typeRef)))
    }
  }

  /**
    * `ByteString` => `A`
    *
    * @tparam A
    *   type to decode
    * @return
    *   unmarshaller for any `A` value
    */
  implicit def fromByteStringUnmarshaller[A](implicit
      typeRef: TypeRef[A],
      foryJson: ForyJson = defaultForyJson
  ): Unmarshaller[ByteString, A] =
    Unmarshaller(ec => bs => Future(foryJson.fromJson(bs.asInputStream, typeRef))(ec))

  /**
    * HTTP entity => `Source[A, _]`
    *
    * @tparam A
    *   type to decode
    * @return
    *   unmarshaller for `Source[A, _]`
    */
  implicit def sourceUnmarshaller[A](implicit
      typeRef: TypeRef[A],
      foryJson: ForyJson = defaultForyJson,
      support: JsonEntityStreamingSupport = EntityStreamingSupport.json()
  ): FromEntityUnmarshaller[SourceOf[A]] =
    Unmarshaller
      .withMaterializer[HttpEntity, SourceOf[A]] { implicit ec => implicit mat => entity =>
        // resolved once per unmarshalling operation rather than once per stream element
        val elementUnmarshaller = fromByteStringUnmarshaller[A]

        def asyncParse(bs: ByteString) =
          elementUnmarshaller(bs)

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
      typeRef: TypeRef[A],
      foryJson: ForyJson = defaultForyJson,
      support: JsonEntityStreamingSupport = EntityStreamingSupport.json()
  ): ToEntityMarshaller[SourceOf[A]] =
    jsonSourceStringMarshaller.compose(jsonSource[A])
}
