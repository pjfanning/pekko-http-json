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

import tools.jackson.core.StreamReadFeature
import tools.jackson.core.util.JsonRecyclerPools.BoundedPool
import tools.jackson.databind.json.JsonMapper
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.ContentTypes.{ `application/json`, `text/plain(UTF-8)` }
import org.apache.pekko.http.scaladsl.unmarshalling.{ Unmarshal, Unmarshaller }
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshaller.UnsupportedContentTypeException
import org.apache.pekko.stream.scaladsl.{ Sink, Source }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import tools.jackson.module.scala.DefaultScalaModule

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

object JacksonSupportSpec {

  final case class Foo(bar: String) {
    require(bar startsWith "bar", "bar must start with 'bar'!")
  }
}

final class JacksonSupportSpec extends AsyncWordSpec with Matchers with BeforeAndAfterAll {
  import JacksonSupport._
  import JacksonSupportSpec._

  private implicit val system: ActorSystem = ActorSystem()

  "JacksonSupport" should {
    "should enable marshalling and unmarshalling of case classes" in {
      val foo = Foo("bar")
      Marshal(foo)
        .to[RequestEntity]
        .flatMap(Unmarshal(_).to[Foo])
        .map(_ shouldBe foo)
    }

    "enable streamed marshalling and unmarshalling for json arrays" in {
      val foos = (0 to 100).map(i => Foo(s"bar-$i")).toList

      Marshal(Source(foos))
        .to[RequestEntity]
        .flatMap(entity => Unmarshal(entity).to[SourceOf[Foo]])
        .flatMap(_.runWith(Sink.seq))
        .map(_ shouldBe foos)
    }

    "should enable marshalling and unmarshalling of arrays of values" in {
      val foo = Seq(Foo("bar"))
      Marshal(foo)
        .to[RequestEntity]
        .flatMap(Unmarshal(_).to[Seq[Foo]])
        .map(_ shouldBe foo)
    }

    "provide proper error messages for requirement errors" in {
      val entity = HttpEntity(MediaTypes.`application/json`, """{ "bar": "baz" }""")
      Unmarshal(entity)
        .to[Foo]
        .failed
        .map(_.getMessage should include("requirement failed: bar must start with 'bar'!"))
    }

    "fail with NoContentException when unmarshalling empty entities" in {
      val entity = HttpEntity.empty(`application/json`)
      Unmarshal(entity)
        .to[Foo]
        .failed
        .map(_ shouldBe Unmarshaller.NoContentException)
    }

    "fail with UnsupportedContentTypeException when Content-Type is not `application/json`" in {
      val entity = HttpEntity("""{ "bar": "bar" }""")
      Unmarshal(entity)
        .to[Foo]
        .failed
        .map(
          _ shouldBe UnsupportedContentTypeException(Some(`text/plain(UTF-8)`), `application/json`)
        )
    }

    "allow unmarshalling with passed in Content-Types" in {
      val foo = Foo("bar")
      val `application/json-home` =
        MediaType.applicationWithFixedCharset("json-home", HttpCharsets.`UTF-8`, "json-home")

      final object CustomJacksonSupport extends JacksonSupport {
        override def unmarshallerContentTypes = List(`application/json`, `application/json-home`)
      }
      import CustomJacksonSupport._

      val entity = HttpEntity(`application/json-home`, """{ "bar": "bar" }""")
      Unmarshal(entity).to[Foo].map(_ shouldBe foo)
    }

    "default the stream read constraints" in {
      val defaultFactory = JacksonSupport.createJsonFactory(JacksonSupport.jacksonConfig)
      val src            = defaultFactory.streamReadConstraints()
      src.getMaxNestingDepth shouldEqual 1000
      src.getMaxNumberLength shouldEqual 1000
      src.getMaxStringLength shouldEqual 20000000
      src.getMaxNameLength shouldEqual 50000
      src.getMaxDocumentLength shouldEqual -1
    }

    "default the stream write constraints" in {
      val defaultFactory = JacksonSupport.createJsonFactory(JacksonSupport.jacksonConfig)
      val swc            = defaultFactory.streamWriteConstraints()
      swc.getMaxNestingDepth shouldEqual 1000
    }

    "default the buffer recycler" in {
      val defaultFactory = JacksonSupport.createJsonFactory(JacksonSupport.jacksonConfig)
      val pool           = defaultFactory._getRecyclerPool()
      pool.getClass.getSimpleName shouldEqual "ThreadLocalPool"
    }

    "support config override for the buffer recycler" in {
      val testCfg = ConfigFactory
        .parseString("""buffer-recycler.pool-instance=bounded
                       |buffer-recycler.bounded-pool-size=1234""".stripMargin)
        .withFallback(JacksonSupport.jacksonConfig)
      val factory = JacksonSupport.createJsonFactory(testCfg)
      val pool    = factory._getRecyclerPool()
      pool.getClass.getSimpleName shouldEqual "BoundedPool"
      pool.asInstanceOf[BoundedPool].capacity() shouldEqual 1234
    }

    "respect pekko-http-json.jackson.read.feature.include-source-in-location" in {
      val defaultFactory = JacksonSupport.createJsonFactory(JacksonSupport.jacksonConfig)
      defaultFactory.isEnabled(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION) shouldBe false
      val testCfg = ConfigFactory
        .parseString("read.feature.include-source-in-location=true")
        .withFallback(JacksonSupport.jacksonConfig)
      val testFactory = JacksonSupport.createJsonFactory(testCfg)
      testFactory.isEnabled(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION) shouldBe true
      JsonMapper
        .builder(testFactory)
        .build()
        .isEnabled(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION) shouldBe true
    }

    "support loading DefaultScalaModule" in {
      val testCfg = JacksonSupport.jacksonConfig
      val mapper  = JacksonSupport.createObjectMapper(testCfg)
      import scala.collection.JavaConverters._
      mapper.getRegisteredModules.asScala.map(_.getClass) should contain(
        classOf[DefaultScalaModule]
      )
    }
  }

  override protected def afterAll() = {
    Await.ready(system.terminate(), 42.seconds)
    super.afterAll()
  }
}
