import $ivy.`software.amazon.awscdk:core:1.11.0.DEVPREVIEW`, software.amazon.awscdk.core._
import $ivy.`software.amazon.awscdk:ec2:1.11.0.DEVPREVIEW`, software.amazon.awscdk.services.ec2._
import $ivy.`software.amazon.awscdk:ecs:1.11.0.DEVPREVIEW`, software.amazon.awscdk.services.ecs._

import scala.jdk.CollectionConverters._

@main def compile(): Unit = println("Done")

@main def run(): Unit = {
  val app = new App()

  new DigdagServerStack(app, "digdag-server")

  app.synth()
}

class DigdagServerStack(scope: Construct, name: String) extends Stack(scope, name) {
  val vpc = {
    val props = {
      val subnetConf = SubnetConfiguration.builder()
        .name(s"$name-public-subnet")
        .subnetType(SubnetType.PUBLIC)
        .build()
      VpcProps.builder()
        .maxAzs(1)
        .subnetConfiguration(List(subnetConf).asJava)
        .build()
    }
    new Vpc(this, s"$name-vpc", props)
  }

  val cluster = new Cluster(this, s"$name-cluster", ClusterProps.builder().vpc(vpc).build())

  val digdagServerPort = 65432

  val service = {
    val taskDefinition = {
      val taskDef = {
        val props = FargateTaskDefinitionProps.builder().cpu(256).memoryLimitMiB(1024).build()
        new FargateTaskDefinition(this, s"$name-task-definition", props)
      }

      val containerOption = ContainerDefinitionOptions.builder()
        .image(ContainerImage.fromRegistry("myui/digdag-server"))
        .build()
      val container = taskDef.addContainer(s"$name-container", containerOption)
      container.addPortMappings(PortMapping.builder().containerPort(digdagServerPort).build())

      taskDef
    }

    val securityGroup = {
      val sgname = s"$name-service-security-group"
      val props = SecurityGroupProps.builder().vpc(vpc).securityGroupName(sgname).allowAllOutbound(true).build()
      val sg = new SecurityGroup(this, sgname, props)
      sg.addIngressRule(Peer.anyIpv4(), Port.tcp(digdagServerPort))
      sg
    }

    val serviceProps = FargateServiceProps.builder()
      .cluster(cluster)
      .desiredCount(1)
      .taskDefinition(taskDefinition)
      .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PUBLIC).build())
      .assignPublicIp(true)
      .securityGroup(securityGroup)
      .enableEcsManagedTags(true)
      .propagateTags(PropagatedTagSource.SERVICE)
      .build()

    new FargateService(this, s"$name-service", serviceProps)
  }
}
