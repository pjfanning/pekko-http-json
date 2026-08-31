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

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.ContentTypes.`text/plain(UTF-8)`
import org.apache.pekko.http.scaladsl.unmarshalling.{ Unmarshal, Unmarshaller }
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshaller.UnsupportedContentTypeException
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import tools.jackson.dataformat.cbor.{ CBORFactory, CBORMapper, CBORParser }
import tools.jackson.module.scala.DefaultScalaModule

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

object CborSupportSpec {

  final case class Foo(bar: String) {
    require(bar startsWith "bar", "bar must start with 'bar'!")
  }

  val plainCborMapper: CBORMapper =
    CBORMapper.builder().addModule(DefaultScalaModule).build()
}

final class CborSupportSpec extends AsyncWordSpec with Matchers with BeforeAndAfterAll {
  import CborSupport._
  import CborSupportSpec._

  private implicit val system: ActorSystem = ActorSystem()

  private val `application/cbor` = MediaTypes.`application/cbor`

  "CborSupport" should {
    "enable marshalling and unmarshalling of case classes" in {
      val foo = Foo("bar")
      Marshal(foo)
        .to[RequestEntity]
        .flatMap(Unmarshal(_).to[Foo])
        .map(_ shouldBe foo)
    }

    "enable marshalling and unmarshalling of arrays of values" in {
      val foo = Seq(Foo("bar"))
      Marshal(foo)
        .to[RequestEntity]
        .flatMap(Unmarshal(_).to[Seq[Foo]])
        .map(_ shouldBe foo)
    }

    "marshal to `application/cbor` entities that hold CBOR, not JSON" in {
      val foo = Foo("bar")
      Marshal(foo).to[RequestEntity].flatMap { entity =>
        entity.contentType.mediaType shouldBe `application/cbor`
        entity.toStrict(3.seconds).map { strict =>
          val bytes = strict.data.toArray
          // a CBOR document starts with a major type byte, never with the '{' of a JSON object
          bytes.head should not be '{'.toByte
          plainCborMapper.readValue(bytes, classOf[Foo]) shouldBe foo
        }
      }
    }

    "unmarshal CBOR written by a plain CBORMapper" in {
      val foo    = Foo("bar")
      val entity = HttpEntity(`application/cbor`, plainCborMapper.writeValueAsBytes(foo))
      Unmarshal(entity).to[Foo].map(_ shouldBe foo)
    }

    "unmarshal from `ByteString`" in {
      val foo = Foo("bar")
      Unmarshal(ByteString(plainCborMapper.writeValueAsBytes(foo))).to[Foo].map(_ shouldBe foo)
    }

    "provide proper error messages for requirement errors" in {
      val entity =
        HttpEntity(`application/cbor`, plainCborMapper.writeValueAsBytes(Map("bar" -> "baz")))
      Unmarshal(entity)
        .to[Foo]
        .failed
        .map(_.getMessage should include("requirement failed: bar must start with 'bar'!"))
    }

    "fail with NoContentException when unmarshalling empty entities" in {
      val entity = HttpEntity.empty(ContentType(`application/cbor`, () => HttpCharsets.`UTF-8`))
      Unmarshal(entity)
        .to[Foo]
        .failed
        .map(_ shouldBe Unmarshaller.NoContentException)
    }

    "fail with UnsupportedContentTypeException when Content-Type is not `application/cbor`" in {
      val entity = HttpEntity("""{ "bar": "bar" }""")
      Unmarshal(entity)
        .to[Foo]
        .failed
        .map(
          _ shouldBe UnsupportedContentTypeException(
            Some(`text/plain(UTF-8)`),
            ContentType(`application/cbor`, () => HttpCharsets.`UTF-8`)
          )
        )
    }

    "allow unmarshalling with passed in Content-Types" in {
      val foo                     = Foo("bar")
      val `application/cbor-home` =
        MediaType.applicationBinary("cbor-home", MediaType.Compressible)

      object CustomCborSupport extends CborSupport {
        override def mediaTypes = List(`application/cbor`, `application/cbor-home`)
      }
      import CustomCborSupport._

      val entity = HttpEntity(`application/cbor-home`, plainCborMapper.writeValueAsBytes(foo))
      Unmarshal(entity).to[Foo].map(_ shouldBe foo)
    }

    "reject the JSON array framed streaming marshallers" in {
      val marshalling = the[UnsupportedOperationException] thrownBy Marshal(
        Source(List(Foo("bar")))
      ).to[RequestEntity]
      marshalling.getMessage should include("data format is cbor")

      val unmarshalling = the[UnsupportedOperationException] thrownBy Unmarshal(
        HttpEntity(`application/cbor`, ByteString.empty)
      ).to[SourceOf[Foo]]
      unmarshalling.getMessage should include("data format is cbor")
    }

    "use a CBOR backed ObjectMapper" in {
      // `:: ClassTagExtensions` copies the mapper into a mixin subclass, so the CBOR flavour shows
      // up in the token stream factory rather than in the mapper's own class
      JacksonSupport
        .objectMapperFor(JacksonDataFormat.Cbor)
        .tokenStreamFactory() shouldBe a[CBORFactory]
      CborSupport.dataFormat shouldBe JacksonDataFormat.Cbor
      CborSupport.mediaTypes shouldEqual List(`application/cbor`)
    }

    "apply the configured stream read constraints to the CBOR factory" in {
      val testCfg = ConfigFactory
        .parseString("read.max-string-length=17")
        .withFallback(JacksonSupport.jacksonConfig)
      val factory = CborConfigSupport.createCborFactory(testCfg)
      factory.streamReadConstraints().getMaxStringLength shouldEqual 17
      factory.streamWriteConstraints().getMaxNestingDepth shouldEqual 1000
      factory._getRecyclerPool().getClass.getSimpleName shouldEqual "ThreadLocalPool"
    }

    "load the configured Jackson modules into the CBOR mapper" in {
      val mapper = CborConfigSupport.createCborObjectMapper(JacksonSupport.jacksonConfig)
      import org.apache.pekko.util.ccompat.JavaConverters._
      mapper.registeredModules.asScala.map(_.getClass) should contain(classOf[DefaultScalaModule])
    }

    "read CBOR specific parser features" in {
      // proves the entity really went through the CBOR parser rather than the JSON one
      val parser =
        CborConfigSupport
          .createCborFactory(JacksonSupport.jacksonConfig)
          .createParser(
            tools.jackson.core.ObjectReadContext.empty(),
            plainCborMapper.writeValueAsBytes(Foo("bar"))
          )
      try parser shouldBe a[CBORParser]
      finally parser.close()
    }
  }

  override protected def afterAll() = {
    Await.ready(system.terminate(), 42.seconds)
    super.afterAll()
  }
}
