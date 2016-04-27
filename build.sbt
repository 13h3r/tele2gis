import sbt._

lazy val bottele = project in file(".")

//casino.Assembly.settings

val metricsV = "3.0.2"

val akkaV = "2.4.4"

scalaVersion := "2.11.8"

resolvers += Resolver.jcenterRepo

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-testkit" % akkaV,
  "com.typesafe.akka" %% "akka-http-core" % akkaV,
  "com.typesafe.akka" %% "akka-http-experimental" % akkaV,
  "com.typesafe.akka" %% "akka-slf4j" % akkaV,
  "com.typesafe.akka" %% "akka-http-experimental" % akkaV,
  "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaV,
  "io.dropwizard.metrics" % "metrics-core" % "3.1.2",
  "io.dropwizard.metrics" % "metrics-graphite" % "3.1.2",
  "org.scalactic" %% "scalactic" % "2.2.6"
)

assemblyMergeStrategy in assembly <<= (assemblyMergeStrategy in assembly) { old => {
  case "application.conf" => MergeStrategy.concat
  case PathList("org", "apache", xs @ _*) => MergeStrategy.last
  case x => old(x)
}}

enablePlugins(DockerPlugin)

dockerfile in docker := {
  val artifact: File = assembly.value
  val base: File = baseDirectory.value

  val artifactTargetPath = s"/app/${artifact.name}"

  new Dockerfile {
    from("frolvlad/alpine-oraclejdk8")
    add(artifact, artifactTargetPath)
    entryPoint("sh", "-c", s"java $$JAVA_D_OPTIONS -cp $artifactTargetPath $$MAIN_CLASS")
  }
}

buildOptions in docker := BuildOptions(
  removeIntermediateContainers = BuildOptions.Remove.Always
)

imageNames in docker := Seq(
  // Sets the latest tag
  ImageName(s"13h3r/bottele:latest")
)
