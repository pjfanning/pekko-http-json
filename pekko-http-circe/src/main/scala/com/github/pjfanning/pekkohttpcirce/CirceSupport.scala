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

package com.github.pjfanning.pekkohttpcirce

import org.apache.pekko.http.scaladsl.unmarshalling.{ FromEntityUnmarshaller, Unmarshaller }
import org.apache.pekko.util.ByteString
import com.github.pjfanning.pekkohttpcircebase.{
  BaseSupport,
  ErrorAccumulatingSupport,
  FailFastSupport
}
import io.circe.{ Json, jawn }
import io.circe.parser.parse

import scala.concurrent.Future

/**
  * Automatic to and from JSON marshalling/unmarshalling using an in-scope circe protocol. The
  * unmarshaller fails fast, throwing the first `Error` encountered.
  *
  * To use automatic codec derivation, user needs to import `io.circe.generic.auto._`.
  */
object FailFastCirceSupport extends FailFastCirceSupport

/**
  * Automatic to and from JSON marshalling/unmarshalling using an in-scope circe protocol. The
  * unmarshaller fails fast, throwing the first `Error` encountered.
  *
  * To use automatic codec derivation import `io.circe.generic.auto._`.
  */
trait FailFastCirceSupport extends BaseCirceSupport with FailFastUnmarshaller

/**
  * Automatic to and from JSON marshalling/unmarshalling using an in-scope circe protocol. The
  * unmarshaller accumulates all errors in the exception `Errors`.
  *
  * To use automatic codec derivation, user needs to import `io.circe.generic.auto._`.
  */
object ErrorAccumulatingCirceSupport extends ErrorAccumulatingCirceSupport

/**
  * Automatic to and from JSON marshalling/unmarshalling using an in-scope circe protocol. The
  * unmarshaller accumulates all errors in the exception `Errors`.
  *
  * To use automatic codec derivation import `io.circe.generic.auto._`.
  */
trait ErrorAccumulatingCirceSupport extends BaseCirceSupport with ErrorAccumulatingUnmarshaller

/**
  * Automatic to and from JSON marshalling/unmarshalling using an in-scope circe protocol.
  */
trait BaseCirceSupport extends BaseSupport {
  override implicit final val jsonUnmarshaller: FromEntityUnmarshaller[Json] =
    Unmarshaller.byteStringUnmarshaller
      .forContentTypes(unmarshallerContentTypes: _*)
      .map {
        case ByteString.empty => throw Unmarshaller.NoContentException
        case data             => jawn.parseByteBuffer(data.asByteBuffer).fold(throw _, identity)
      }

  override implicit final val safeJsonUnmarshaller
      : FromEntityUnmarshaller[Either[io.circe.ParsingFailure, Json]] =
    Unmarshaller.stringUnmarshaller
      .forContentTypes(unmarshallerContentTypes: _*)
      .map(parse)

  override def byteStringJsonUnmarshaller: Unmarshaller[ByteString, Json] =
    Unmarshaller { ec => bs =>
      Future {
        jawn.parseByteBuffer(bs.asByteBuffer) match {
          case Right(json) => json
          case Left(pf)    => throw pf
        }
      }(ec)
    }
}

/**
  * Mix-in this trait to fail on the first error during unmarshalling.
  */
trait FailFastUnmarshaller extends FailFastSupport

/**
  * Mix-in this trait to accumulate all errors during unmarshalling.
  */
trait ErrorAccumulatingUnmarshaller extends ErrorAccumulatingSupport
