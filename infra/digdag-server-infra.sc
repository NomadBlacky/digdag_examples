import $ivy.`software.amazon.awscdk:core:1.11.0.DEVPREVIEW`, software.amazon.awscdk.core._
import $ivy.`software.amazon.awscdk:ec2:1.11.0.DEVPREVIEW`, software.amazon.awscdk.services.ec2._
import $ivy.`software.amazon.awscdk:ecs:1.11.0.DEVPREVIEW`, software.amazon.awscdk.services.ecs._
import $ivy.`com.github.pureconfig::pureconfig-core:0.12.1`
import $ivy.`com.github.pureconfig::pureconfig-generic:0.12.1`

import scala.jdk.CollectionConverters._

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
  val vpc = {
    val props = {
      val subnetConf = SubnetConfiguration.builder()
        .name(s"$stackName-public-subnet")
        .subnetType(SubnetType.PUBLIC)
        .build()
      VpcProps.builder()
        .maxAzs(1)
        .subnetConfiguration(List(subnetConf).asJava)
        .build()
    }
    new Vpc(this, s"$stackName-vpc", props)
  }

  val cluster = new Cluster(this, s"$stackName-cluster", ClusterProps.builder().vpc(vpc).build())

  val service = new DigdagServerFagateService(this, DigdagServerServiceProp(vpc, cluster, conf.serverPort))
}

case class DigdagServerServiceProp(vpc: Vpc, cluster: Cluster, serverPort: Int)

class DigdagServerFagateService(scope: Construct, prop: DigdagServerServiceProp)
  extends Construct(scope, "digdag-server-fagate-service") {

  val taskDefinition = {
    val taskDef = {
      val props = FargateTaskDefinitionProps.builder().cpu(256).memoryLimitMiB(1024).build()
      new FargateTaskDefinition(this, s"task-definition", props)
    }

    val containerOption = ContainerDefinitionOptions.builder()
      .image(ContainerImage.fromRegistry("myui/digdag-server"))
      .build()
    val container = taskDef.addContainer(s"container", containerOption)
    container.addPortMappings(PortMapping.builder().containerPort(prop.serverPort).build())

    taskDef
  }

  val securityGroup = {
    val sgname = s"security-group"
    val props = SecurityGroupProps.builder().vpc(prop.vpc).securityGroupName(sgname).allowAllOutbound(true).build()
    val sg = new SecurityGroup(this, sgname, props)
    sg.addIngressRule(Peer.anyIpv4(), Port.tcp(prop.serverPort))
    sg
  }

  val serviceProps = FargateServiceProps.builder()
    .cluster(prop.cluster)
    .desiredCount(1)
    .taskDefinition(taskDefinition)
    .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PUBLIC).build())
    .assignPublicIp(true)
    .securityGroup(securityGroup)
    .enableEcsManagedTags(true)
    .propagateTags(PropagatedTagSource.SERVICE)
    .build()

  new FargateService(this, s"service", serviceProps)
}
