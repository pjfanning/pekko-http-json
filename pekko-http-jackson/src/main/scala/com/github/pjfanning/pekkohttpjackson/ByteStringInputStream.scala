/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.pjfanning.pekkohttpjackson

import java.io.{ ByteArrayInputStream, InputStream }
import java.lang.invoke.{ MethodHandles, MethodType }

import scala.util.Try

import org.apache.pekko
import pekko.util.ByteString
import pekko.util.ByteString.ByteString1C

private[pekkohttpjackson] object ByteStringInputStream {
  private val byteStringInputStreamMethodTypeOpt = Try {
    val lookup                = MethodHandles.publicLookup()
    val inputStreamMethodType = MethodType.methodType(classOf[InputStream])
    lookup.findVirtual(classOf[ByteString], "asInputStream", inputStreamMethodType)
  }.toOption

  def apply(bs: ByteString): InputStream = bs match {
    case cs: ByteString1C =>
      getInputStreamUnsafe(cs)
    case _ =>
      if (byteStringInputStreamMethodTypeOpt.isDefined) {
        byteStringInputStreamMethodTypeOpt.get.invoke(bs).asInstanceOf[InputStream]
      } else {
        getInputStreamUnsafe(bs)
      }
  }

  val byteStringSupportsAsInputStream: Boolean = byteStringInputStreamMethodTypeOpt.isDefined

  private def getInputStreamUnsafe(bs: ByteString): InputStream =
    new ByteArrayInputStream(bs.toArrayUnsafe())
}
