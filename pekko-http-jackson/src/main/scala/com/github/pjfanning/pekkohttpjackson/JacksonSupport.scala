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

package com.github.pjfanning.pekkohttpjackson

import com.fasterxml.jackson.core.util.{ BufferRecycler, JsonRecyclerPools, RecyclerPool }
import com.fasterxml.jackson.core.{
  JsonFactory,
  JsonFactoryBuilder,
  StreamReadConstraints,
  StreamReadFeature,
  StreamWriteConstraints
}
import org.apache.pekko.http.javadsl.common.JsonEntityStreamingSupport
import org.apache.pekko.http.javadsl.marshallers.jackson.Jackson
import org.apache.pekko.http.scaladsl.common.EntityStreamingSupport
import org.apache.pekko.http.scaladsl.marshalling._
import org.apache.pekko.http.scaladsl.model.{
  ContentTypeRange,
  HttpEntity,
  MediaType,
  MessageEntity
}
import org.apache.pekko.http.scaladsl.model.MediaTypes.`application/json`
import org.apache.pekko.http.scaladsl.unmarshalling.{
  FromEntityUnmarshaller,
  Unmarshal,
  Unmarshaller
}
import org.apache.pekko.http.scaladsl.util.FastFuture
import org.apache.pekko.stream.scaladsl.{ Flow, Source }
import org.apache.pekko.util.ByteString
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.databind.{ Module, ObjectMapper }
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.scala.{ ClassTagExtensions, JavaTypeable }
import com.typesafe.config.{ Config, ConfigFactory }

import scala.concurrent.Future
import scala.util.Try
import scala.util.control.NonFatal

/**
  * Automatic to and from JSON marshalling/unmarshalling using an in-scope Jackson's ObjectMapper
  */
object JacksonSupport extends JacksonSupport {

  private[pekkohttpjackson] val jacksonConfig =
    ConfigFactory.load().getConfig("pekko-http-json.jackson")

  private[pekkohttpjackson] def createJsonFactory(config: Config): JsonFactory = {
    val streamReadConstraints = StreamReadConstraints
      .builder()
      .maxNestingDepth(config.getInt("read.max-nesting-depth"))
      .maxNumberLength(config.getInt("read.max-number-length"))
      .maxStringLength(config.getInt("read.max-string-length"))
      .maxNameLength(config.getInt("read.max-name-length"))
      .maxDocumentLength(config.getInt("read.max-document-length"))
      .build()
    val streamWriteConstraints = StreamWriteConstraints
      .builder()
      .maxNestingDepth(config.getInt("write.max-nesting-depth"))
      .build()
    val jsonFactoryBuilder: JsonFactoryBuilder = JsonFactory
      .builder()
      .asInstanceOf[JsonFactoryBuilder]
      .streamReadConstraints(streamReadConstraints)
      .streamWriteConstraints(streamWriteConstraints)
      .recyclerPool(getBufferRecyclerPool(config))
      .configure(
        StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION,
        config.getBoolean("read.feature.include-source-in-location")
      )
    jsonFactoryBuilder.build()
  }

  private def getBufferRecyclerPool(cfg: Config): RecyclerPool[BufferRecycler] =
    cfg.getString("buffer-recycler.pool-instance") match {
      case "thread-local"            => JsonRecyclerPools.threadLocalPool()
      case "lock-free"               => JsonRecyclerPools.newLockFreePool()
      case "shared-lock-free"        => JsonRecyclerPools.sharedLockFreePool()
      case "concurrent-deque"        => JsonRecyclerPools.newConcurrentDequePool()
      case "shared-concurrent-deque" => JsonRecyclerPools.sharedConcurrentDequePool()
      case "bounded" =>
        JsonRecyclerPools.newBoundedPool(cfg.getInt("buffer-recycler.bounded-pool-size"))
      case other => throw new IllegalArgumentException(s"Unknown recycler-pool: $other")
    }

  import org.apache.pekko.util.ccompat.JavaConverters._

  private val configuredModules = jacksonConfig.getStringList("jackson-modules").asScala.toSeq
  private val modules           = configuredModules.map(loadModule)

  val defaultObjectMapper: ObjectMapper with ClassTagExtensions = {
    val builder = JsonMapper.builder(createJsonFactory(jacksonConfig))
    modules.foreach(builder.addModule)
    builder.build() :: ClassTagExtensions
  }

  private def loadModule(fcqn: String): Module = {
    val inst = if (fcqn == "com.fasterxml.jackson.module.paramnames.ParameterNamesModule") {
      // matches the support for this module that appears in pekko-serialization-jackson
      Class
        .forName(fcqn)
        .getConstructor(classOf[JsonCreator.Mode])
        .newInstance(JsonCreator.Mode.PROPERTIES)
    } else {
      Try(Class.forName(fcqn).getConstructor().newInstance())
        .getOrElse(Class.forName(fcqn + "$").getConstructor().newInstance())
    }
    inst.asInstanceOf[Module]
  }
}

/**
  * JSON marshalling/unmarshalling using an in-scope Jackson's ObjectMapper
  */
trait JacksonSupport {
  type SourceOf[A] = Source[A, _]

  import JacksonSupport._

  def unmarshallerContentTypes: Seq[ContentTypeRange] =
    mediaTypes.map(ContentTypeRange.apply)

  def mediaTypes: Seq[MediaType.WithFixedCharset] =
    List(`application/json`)

  private val jsonStringUnmarshaller =
    Unmarshaller.byteStringUnmarshaller
      .forContentTypes(unmarshallerContentTypes: _*)
      .mapWithCharset {
        case (ByteString.empty, _) => throw Unmarshaller.NoContentException
        case (data, charset)       => data.decodeString(charset.nioCharset.name)
      }

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
      objectMapper: ObjectMapper = defaultObjectMapper,
      support: JsonEntityStreamingSupport
  ): SourceOf[ByteString] =
    entitySource
      .map(objectMapper.writeValueAsBytes)
      .map(ByteString(_))
      .via(support.framingRenderer)

  /**
    * HTTP entity => `A`
    */
  implicit def unmarshaller[A: JavaTypeable](implicit
      objectMapper: ObjectMapper with ClassTagExtensions = defaultObjectMapper
  ): FromEntityUnmarshaller[A] =
    jsonStringUnmarshaller.map(data => objectMapper.readValue[A](data))

  /**
    * `A` => HTTP entity
    */
  implicit def marshaller[Object](implicit
      objectMapper: ObjectMapper = defaultObjectMapper
  ): ToEntityMarshaller[Object] =
    Jackson.marshaller[Object](objectMapper)

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
    Unmarshaller { _ => bs =>
      Future.fromTry(Try(objectMapper.readValue[A](bs.toArray)))
    }

  /**
    * HTTP entity => `Source[A, _]`
    *
    * @tparam A
    *   type to decode
    * @return
    *   unmarshaller for `Source[A, _]`
    */
  implicit def sourceUnmarshaller[A: JavaTypeable](implicit
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
      objectMapper: ObjectMapper = defaultObjectMapper,
      support: JsonEntityStreamingSupport = EntityStreamingSupport.json()
  ): ToEntityMarshaller[SourceOf[A]] =
    jsonSourceStringMarshaller.compose(jsonSource[A])
}
