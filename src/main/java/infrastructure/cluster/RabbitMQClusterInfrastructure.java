package infrastructure.cluster;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClientBuilder;
import com.amazonaws.services.autoscaling.model.*;
import com.amazonaws.services.autoscaling.model.Tag;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClientBuilder;
import com.amazonaws.services.elasticloadbalancing.model.*;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.amazonaws.services.identitymanagement.model.InstanceProfile;
import infrastructure.iam.InstanceProfileCreator;
import infrastructure.instances.manager.ManagerInstance;
import infrastructure.securitygroup.SecurityGroupCreator;

import java.util.*;

public class RabbitMQClusterInfrastructure {

    public static final String RABBITMQ_SECURITYGROUP = "rabbitmq-securitygroup";
    public static final String RABBITMQ_CLUSTER_LOADBALANCER = "rabbitmq-cluster-cluster";
    public static final String RABBITMQ_CLUSTER_INSTANCE_LAUNCH_CONFIG = "rabbitmq-cluster-instance-launch-config";
    public static final String RABBITMQ_CLUSTER_AUTOSCALING_GROUP = "rabbitmq-cluster-autoscaling-group";

    public static void create(AWSCredentialsProvider cp) {
        AmazonElasticLoadBalancing loadbalancerClient = AmazonElasticLoadBalancingClientBuilder
                .standard()
                .withCredentials(cp)
                .withRegion(Regions.EU_CENTRAL_1)
                .build();

        AmazonEC2 ec2Client = AmazonEC2ClientBuilder.standard()
                .withRegion(Regions.EU_CENTRAL_1)
                .withCredentials(cp)
                .build();

        AmazonAutoScaling autoScaleClient = AmazonAutoScalingClientBuilder
                .standard()
                .withCredentials(cp)
                .withRegion(Regions.EU_CENTRAL_1)
                .build();

        AmazonIdentityManagement aim = AmazonIdentityManagementClientBuilder
                .standard()
                .withCredentials(cp)
                .withRegion(Regions.EU_CENTRAL_1)
                .build();

        System.out.println("Creating security group for rabbitmq");
        //Create security group for rabbitmq-cluster-instances and the load-balancer
        SecurityGroup sg = createSecurityGroup(ec2Client);

        System.out.println("Creating load balancer");
        //Creating load balancer with security group and correct listeners
        createLoadBalancer(loadbalancerClient, sg);

        System.out.println("Set cross-zone load balancing");
        //Updating attributes of load balancer
        updateAttributeOnLoadBalancer(loadbalancerClient);

        //Creating AutoScale launch-configurations
        System.out.println("Creating auto-scale launch configurations");
        createLaunchConfiguration(autoScaleClient, aim);

        //Creating new auto-scale group
        System.out.println("Creating autoscale group");
        createAutoscaleGroup(autoScaleClient);

        //Attach cluster to
        System.out.println("Attaching cluster to auto-scale group");
        attachLoadbalancerToAutoscaleGroup(autoScaleClient);


        System.out.println("DONE, might take approx 2 mins before the cluster is finished");
        System.out.println("Loadbalancer DNS: ");
        System.out.println("http://" + getLoadBalancerDNSName(loadbalancerClient) + ":15672");
    }

    private static String getLoadBalancerDNSName(AmazonElasticLoadBalancing loadbalancerClient) {
        DescribeLoadBalancersRequest dlbr = new DescribeLoadBalancersRequest();
        dlbr.setLoadBalancerNames(Arrays.asList(RABBITMQ_CLUSTER_LOADBALANCER));
        return loadbalancerClient.describeLoadBalancers(dlbr).getLoadBalancerDescriptions().get(0).getDNSName();
    }

    private static void attachLoadbalancerToAutoscaleGroup(AmazonAutoScaling autoScaleClient) {
        AttachLoadBalancersRequest albr = new AttachLoadBalancersRequest();
        albr.setAutoScalingGroupName(RABBITMQ_CLUSTER_AUTOSCALING_GROUP);
        albr.setLoadBalancerNames(Arrays.asList(RABBITMQ_CLUSTER_LOADBALANCER));
        autoScaleClient.attachLoadBalancers(albr);
    }

    private static void createAutoscaleGroup(AmazonAutoScaling autoScaleClient) {
        CreateAutoScalingGroupRequest casgr = new CreateAutoScalingGroupRequest();
        casgr.setAutoScalingGroupName(RABBITMQ_CLUSTER_AUTOSCALING_GROUP);
        casgr.setAvailabilityZones(Arrays.asList("eu-central-1a", "eu-central-1b", "eu-central-1c"));
        casgr.setLaunchConfigurationName(RABBITMQ_CLUSTER_INSTANCE_LAUNCH_CONFIG);
        casgr.setMaxSize(2);
        casgr.setMinSize(2);
        casgr.setDesiredCapacity(2);
        casgr.setTags(Arrays.asList(new Tag().withKey("RabbitMQ cluster node")));
        try {
            autoScaleClient.createAutoScalingGroup(casgr);
        } catch (AlreadyExistsException e) {

        }
    }

    private static void createLaunchConfiguration(AmazonAutoScaling autoScaleClient, AmazonIdentityManagement aim) {
        CreateLaunchConfigurationRequest clcr = new CreateLaunchConfigurationRequest();
        String userData = new Scanner(ManagerInstance.class.getResourceAsStream("/rabbitmq-cluster-instance-v2.yml"), "UTF-8").useDelimiter("\\A").next();
        clcr.setUserData(Base64.getEncoder().encodeToString(userData.getBytes()));
        clcr.setLaunchConfigurationName(RABBITMQ_CLUSTER_INSTANCE_LAUNCH_CONFIG);
        clcr.setInstanceType("t2.micro");
        clcr.setImageId("ami-0bdf93799014acdc4");
        clcr.setKeyName("school"); //CJ key TODO: change
        clcr.setSecurityGroups(Arrays.asList(RABBITMQ_SECURITYGROUP));

        InstanceProfile instanceProfile = createInstanceProfile(aim);

        clcr.setIamInstanceProfile(instanceProfile.getArn());
        try {
            autoScaleClient.createLaunchConfiguration(clcr);
        } catch (AlreadyExistsException e) {

        }
    }

    private static InstanceProfile createInstanceProfile(AmazonIdentityManagement aim) {
        List<String> policies = Arrays.asList("arn:aws:iam::aws:policy/AutoScalingReadOnlyAccess", "arn:aws:iam::aws:policy/AmazonEC2ReadOnlyAccess");
        return InstanceProfileCreator.create(aim, "rabbitmq-cluster-role-v1", "rabbitmq-cluster-instance-profile-v1", policies);
    }

    private static void updateAttributeOnLoadBalancer(AmazonElasticLoadBalancing loadbalancerClient) {
        ModifyLoadBalancerAttributesRequest m = new ModifyLoadBalancerAttributesRequest();
        LoadBalancerAttributes attrib = new LoadBalancerAttributes();
        attrib.setCrossZoneLoadBalancing(new CrossZoneLoadBalancing().withEnabled(true));
        m.setLoadBalancerAttributes(attrib);
        m.setLoadBalancerName(RABBITMQ_CLUSTER_LOADBALANCER);
        loadbalancerClient.modifyLoadBalancerAttributes(m);
    }

    private static void createLoadBalancer(AmazonElasticLoadBalancing loadbalancerClient, SecurityGroup sg) {
        CreateLoadBalancerRequest clbr = new CreateLoadBalancerRequest();
        clbr.setLoadBalancerName(RABBITMQ_CLUSTER_LOADBALANCER);
        clbr.setSecurityGroups(Arrays.asList(sg.getGroupId()));

        Listener l1 = new Listener();
        l1.setInstancePort(5672);
        l1.setLoadBalancerPort(5672);
        l1.setProtocol("TCP");
        l1.setInstanceProtocol("TCP");

        Listener l2 = new Listener();
        l2.setInstancePort(15672);
        l2.setLoadBalancerPort(15672);
        l2.setProtocol("TCP");
        l2.setInstanceProtocol("TCP");

        clbr.setListeners(Arrays.asList(l1, l2));
        clbr.setAvailabilityZones(Arrays.asList("eu-central-1a", "eu-central-1b", "eu-central-1c"));

        loadbalancerClient.createLoadBalancer(clbr);
    }

    private static SecurityGroup createSecurityGroup(AmazonEC2 ec2Client) {
        List<SecurityGroupCreator.PortRange> ports = Arrays.asList(
                new SecurityGroupCreator.PortRange(22, 22),
                new SecurityGroupCreator.PortRange(4369, 4369),
                new SecurityGroupCreator.PortRange(8883, 8883),
                new SecurityGroupCreator.PortRange(5671, 5672),
                new SecurityGroupCreator.PortRange(1883, 1883),
                new SecurityGroupCreator.PortRange(35672, 35682),
                new SecurityGroupCreator.PortRange(61613, 61614),
                new SecurityGroupCreator.PortRange(25672, 25672),
                new SecurityGroupCreator.PortRange(15675, 15675),
                new SecurityGroupCreator.PortRange(25672, 45672),
                new SecurityGroupCreator.PortRange(15674, 15674),
                new SecurityGroupCreator.PortRange(15672, 15672));

        return SecurityGroupCreator.create(ec2Client, RABBITMQ_SECURITYGROUP, ports);
    }


}
