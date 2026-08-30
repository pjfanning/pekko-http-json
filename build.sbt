// *****************************************************************************
// Build settings
// *****************************************************************************

inThisBuild(
  Seq(
    organization := "com.github.pjfanning",
    startYear    := Some(2023),
    licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
    homepage := Some(url("https://github.com/pjfanning/pekko-http-json")),
    scmInfo  := Some(
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
    scalaVersion       := "2.13.18",
    crossScalaVersions := Seq("2.13.18", "2.12.21"),
    scalacOptions ++= Seq(
      "-unchecked",
      "-deprecation",
      "-language:_",
      "-encoding",
      "UTF-8",
      "-Ywarn-unused:imports"
    ),
    scalafmtOnCompile := true,
    dynverSeparator   := "_" // the default `+` is not compatible with docker tags
  )
)

val withScala3 = Seq(
  crossScalaVersions += "3.3.8",
)

// fory-json-scala is only published for Scala 2.13 and Scala 3
val scala213AndScala3 = Seq(
  crossScalaVersions := Seq("2.13.18", "3.3.8"),
)

val targetJava8 = Seq(
  javacOptions ++= Seq(
    "--release",
    "8"
  ),
  scalacOptions ++= Seq(
    "-release:8"
  )
)

val targetJava17 = Seq(
  javacOptions ++= Seq(
    "--release",
    "17"
  ),
  scalacOptions ++= Seq(
    "-release:17"
  )
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
      `pekko-http-circe-base`,
      `pekko-http-fory-json-scala`,
      `pekko-http-jackson`,
      `pekko-http-jackson3`,
      `pekko-http-json4s`,
      `pekko-http-jsoniter-scala`,
      `pekko-http-jsoniter-scala-circe`,
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
    .settings(commonSettings, targetJava8, withScala3)
    .settings(
      libraryDependencies ++= Seq(
        Library.pekkoHttp,
        Library.argonaut,
        Library.pekkoStream % Provided,
        Library.scalaTest   % Test,
      )
    )

lazy val `pekko-http-circe` =
  project
    .settings(commonSettings, targetJava8, withScala3)
    .dependsOn(
      `pekko-http-circe-base` % "compile-internal->compile-internal;test-internal->test-internal"
    )
    .settings(
      // We don't want to publish pekko-http-circe-base, but want to have its classes available for users of
      // pekko-http-circe.
      Compile / packageBin / mappings ++= (`pekko-http-circe-base` / Compile / packageBin / mappings).value
    )
    .settings(
      libraryDependencies ++= Seq(
        Library.pekkoHttp,
        Library.circe,
        Library.circeParser,
        Library.pekkoStream  % Provided,
        Library.circeGeneric % Test,
        Library.scalaTest    % Test,
      )
    )

lazy val `pekko-http-circe-base` =
  project
    .settings(commonSettings, targetJava8, withScala3)
    .settings(publishArtifact := false)
    .settings(
      libraryDependencies ++= Seq(
        Library.pekkoHttp,
        Library.circe,
        Library.pekkoStream % Provided
      )
    )

lazy val `pekko-http-fory-json-scala` =
  project
    .settings(commonSettings, targetJava8, scala213AndScala3)
    .settings(
      libraryDependencies ++= Seq(
        Library.pekkoHttp,
        Library.foryJsonScala,
        Library.pekkoStream % Provided,
        Library.scalaTest   % Test,
      )
    )

lazy val `pekko-http-jackson` =
  project
    .settings(commonSettings, targetJava8, withScala3)
    .settings(
      libraryDependencies ++= Seq(
        Library.pekkoHttp,
        Library.jacksonModuleScala2,
        Library.pekkoStream              % Provided,
        Library.scalaTest                % Test,
        Library.jacksonModuleParamNames2 % Test
      )
    )

lazy val `pekko-http-jackson3` =
  project
    .settings(commonSettings, targetJava17, withScala3)
    .settings(
      libraryDependencies ++= Seq(
        Library.pekkoHttp,
        Library.jacksonModuleScala3,
        Library.pekkoStream % Provided,
        Library.scalaTest   % Test
      )
    )

lazy val `pekko-http-json4s` =
  project
    .settings(commonSettings, targetJava8, withScala3)
    .settings(
      libraryDependencies ++= Seq(
        Library.pekkoHttp,
        Library.json4sCore,
        Library.pekkoStream   % Provided,
        Library.json4sJackson % Test,
        Library.json4sNative  % Test,
        Library.scalaTest     % Test,
      )
    )

lazy val `pekko-http-jsoniter-scala` =
  project
    .settings(commonSettings, targetJava8, withScala3)
    .settings(
      libraryDependencies ++= Seq(
        Library.pekkoHttp,
        Library.jsoniterScalaCore,
        Library.pekkoStream         % Provided,
        Library.jsoniterScalaMacros % Test,
        Library.scalaTest           % Test,
      )
    )

lazy val `pekko-http-jsoniter-scala-circe` =
  project
    .settings(commonSettings, targetJava8, withScala3)
    .dependsOn(
      `pekko-http-circe-base` % "compile-internal->compile-internal;test-internal->test-internal"
    )
    .settings(
      // We don't want to publish pekko-http-circe-base, but want to have its classes available for users of
      // pekko-http-jsoniter-scala-circe.
      Compile / packageBin / mappings ++= (`pekko-http-circe-base` / Compile / packageBin / mappings).value
    )
    .settings(
      libraryDependencies ++= Seq(
        Library.pekkoHttp,
        Library.circe,
        Library.jsoniterScalaCore,
        Library.jsoniterScalaCirce,
        Library.pekkoStream  % Provided,
        Library.circeGeneric % Test,
        Library.scalaTest    % Test,
      )
    )

lazy val `pekko-http-ninny` =
  project
    .settings(commonSettings, targetJava8, withScala3)
    .settings(
      libraryDependencies ++= Seq(
        Library.pekkoHttp,
        Library.ninny,
        Library.pekkoStream % Provided,
        Library.scalaTest   % Test,
      )
    )

lazy val `pekko-http-play-json` =
  project
    .settings(commonSettings, targetJava17, withScala3)
    .settings(
      libraryDependencies ++= Seq(
        Library.pekkoHttp,
        Library.playJson,
        Library.pekkoStream % Provided,
        Library.scalaTest   % Test,
      )
    )

lazy val `pekko-http-upickle` =
  project
    .settings(commonSettings, targetJava8, withScala3)
    .settings(
      libraryDependencies ++= Seq(
        Library.pekkoHttp,
        Library.upickle,
        Library.pekkoStream % Provided,
        Library.scalaTest   % Test,
      )
    )

lazy val `pekko-http-avro4s` =
  project
    .settings(commonSettings, targetJava8)
    .settings(
      libraryDependencies ++= Seq(
        Library.pekkoHttp,
        Library.avro4sJson,
        Library.pekkoStream % Provided,
        Library.scalaTest   % Test,
      )
    )

lazy val `pekko-http-zio-json` =
  project
    .settings(commonSettings, withScala3)
    .settings(
      libraryDependencies ++= Seq(
        Library.pekkoHttp,
        Library.zioJson,
        Library.pekkoStream % Provided,
        Library.scalaTest   % Test
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
