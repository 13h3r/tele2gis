resolvers ++= Seq(
  "casino-plugins-releases" at "http://artifactory.billing.test:8081/artifactory/sbt-plugins-release-local",
  "casino-plugins-snapshots" at "http://artifactory.billing.test:8081/artifactory/sbt-plugins-snapshot-local"
)

//addSbtPlugin("ru.dgis.casino" %% "casino-sbt-plugin" % "0.13.0-SNAPSHOT")

addSbtPlugin("ru.dgis.casino" %% "sbt-build-info" % "0.2.0")

addSbtPlugin("com.gilt" % "sbt-dependency-graph-sugar" % "0.7.4")

addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.3.0")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.3")
