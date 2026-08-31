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

import com.typesafe.config.Config
import org.apache.pekko.http.scaladsl.model.{ MediaType, MediaTypes }
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.scala.ClassTagExtensions

import java.util.Locale
import scala.collection.immutable.Seq

/**
  * A data format that [[JacksonSupport]] can marshal to and unmarshal from.
  *
  * The format in use is read from the `pekko-http-json.jackson.format` config setting - `json`
  * unless you override it - and can also be chosen in code by overriding
  * [[JacksonSupport.dataFormat]]; [[JsonSupport]] and [[CborSupport]] do exactly that.
  */
sealed abstract class JacksonDataFormat(val name: String) {

  /** The media type that entities of this format are marshalled to. */
  def mediaType: MediaType

  private[pekkohttpjackson3] def createObjectMapper(
      config: Config
  ): ObjectMapper with ClassTagExtensions

  override def toString: String = name
}

object JacksonDataFormat {

  /** JSON, via `tools.jackson.databind.json.JsonMapper`. */
  case object Json extends JacksonDataFormat("json") {
    override def mediaType: MediaType = MediaTypes.`application/json`

    override private[pekkohttpjackson3] def createObjectMapper(
        config: Config
    ): ObjectMapper with ClassTagExtensions =
      JacksonConfigSupport.createJsonObjectMapper(config)
  }

  /**
    * CBOR, via `tools.jackson.dataformat.cbor.CBORMapper`.
    *
    * `tools.jackson.dataformat:jackson-dataformat-cbor` is an optional dependency of this library,
    * so add it to your build before selecting this format.
    */
  case object Cbor extends JacksonDataFormat("cbor") {
    override def mediaType: MediaType = MediaTypes.`application/cbor`

    override private[pekkohttpjackson3] def createObjectMapper(
        config: Config
    ): ObjectMapper with ClassTagExtensions =
      CborConfigSupport.createCborObjectMapper(config)
  }

  val values: Seq[JacksonDataFormat] = List(Json, Cbor)

  /** The format configured by `pekko-http-json.jackson.format`. */
  lazy val default: JacksonDataFormat = apply(
    JacksonConfigSupport.jacksonConfig.getString("format")
  )

  /** @throws IllegalArgumentException if `name` does not name a supported format */
  def apply(name: String): JacksonDataFormat =
    values
      .find(_.name == name.toLowerCase(Locale.ROOT))
      .getOrElse(
        throw new IllegalArgumentException(
          s"Unknown Jackson data format: '$name' (supported: ${values.map(_.name).mkString(", ")})"
        )
      )
}
