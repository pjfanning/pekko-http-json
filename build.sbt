// *****************************************************************************
// Build settings
// *****************************************************************************

inThisBuild(
  Seq(
    organization := "com.github.pjfanning",
    startYear    := Some(2023),
    licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
    homepage := Some(url("https://github.com/pjfanning/pekko-http-json")),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/pjfanning/pekko-http-json"),
        "git@github.com:pjfanning/pekko-http-json.git"
      )
    ),
    developers := List(
      Developer(
        "hseeberger",
        "Heiko Seeberger",
        "mail@heikoseeberger.de",
        url("https://github.com/hseeberger")
      ),
      Developer(
        "pjfanning",
        "PJ Fanning",
        "",
        url("https://github.com/pjfanning")
      ),
    ),
    scalaVersion       := "2.13.16",
    crossScalaVersions := Seq("2.13.16", "2.12.20"),
    scalacOptions ++= Seq(
      "-unchecked",
      "-deprecation",
      "-language:_",
      "-encoding",
      "UTF-8",
      "-Ywarn-unused:imports",
      "-target:jvm-1.8"
    ),
    scalafmtOnCompile := true,
    dynverSeparator   := "_" // the default `+` is not compatible with docker tags
  )
)

val withScala3 = Seq(
  crossScalaVersions += "3.3.5",
)

// *****************************************************************************
// Projects
// *****************************************************************************

lazy val `pekko-http-json` =
  project
    .in(file("."))
    .disablePlugins(MimaPlugin)
    .aggregate(
      `pekko-http-argonaut`,
      `pekko-http-avro4s`,
      `pekko-http-circe`,
      `pekko-http-jackson`,
      `pekko-http-json4s`,
      `pekko-http-jsoniter-scala`,
      `pekko-http-ninny`,
      `pekko-http-play-json`,
      `pekko-http-upickle`,
      `pekko-http-zio-json`,
    )
    .settings(commonSettings)
    .settings(
      Compile / unmanagedSourceDirectories := Seq.empty,
      Test / unmanagedSourceDirectories    := Seq.empty,
      publishArtifact                      := false,
    )

lazy val `pekko-http-argonaut` =
  project
    .settings(commonSettings, withScala3)
    .settings(
      libraryDependencies ++= Seq(
        library.pekkoHttp,
        library.argonaut,
        library.pekkoStream % Provided,
        library.scalaTest   % Test,
      )
    )

lazy val `pekko-http-circe` =
  project
    .settings(commonSettings, withScala3)
    .settings(
      libraryDependencies ++= Seq(
        library.pekkoHttp,
        library.circe,
        library.circeParser,
        library.pekkoStream  % Provided,
        library.circeGeneric % Test,
        library.scalaTest    % Test,
      )
    )

lazy val `pekko-http-jackson` =
  project
    .settings(commonSettings, withScala3)
    .settings(
      libraryDependencies ++= Seq(
        library.pekkoHttp,
        library.pekkoHttpJacksonJava,
        library.jacksonModuleScala,
        library.pekkoStream             % Provided,
        library.scalaTest               % Test
      )
    )

lazy val `pekko-http-json4s` =
  project
    .settings(commonSettings, withScala3)
    .settings(
      libraryDependencies ++= Seq(
        library.pekkoHttp,
        library.json4sCore,
        library.pekkoStream   % Provided,
        library.json4sJackson % Test,
        library.json4sNative  % Test,
        library.scalaTest     % Test,
      )
    )

lazy val `pekko-http-jsoniter-scala` =
  project
    .settings(commonSettings, withScala3)
    .settings(
      libraryDependencies ++= Seq(
        library.pekkoHttp,
        library.jsoniterScalaCore,
        library.pekkoStream         % Provided,
        library.jsoniterScalaMacros % Test,
        library.scalaTest           % Test,
      )
    )

lazy val `pekko-http-ninny` =
  project
    .settings(commonSettings, withScala3)
    .settings(
      libraryDependencies ++= Seq(
        library.pekkoHttp,
        library.ninny,
        library.pekkoStream % Provided,
        library.scalaTest   % Test,
      )
    )

lazy val `pekko-http-play-json` =
  project
    .settings(commonSettings, withScala3)
    .settings(
      libraryDependencies ++= Seq(
        library.pekkoHttp,
        library.playJson,
        library.pekkoStream % Provided,
        library.scalaTest   % Test,
      )
    )

lazy val `pekko-http-upickle` =
  project
    .settings(commonSettings, withScala3)
    .settings(
      libraryDependencies ++= Seq(
        library.pekkoHttp,
        library.upickle,
        library.pekkoStream % Provided,
        library.scalaTest   % Test,
      )
    )

lazy val `pekko-http-avro4s` =
  project
    .settings(commonSettings)
    .settings(
      libraryDependencies ++= Seq(
        library.pekkoHttp,
        library.avro4sJson,
        library.pekkoStream % Provided,
        library.scalaTest   % Test,
      )
    )

lazy val `pekko-http-zio-json` =
  project
    .settings(commonSettings, withScala3)
    .settings(
      libraryDependencies ++= Seq(
        library.pekkoHttp,
        library.zioJson,
        library.pekkoStream % Provided,
        library.scalaTest   % Test
      )
    )

// *****************************************************************************
// Project settings
// *****************************************************************************

lazy val commonSettings =
  Seq(
    // Also (automatically) format build definition together with sources
    Compile / scalafmt := {
      val _ = (Compile / scalafmtSbt).value
      (Compile / scalafmt).value
    }
  )

// *****************************************************************************
// Library dependencies
// *****************************************************************************

lazy val library =
  new {
    object Version {
      val pekko              = "1.1.3"
      val pekkoHttp          = "1.1.0"
      val argonaut           = "6.3.11"
      val avro4s             = "4.1.2"
      val circe              = "0.14.10"
      val jacksonModuleScala = "2.18.2"
      val json4s             = "4.0.7"
      val jsoniterScala      = "2.33.2"
      val ninny              = "0.9.1"
      val play               = "3.0.4"
      val scalaTest          = "3.2.19"
      val upickle            = "4.1.0"
      val zioJson            = "0.7.36"
    }
    // format: off
    val pekkoHttp            = "org.apache.pekko"                      %% "pekko-http"            % Version.pekkoHttp
    val pekkoHttpJacksonJava = "org.apache.pekko"                      %% "pekko-http-jackson"    % Version.pekkoHttp
    val pekkoStream          = "org.apache.pekko"                      %% "pekko-stream"          % Version.pekko
    val argonaut             = "io.github.argonaut-io"                 %% "argonaut"              % Version.argonaut
    val avro4sJson           = "com.sksamuel.avro4s"                   %% "avro4s-json"           % Version.avro4s
    val circe                = "io.circe"                              %% "circe-core"            % Version.circe
    val circeGeneric         = "io.circe"                              %% "circe-generic"         % Version.circe
    val circeParser          = "io.circe"                              %% "circe-parser"          % Version.circe
    val jacksonModuleScala   = "com.fasterxml.jackson.module"          %% "jackson-module-scala"  % Version.jacksonModuleScala
    val json4sCore           = "org.json4s"                            %% "json4s-core"           % Version.json4s
    val json4sJackson        = "org.json4s"                            %% "json4s-jackson"        % Version.json4s
    val json4sNative         = "org.json4s"                            %% "json4s-native"         % Version.json4s
    val jsoniterScalaCore    = "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core"   % Version.jsoniterScala
    val jsoniterScalaMacros  = "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % Version.jsoniterScala
    val ninny                = "tk.nrktkt"                             %% "ninny"                 % Version.ninny
    val playJson             = "org.playframework"                     %% "play-json"             % Version.play
    val scalaTest            = "org.scalatest"                         %% "scalatest"             % Version.scalaTest
    val upickle              = "com.lihaoyi"                           %% "upickle"               % Version.upickle
    val zioJson              = "dev.zio"                               %% "zio-json"              % Version.zioJson
    // format: on
  }
