import $ivy.`software.amazon.awscdk:core:1.15.0.DEVPREVIEW`, software.amazon.awscdk.core._
import $ivy.`software.amazon.awscdk:ec2:1.15.0.DEVPREVIEW`, software.amazon.awscdk.services.ec2._
import $ivy.`software.amazon.awscdk:ecs:1.15.0.DEVPREVIEW`, software.amazon.awscdk.services.ecs._
import $ivy.`software.amazon.awscdk:ecs-patterns:1.15.0.DEVPREVIEW`, software.amazon.awscdk.services.ecs.patterns._
import $ivy.`software.amazon.awscdk:ecr-assets:1.15.0.DEVPREVIEW`, software.amazon.awscdk.services.ecr.assets._
import $ivy.`com.github.pureconfig::pureconfig-core:0.12.1`
import $ivy.`com.github.pureconfig::pureconfig-generic:0.12.1`

import pureconfig._, generic.auto._

import scala.jdk.CollectionConverters._

case class DigdagServerStackConfig(
  serverPort: Int,
  datadogApiKey: String,
  desiredCount: Int
)

@main def compile(): Unit = println("Done")

@main def run(configFile: os.Path): Unit = {
  val app = new App()

  val conf = ConfigSource.file(configFile.toNIO).loadOrThrow[DigdagServerStackConfig]
  new DigdagServerStack(app, "digdag-server", conf)

  app.synth()
}

class DigdagServerStack(scope: Construct, stackName: String, conf: DigdagServerStackConfig) extends Stack(scope, stackName) {
  val vpc = new Vpc(this, s"$stackName-vpc", VpcProps.builder().maxAzs(2).build())

  val cluster = new Cluster(this, s"$stackName-cluster", ClusterProps.builder().vpc(vpc).build())

  val digdagService = {
    val taskDefinition = {
      val props = FargateTaskDefinitionProps.builder().cpu(256).memoryLimitMiB(1024).build()
      val taskDef = new FargateTaskDefinition(this, s"task-definition", props)

      { // Digdag Container
        val jmxPort = 9010
        val jvmOpts = List(
          "-Djava.rmi.server.hostname=127.0.0.1",
          s"-Dcom.sun.management.jmxremote.rmi.port=$jmxPort",
          "-Dcom.sun.management.jmxremote.ssl=false",
          "-Dcom.sun.management.jmxremote.local.only=false",
          "-Dcom.sun.management.jmxremote.authenticate=false",
          "-Dcom.sun.management.jmxremote",
          s"-Dcom.sun.management.jmxremote.port=$jmxPort"
        ).mkString(" ")
        val digContainerOptions = ContainerDefinitionOptions.builder()
          .image(ContainerImage.fromAsset("./docker/digdag/"))
          .environment(Map("JAVA_TOOL_OPTIONS" -> jvmOpts).asJava)
          .build()
        val container = taskDef.addContainer(s"digdag-server-container", digContainerOptions)

        container.addPortMappings(PortMapping.builder().containerPort(conf.serverPort).build())
        container.addPortMappings(PortMapping.builder().containerPort(jmxPort).build())
      }

      { // Datadog Container
        val dogContainerOptions = ContainerDefinitionOptions.builder()
          .image(ContainerImage.fromAsset("./docker/datadog/"))
          .cpu(10)
          .memoryLimitMiB(256)
          .environment(Map("DD_API_KEY" -> conf.datadogApiKey, "ECS_FARGATE" -> "true").asJava)
          .build()
        taskDef.addContainer("datadog-container", dogContainerOptions)
      }

      taskDef
    }

    val props = ApplicationLoadBalancedFargateServiceProps.builder()
      .cluster(cluster)
      .taskDefinition(taskDefinition)
      .publicLoadBalancer(true)
      .desiredCount(conf.desiredCount)
      .enableEcsManagedTags(true)
      .propagateTags(PropagatedTagSource.SERVICE)
      .build()
    new ApplicationLoadBalancedFargateService(this, "digdag-server-fargate-service", props)
  }
}
