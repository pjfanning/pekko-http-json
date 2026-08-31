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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import java.net.{ URL, URLClassLoader }
import java.util.Collections
import scala.annotation.tailrec

/**
  * `jackson-dataformat-cbor` is an optional dependency, so JSON has to keep working when it is
  * absent. Everything that touches it is confined to `CborConfigSupport`, which is only loaded once
  * [[JacksonDataFormat.Cbor]] is used - this reloads the module in a class loader that cannot see
  * the CBOR classes to prove it.
  */
final class OptionalCborDependencySpec extends AnyWordSpec with Matchers {

  private val cborFreeLoader = new URLClassLoader(classPath, ClassLoader.getPlatformClassLoader) {
    override def loadClass(name: String, resolve: Boolean): Class[_] = {
      if (name.startsWith("tools.jackson.dataformat.cbor")) throw new ClassNotFoundException(name)
      super.loadClass(name, resolve)
    }
  }

  "pekko-http-jackson3" should {
    "marshal JSON without jackson-dataformat-cbor on the classpath" in {
      val mapper = objectMapperIn(cborFreeLoader, "JacksonSupport$")
      // a JDK type rather than a Scala one: the isolated loader has its own copy of the Scala
      // library, so a Map from this one would not be the Map its DefaultScalaModule knows
      mapper.getClass
        .getMethod("writeValueAsString", classOf[Any])
        .invoke(mapper, Collections.singletonMap("bar", "bar")) shouldEqual """{"bar":"bar"}"""
    }

    "fail only once CBOR is actually asked for" in {
      // guards the test above: without this the class loader filter could silently be a no-op
      a[ClassNotFoundException] should be thrownBy cborFreeLoader.loadClass(
        "tools.jackson.dataformat.cbor.CBORMapper"
      )
      val thrown = the[Exception] thrownBy objectMapperIn(cborFreeLoader, "CborSupport$")
      thrown.getCause shouldBe a[NoClassDefFoundError]
    }
  }

  private def objectMapperIn(loader: ClassLoader, supportObject: String): AnyRef = {
    val clazz  = loader.loadClass(s"com.github.pjfanning.pekkohttpjackson3.$supportObject")
    val module = clazz.getField("MODULE$").get(null)
    clazz.getMethod("defaultObjectMapper").invoke(module)
  }

  private def classPath: Array[URL] = {
    @tailrec
    def urlsOf(loader: ClassLoader, acc: List[URL]): List[URL] =
      loader match {
        case null                => acc
        case url: URLClassLoader => urlsOf(url.getParent, acc ::: url.getURLs.toList)
        case other               => urlsOf(other.getParent, acc)
      }

    val fromLoaders = urlsOf(getClass.getClassLoader, Nil)
    val urls        =
      if (fromLoaders.nonEmpty) fromLoaders
      else
        System
          .getProperty("java.class.path")
          .split(File.pathSeparatorChar)
          .map(new File(_).toURI.toURL)
          .toList
    urls.toArray
  }
}
