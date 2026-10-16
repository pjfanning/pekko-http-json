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

package com.github.pjfanning.pekkohttpjackson3

import tools.jackson.core.{ JsonParser, ObjectReadContext }
import tools.jackson.core.json.JsonFactory
import tools.jackson.core.async.ByteBufferFeeder
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.scala.{ ClassTagExtensions, JavaTypeable }
import com.typesafe.config.Config
import org.apache.pekko.http.javadsl.common.JsonEntityStreamingSupport
import org.apache.pekko.http.scaladsl.common.EntityStreamingSupport
import org.apache.pekko.http.scaladsl.marshalling._
import org.apache.pekko.http.scaladsl.model.{
  ContentType,
  ContentTypeRange,
  HttpCharsets,
  HttpEntity,
  MediaType,
  MessageEntity
}
import org.apache.pekko.http.scaladsl.unmarshalling.{ FromEntityUnmarshaller, Unmarshaller }
import org.apache.pekko.http.scaladsl.util.FastFuture
import org.apache.pekko.stream.scaladsl.{ Flow, Source }
import org.apache.pekko.util.ByteString

import java.util.concurrent.ConcurrentHashMap
import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala.util.control.NonFatal

/**
  * Automatic to and from JSON marshalling/unmarshalling using an in-scope Jackson's ObjectMapper
  */
object JacksonSupport extends JacksonSupport {

  private[pekkohttpjackson3] val jacksonConfig = JacksonConfigSupport.jacksonConfig

  private[pekkohttpjackson3] def createJsonFactory(config: Config): JsonFactory =
    JacksonConfigSupport.createJsonFactory(config)

  private val objectMappers =
    new ConcurrentHashMap[JacksonDataFormat, ObjectMapper with ClassTagExtensions]

  /**
    * The mapper used for `dataFormat` when none is passed in explicitly. Built from
    * `pekko-http-json.jackson` on first use, and then reused.
    */
  def objectMapperFor(dataFormat: JacksonDataFormat): ObjectMapper with ClassTagExtensions =
    objectMappers.computeIfAbsent(
      dataFormat,
      (format: JacksonDataFormat) => createObjectMapper(jacksonConfig, format)
    )

  override val defaultObjectMapper: ObjectMapper with ClassTagExtensions =
    objectMapperFor(JacksonDataFormat.default)

  private[pekkohttpjackson3] def createObjectMapper(
      config: Config
  ): ObjectMapper with ClassTagExtensions =
    createObjectMapper(config, JacksonDataFormat(config.getString("format")))

  private[pekkohttpjackson3] def createObjectMapper(
      config: Config,
      dataFormat: JacksonDataFormat
  ): ObjectMapper with ClassTagExtensions =
    dataFormat.createObjectMapper(config)
}

/**
  * JSON marshalling/unmarshalling using an in-scope Jackson's ObjectMapper.
  *
  * The data format is [[JacksonDataFormat.Json]] unless `pekko-http-json.jackson.format` says
  * otherwise; override [[dataFormat]] to pin a format regardless of the config, as [[JsonSupport]]
  * and [[CborSupport]] do. The `Source` based streaming marshallers frame their output as a JSON
  * array, so they are only available for [[JacksonDataFormat.Json]].
  */
trait JacksonSupport {
  type SourceOf[A] = Source[A, _]

  /** The data format to marshal to and unmarshal from. */
  def dataFormat: JacksonDataFormat = JacksonDataFormat.default

  def unmarshallerContentTypes: Seq[ContentTypeRange] =
    mediaTypes.map(ContentTypeRange.apply)

  def mediaTypes: Seq[MediaType] = List(dataFormat.mediaType)

  private def contentTypeOf(mediaType: MediaType): ContentType =
    ContentType(mediaType, () => HttpCharsets.`UTF-8`)

  /** The mapper used when there is no `ObjectMapper` in implicit scope. */
  def defaultObjectMapper: ObjectMapper with ClassTagExtensions =
    JacksonSupport.objectMapperFor(dataFormat)

  private def requireJsonFormat(operation: String): Unit =
    if (dataFormat != JacksonDataFormat.Json)
      throw new UnsupportedOperationException(
        s"$operation frames its output as a JSON array and is only supported for the json data " +
        s"format, but the data format is ${dataFormat.name}"
      )

  private val jsonStringUnmarshaller =
    Unmarshaller.byteStringUnmarshaller
      .forContentTypes(unmarshallerContentTypes: _*)
      .mapWithCharset {
        case (ByteString.empty, _) => throw Unmarshaller.NoContentException
        case (data, charset)       => data.decodeString(charset.nioCharset)
      }

  // a def rather than a val: a val would add an abstract accessor to the trait, breaking anything
  // that already implements it
  private def binaryUnmarshaller =
    Unmarshaller.byteStringUnmarshaller
      .forContentTypes(unmarshallerContentTypes: _*)
      .map {
        case ByteString.empty => throw Unmarshaller.NoContentException
        case data             => data.toArrayUnsafe()
      }

  private def sourceByteStringMarshaller(
      mediaType: MediaType
  ): Marshaller[SourceOf[ByteString], MessageEntity] = {
    val contentType = contentTypeOf(mediaType)
    Marshaller[SourceOf[ByteString], MessageEntity] { implicit ec => value =>
      try
        FastFuture.successful {
          Marshalling.WithFixedContentType(
            contentType,
            () => HttpEntity(contentType = contentType, data = value)
          ) :: Nil
        }
      catch {
        case NonFatal(e) => FastFuture.failed(e)
      }
    }
  }

  private val jsonSourceStringMarshaller =
    Marshaller.oneOf(mediaTypes: _*)(sourceByteStringMarshaller)

  private def jsonSource[A](entitySource: SourceOf[A])(implicit
      objectMapper: ObjectMapper = defaultObjectMapper,
      support: JsonEntityStreamingSupport
  ): SourceOf[ByteString] =
    entitySource
      // writeValueAsBytes returns a fresh array, so it can be wrapped without copying
      .map(a => ByteString.fromArrayUnsafe(objectMapper.writeValueAsBytes(a)))
      .via(support.framingRenderer)

  /**
    * HTTP entity => `A`
    */
  implicit def unmarshaller[A: JavaTypeable](implicit
      objectMapper: ObjectMapper with ClassTagExtensions = defaultObjectMapper
  ): FromEntityUnmarshaller[A] =
    dataFormat match {
      case JacksonDataFormat.Json =>
        jsonStringUnmarshaller.map(data => objectMapper.readValue[A](data))
      case _ =>
        binaryUnmarshaller.map(data => objectMapper.readValue[A](data))
    }

  /**
    * `A` => HTTP entity
    */
  implicit def marshaller[Object](implicit
      objectMapper: ObjectMapper = defaultObjectMapper
  ): ToEntityMarshaller[Object] =
    dataFormat match {
      case JacksonDataFormat.Json => Jackson.marshaller[Object](objectMapper)
      case _                      =>
        Marshaller
          .oneOf(mediaTypes: _*)(mediaType =>
            Marshaller.byteArrayMarshaller(contentTypeOf(mediaType))
          )
          .compose[Object](objectMapper.writeValueAsBytes)
    }

  /**
    * `ByteString` => `A`
    *
    * @tparam A
    *   type to decode
    * @return
    *   unmarshaller for any `A` value
    */
  implicit def fromByteStringUnmarshaller[A: JavaTypeable](implicit
      objectMapper: ObjectMapper with ClassTagExtensions = defaultObjectMapper
  ): Unmarshaller[ByteString, A] =
    dataFormat match {
      case JacksonDataFormat.Json =>
        Unmarshaller { ec => bs =>
          Future {
            val parser = objectMapper
              .tokenStreamFactory()
              .createNonBlockingByteBufferParser(ObjectReadContext.empty())
              .asInstanceOf[JsonParser with ByteBufferFeeder]
            try {
              bs match {
                case bs: ByteString.ByteStrings =>
                  bs.asByteBuffers.foreach(parser.feedInput)
                case bytes =>
                  parser.feedInput(bytes.asByteBuffer)
              }
              objectMapper.readValue[A](parser)
            } finally
              parser.close()
          }(ec)
        }
      case _ =>
        Unmarshaller { ec => bs =>
          Future(objectMapper.readValue[A](bs.toArrayUnsafe()))(ec)
        }
    }

  /**
    * HTTP entity => `Source[A, _]`
    *
    * Only supported for [[JacksonDataFormat.Json]].
    *
    * @tparam A
    *   type to decode
    * @return
    *   unmarshaller for `Source[A, _]`
    */
  implicit def sourceUnmarshaller[A: JavaTypeable](implicit
      support: JsonEntityStreamingSupport = EntityStreamingSupport.json()
  ): FromEntityUnmarshaller[SourceOf[A]] = {
    requireJsonFormat("Source unmarshalling")
    Unmarshaller
      .withMaterializer[HttpEntity, SourceOf[A]] { implicit ec => implicit mat => entity =>
        // resolved once per unmarshalling operation rather than once per stream element
        val elementUnmarshaller = implicitly[Unmarshaller[ByteString, A]]

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
  }

  /**
    * `SourceOf[A]` => HTTP entity
    *
    * Only supported for [[JacksonDataFormat.Json]].
    *
    * @tparam A
    *   type to encode
    * @return
    *   marshaller for any `SourceOf[A]` value
    */
  implicit def sourceMarshaller[A](implicit
      objectMapper: ObjectMapper = defaultObjectMapper,
      support: JsonEntityStreamingSupport = EntityStreamingSupport.json()
  ): ToEntityMarshaller[SourceOf[A]] = {
    requireJsonFormat("Source marshalling")
    jsonSourceStringMarshaller.compose(jsonSource[A])
  }
}
