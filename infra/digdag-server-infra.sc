import $ivy.`software.amazon.awscdk:core:1.11.0.DEVPREVIEW`, software.amazon.awscdk.core._
import $ivy.`software.amazon.awscdk:ec2:1.11.0.DEVPREVIEW`, software.amazon.awscdk.services.ec2._
import $ivy.`software.amazon.awscdk:ecs:1.11.0.DEVPREVIEW`, software.amazon.awscdk.services.ecs._
import $ivy.`software.amazon.awscdk:ecs-patterns:1.11.0.DEVPREVIEW`, software.amazon.awscdk.services.ecs.patterns._
import $ivy.`com.github.pureconfig::pureconfig-core:0.12.1`
import $ivy.`com.github.pureconfig::pureconfig-generic:0.12.1`

import pureconfig._, generic.auto._

case class DigdagServerStackConfig(serverPort: Int)

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

      val container = taskDef.addContainer(
        s"digdag-server-container",
        ContainerDefinitionOptions.builder().image(ContainerImage.fromRegistry("myui/digdag-server")).build()
      )
      container.addPortMappings(PortMapping.builder().containerPort(conf.serverPort).build())

      taskDef
    }

    val props = ApplicationLoadBalancedFargateServiceProps.builder()
      .cluster(cluster)
      .taskDefinition(taskDefinition)
      .publicLoadBalancer(true)
      .desiredCount(1)
      .enableEcsManagedTags(true)
      .propagateTags(PropagatedTagSource.SERVICE)
      .build()
    new ApplicationLoadBalancedFargateService(this, "digdag-server-fargate-service", props)
  }
}
