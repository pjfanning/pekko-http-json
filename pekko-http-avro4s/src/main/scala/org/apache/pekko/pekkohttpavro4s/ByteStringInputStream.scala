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

package org.apache.pekko.pekkohttpavro4s

import org.apache.pekko.annotation.InternalApi
import org.apache.pekko.util.ByteString
import org.apache.pekko.util.ByteString.ByteStrings

import java.io.{ ByteArrayInputStream, InputStream, SequenceInputStream }
import scala.collection.JavaConverters._

/**
  * INTERNAL API
  *
  * Helper to create an InputStream from a ByteString. Needs to be in the same package as ByteString
  * to access the package-private ByteString methods.
  */
@InternalApi
object ByteStringInputStream {
  def apply(bs: ByteString): InputStream = bs match {
    case bss: ByteStrings =>
      new SequenceInputStream(bss.bytestrings.iterator.map(apply).asJavaEnumeration)
    case _ =>
      new ByteArrayInputStream(bs.toArrayUnsafe())
  }
}
