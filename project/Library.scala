import sbt.Keys.*
import sbt.*

object Library {
  object Version {
    val pekko         = "2.0.0-M3"
    val pekkoHttp     = "2.0.0-M1"
    val argonaut      = "6.3.13"
    val avro4s        = "4.1.2"
    val circe         = "0.14.16"
    val jackson2      = "2.22.1"
    val jackson3      = "3.2.0"
    val json4s        = "4.1.1"
    val jsoniterScala = "2.39.1"
    val ninny         = "0.9.4"
    val play          = "3.0.6"
    val scalaTest     = "3.2.20"
    val upickle       = "4.3.2"
    val zioJson       = "0.7.36"
  }
  // format: off
  val pekkoHttp            = "org.apache.pekko"                      %% "pekko-http"            % Version.pekkoHttp
  val pekkoStream          = "org.apache.pekko"                      %% "pekko-stream"          % Version.pekko
  val argonaut             = "io.github.argonaut-io"                 %% "argonaut"              % Version.argonaut
  val avro4sJson           = "com.sksamuel.avro4s"                   %% "avro4s-json"           % Version.avro4s
  val circe                = "io.circe"                              %% "circe-core"            % Version.circe
  val circeGeneric         = "io.circe"                              %% "circe-generic"         % Version.circe
  val circeParser          = "io.circe"                              %% "circe-parser"          % Version.circe
  val jacksonModuleScala2  = "com.fasterxml.jackson.module"          %% "jackson-module-scala"  % Version.jackson2
  val jacksonModuleParamNames2 = "com.fasterxml.jackson.module"       % "jackson-module-parameter-names" % Version.jackson2
  val jacksonModuleScala3  = "tools.jackson.module"                  %% "jackson-module-scala"  % Version.jackson3
  val json4sCore           = "io.github.json4s"                      %% "json4s-core"           % Version.json4s
  val json4sJackson        = "io.github.json4s"                      %% "json4s-jackson"        % Version.json4s
  val json4sNative         = "io.github.json4s"                      %% "json4s-native"         % Version.json4s
  val jsoniterScalaCirce   = "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-circe"  % Version.jsoniterScala
  val jsoniterScalaCore    = "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core"   % Version.jsoniterScala
  val jsoniterScalaMacros  = "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % Version.jsoniterScala
  val ninny                = "tk.nrktkt"                             %% "ninny"                 % Version.ninny
  val playJson             = "org.playframework"                     %% "play-json"             % Version.play
  val scalaTest            = "org.scalatest"                         %% "scalatest"             % Version.scalaTest
  val upickle              = "com.lihaoyi"                           %% "upickle"               % Version.upickle
  val zioJson              = "dev.zio"                               %% "zio-json"              % Version.zioJson
}
