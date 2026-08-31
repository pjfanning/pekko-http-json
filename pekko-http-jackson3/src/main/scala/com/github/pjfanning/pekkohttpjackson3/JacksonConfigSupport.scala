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

import com.typesafe.config.{ Config, ConfigFactory }
import tools.jackson.core.util.{ BufferRecycler, JsonRecyclerPools, RecyclerPool }
import tools.jackson.core.{ StreamReadConstraints, StreamReadFeature, StreamWriteConstraints }
import tools.jackson.core.json.{ JsonFactory, JsonFactoryBuilder }
import tools.jackson.databind.{ DeserializationFeature, JacksonModule, ObjectMapper }
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.scala.ClassTagExtensions

import scala.util.Try

/**
  * The parts of the `pekko-http-json.jackson` config that are not specific to any one data format.
  *
  * This lives outside of [[JacksonSupport]] so that reading the config does not depend on that
  * object having finished initialising - the trait it extends asks for the configured data format
  * while the object is still being constructed.
  */
private[pekkohttpjackson3] object JacksonConfigSupport {

  val jacksonConfig: Config = ConfigFactory.load().getConfig("pekko-http-json.jackson")

  def streamReadConstraints(config: Config): StreamReadConstraints =
    StreamReadConstraints
      .builder()
      .maxNestingDepth(config.getInt("read.max-nesting-depth"))
      .maxNumberLength(config.getInt("read.max-number-length"))
      .maxStringLength(config.getInt("read.max-string-length"))
      .maxNameLength(config.getInt("read.max-name-length"))
      .maxDocumentLength(config.getInt("read.max-document-length"))
      .maxTokenCount(config.getInt("read.max-token-count"))
      .build()

  def streamWriteConstraints(config: Config): StreamWriteConstraints =
    StreamWriteConstraints
      .builder()
      .maxNestingDepth(config.getInt("write.max-nesting-depth"))
      .build()

  def includeSourceInLocation(config: Config): Boolean =
    config.getBoolean("read.feature.include-source-in-location")

  def bufferRecyclerPool(config: Config): RecyclerPool[BufferRecycler] =
    config.getString("buffer-recycler.pool-instance") match {
      case "thread-local"            => JsonRecyclerPools.threadLocalPool()
      case "concurrent-deque"        => JsonRecyclerPools.newConcurrentDequePool()
      case "shared-concurrent-deque" => JsonRecyclerPools.sharedConcurrentDequePool()
      case "bounded"                 =>
        JsonRecyclerPools.newBoundedPool(config.getInt("buffer-recycler.bounded-pool-size"))
      case "non-recycling" => JsonRecyclerPools.nonRecyclingPool()
      case other           => throw new IllegalArgumentException(s"Unknown recycler-pool: $other")
    }

  /** The modules named by `jackson-modules`, in the order they are configured. */
  def modules(config: Config): List[JacksonModule] = {
    import org.apache.pekko.util.ccompat.JavaConverters._
    config.getStringList("jackson-modules").asScala.toList.map(loadModule)
  }

  def createJsonFactory(config: Config): JsonFactory = {
    val jsonFactoryBuilder: JsonFactoryBuilder = JsonFactory
      .builder()
      .asInstanceOf[JsonFactoryBuilder]
      .streamReadConstraints(streamReadConstraints(config))
      .streamWriteConstraints(streamWriteConstraints(config))
      .recyclerPool(bufferRecyclerPool(config))
      .configure(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION, includeSourceInLocation(config))
    jsonFactoryBuilder.build()
  }

  def createJsonObjectMapper(config: Config): ObjectMapper with ClassTagExtensions = {
    val builder = JsonMapper.builder(createJsonFactory(config))
    builder.disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
    modules(config).foreach(builder.addModule)
    builder.build() :: ClassTagExtensions
  }

  private def loadModule(fcqn: String): JacksonModule = {
    val inst = Try(Class.forName(fcqn).getConstructor().newInstance())
      .getOrElse(Class.forName(fcqn + "$").getConstructor().newInstance())
    inst.asInstanceOf[JacksonModule]
  }
}
