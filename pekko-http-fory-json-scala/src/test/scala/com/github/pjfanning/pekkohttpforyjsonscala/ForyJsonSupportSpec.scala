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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.ContentTypes.{ `application/json`, `text/plain(UTF-8)` }
import org.apache.pekko.http.scaladsl.unmarshalling.{ Unmarshal, Unmarshaller }
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshaller.UnsupportedContentTypeException
import org.apache.pekko.stream.scaladsl.{ Sink, Source }
import org.apache.fory.json.scala.ScalaTypeRef
import org.apache.fory.reflect.TypeRef
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

// Fory leaves the fields of a case class declared inside an `object` unset, so the types used here
// are declared at the top level rather than in a companion object like the other modules' specs.
final case class Foo(bar: String)

final class ForyJsonSupportSpec extends AsyncWordSpec with Matchers with BeforeAndAfterAll {
  import ForyJsonSupport._

  private implicit val system: ActorSystem      = ActorSystem()
  private implicit val fooTypeRef: TypeRef[Foo] = ScalaTypeRef[Foo]

  "ForyJsonSupport" should {
    "enable marshalling and unmarshalling of case classes" in {
      val foo = Foo("bar")
      Marshal(foo)
        .to[RequestEntity]
        .flatMap(Unmarshal(_).to[Foo])
        .map(_ shouldBe foo)
    }

    "marshal to compact json" in
    Marshal(Foo("bar"))
      .to[RequestEntity]
      .map(_.asInstanceOf[HttpEntity.Strict].data.utf8String shouldBe """{"bar":"bar"}""")

    "enable streamed marshalling and unmarshalling for json arrays" in {
      val foos = (0 to 100).map(i => Foo(s"bar-$i")).toList

      Marshal(Source(foos))
        .to[RequestEntity]
        .flatMap(entity => Unmarshal(entity).to[SourceOf[Foo]])
        .flatMap(_.runWith(Sink.seq))
        .map(_ shouldBe foos)
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
      val foo                     = Foo("bar")
      val `application/json-home` =
        MediaType.applicationWithFixedCharset("json-home", HttpCharsets.`UTF-8`, "json-home")

      object CustomForyJsonSupport extends ForyJsonSupport {
        override def unmarshallerContentTypes: Seq[ContentTypeRange] =
          List(`application/json`, `application/json-home`)
      }
      import CustomForyJsonSupport._

      val entity = HttpEntity(`application/json-home`, """{ "bar": "bar" }""")
      Unmarshal(entity).to[Foo].map(_ shouldBe foo)
    }
  }

  override protected def afterAll(): Unit = {
    Await.ready(system.terminate(), 42.seconds)
    super.afterAll()
  }
}
