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

/**
  * [[JacksonSupport]] pinned to CBOR, whatever `pekko-http-json.jackson.format` is set to.
  *
  * Entities are marshalled to, and unmarshalled from, `application/cbor`. This needs
  * `tools.jackson.dataformat:jackson-dataformat-cbor` on the classpath - it is an optional
  * dependency of this library:
  *
  * {{{
  * libraryDependencies += "tools.jackson.dataformat" % "jackson-dataformat-cbor" % jacksonVersion
  * }}}
  */
object CborSupport extends CborSupport

trait CborSupport extends JacksonSupport {
  final override def dataFormat: JacksonDataFormat = JacksonDataFormat.Cbor
}
