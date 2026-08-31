# pekko-http-json #

[![License](http://img.shields.io/:license-apache-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.pjfanning/pekko-http-circe_2.13)](https://search.maven.org/artifact/com.github.pjfanning/pekko-http-circe_2.13)
<!---
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)
-->

Releases are pushed to Maven Central. Snapshots are pushed to Sonatype. See below.

This is a fork of [akka-http-json](https://github.com/hseeberger/akka-http-json). Thanks to Heiko Seeberger and all those who contributed to akka-http-json.

pekko-http-json provides JSON (un)marshalling support for [Apache Pekko HTTP](https://github.com/apache/incubator-pekko-http) via the following JSON libraries:
- [Argonaut](https://github.com/argonaut-io/argonaut)
- [avro4s](https://github.com/sksamuel/avro4s)
- [AVSystem GenCodec](https://github.com/AVSystem/scala-commons/blob/master/docs/GenCodec.md)
- [circe](https://circe.github.io/circe/)
- [Fory JSON](https://fory.apache.org) via [fory-json-scala](https://github.com/apache/fory) - Scala 2.13 and Scala 3 only
- [Jackson](https://github.com/FasterXML/jackson) via [Scala Module](https://github.com/FasterXML/jackson-module-scala) by default
  - pekko-http-jackson v2.0.x supports Jackson 2.14
  - pekko-http-jackson v2.1.x supports Jackson 2.15
    - Jackson 2.15 users should read about [StreamReadConstraints](https://javadoc.io/static/com.fasterxml.jackson.core/jackson-core/2.15.2/com/fasterxml/jackson/core/StreamReadConstraints.html)
    - pekko-http-jackson uses the default constraints but you can override them by overriding the configs in [reference.conf](https://github.com/pjfanning/pekko-http-json/blob/main/pekko-http-jackson/src/main/resources/reference.conf)
    - you can override the configs by adding an application.conf file to your project ([Lightbend Config docs](https://github.com/lightbend/config))
  - pekko-http-jackson v2.3.x supports Jackson 2.16
    - pekko-http-jackson uses the default Jackson constraints (including some new to Jackson 2.16) but you can override them by overriding the configs in [reference.conf](https://github.com/pjfanning/pekko-http-json/blob/main/pekko-http-jackson/src/main/resources/reference.conf)
  - pekko-http-jackson v2.5.x supports Jackson 2.17
  - pekko-http-jackson3 supports [Jackson 3](https://github.com/FasterXML/jackson-3) and, as well as
    JSON, can marshal and unmarshal [CBOR](https://cbor.io) - see below
- [Json4s](https://github.com/json4s/json4s)
- [jsoniter-scala](https://github.com/plokhotnyuk/jsoniter-scala)
- [ninny](https://nrktkt.github.io/ninny-json/USERGUIDE)
- [Play JSON](https://www.playframework.com/documentation/2.6.x/ScalaJson)
- [uPickle](https://github.com/lihaoyi/upickle-pprint)
- [zio-json](https://github.com/zio/zio-json)

## Installation

The artifacts are published to Maven Central.

``` scala
libraryDependencies ++= Seq(
  "com.github.pjfanning" %% "pekko-http-circe" % PekkoHttpJsonVersion,
  ...
)
```

### Snapshots

Check https://central.sonatype.com/service/rest/repository/browse/maven-snapshots/com/github/pjfanning/ to find the version numbers for the latest pekko-http-json snapshots.

``` scala
resolvers += "Sonatype Central Snapshots" at "https://central.sonatype.com/repository/maven-snapshots/"
// older snapshots on OSSRH
//resolvers ++= Resolver.sonatypeOssRepos("snapshots")
```

## Usage

Use respective support trait or object, e.g. `ArgonautSupport`, `FailFastCirceSupport`, etc. into your Pekko HTTP code which is supposed to (un)marshal from/to JSON. Don't forget to provide the type class instances for the respective JSON libraries, if needed.

### CBOR with pekko-http-jackson3

`pekko-http-jackson3` marshals to JSON by default. It can marshal to CBOR instead, either for the
whole application:

``` hocon
pekko-http-json.jackson.format = "cbor"
```

or for the routes you pick, by importing `CborSupport` (or `JsonSupport`, which stays on JSON
whatever the config says) instead of `JacksonSupport`:

``` scala
import com.github.pjfanning.pekkohttpjackson3.CborSupport._
```

Either way, entities are marshalled to and unmarshalled from `application/cbor`, and the
`pekko-http-json.jackson.read` and `write` settings apply just as they do to JSON. The
`jackson-dataformat-cbor` jar is an optional dependency of `pekko-http-jackson3`, so add it to your
build to use CBOR:

``` scala
libraryDependencies += "tools.jackson.dataformat" % "jackson-dataformat-cbor" % "3.2.2"
```

The `Source` based streaming marshallers frame their output as a JSON array, so they remain
JSON only; they throw an `UnsupportedOperationException` when the data format is CBOR.

## Contribution policy ##

Contributions via GitHub pull requests are gladly accepted from their original author. Along with any pull requests, please state that the contribution is your original work and that you license the work to the project under the project's open source license. Whether or not you state this explicitly, by submitting any copyrighted material via pull request, email, or other means you agree to license the material under the project's open source license and warrant that you have the legal authority to do so.

## License ##

This code is open source software licensed under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).
