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
import tools.jackson.core.StreamReadFeature
import tools.jackson.databind.{ DeserializationFeature, ObjectMapper }
import tools.jackson.dataformat.cbor.{ CBORFactory, CBORMapper }
import tools.jackson.module.scala.ClassTagExtensions

/**
  * CBOR flavour of [[JacksonConfigSupport]].
  *
  * `jackson-dataformat-cbor` is an optional dependency, so every reference to it is confined to
  * this object - nothing here is loaded unless [[JacksonDataFormat.Cbor]] is actually used.
  */
private[pekkohttpjackson3] object CborConfigSupport {

  def createCborFactory(config: Config): CBORFactory =
    CBORFactory
      .builder()
      .streamReadConstraints(JacksonConfigSupport.streamReadConstraints(config))
      .streamWriteConstraints(JacksonConfigSupport.streamWriteConstraints(config))
      .recyclerPool(JacksonConfigSupport.bufferRecyclerPool(config))
      .configure(
        StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION,
        JacksonConfigSupport.includeSourceInLocation(config)
      )
      .build()

  def createCborObjectMapper(config: Config): ObjectMapper with ClassTagExtensions = {
    val builder = CBORMapper.builder(createCborFactory(config))
    builder.disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
    JacksonConfigSupport.modules(config).foreach(builder.addModule)
    builder.build() :: ClassTagExtensions
  }
}
