package infrastructure.loadbalancer;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClientBuilder;
import com.amazonaws.services.elasticloadbalancing.model.*;

import java.util.Arrays;
import java.util.Collections;

public class RabbitMQLoadBalancer {

    public static final String RABBITMQ_SECURITYGROUP = "rabbitmq-securitygroup";
    public static final String RABBITMQ_CLUSTER_LOADBALANCER = "rabbitmq-cluster-loadbalancer";

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

        System.out.println("Creating security group for rabbitmq");
        //Create security group for rabbitmq-cluster-instances and the load-balancer
        try {
            CreateSecurityGroupRequest csgr = new CreateSecurityGroupRequest();
            csgr.setGroupName(RABBITMQ_SECURITYGROUP);
            csgr.setDescription("The inbound and outbound ports for a rabbitmq instance");
            ec2Client.createSecurityGroup(csgr);
        } catch (AmazonEC2Exception e) {
            System.out.println("Security group rabbitmq-securitygroup already exists");
        }

        System.out.println("Adding inboud ports to whitelist");
        //Adding inbound ports for security group
        authorizeSecurityGroupIngress(ec2Client, 22, 22, RABBITMQ_SECURITYGROUP);           //SSH
        authorizeSecurityGroupIngress(ec2Client, 4369, 4369, RABBITMQ_SECURITYGROUP);
        authorizeSecurityGroupIngress(ec2Client, 8883, 8883, RABBITMQ_SECURITYGROUP);
        authorizeSecurityGroupIngress(ec2Client, 5671, 5672, RABBITMQ_SECURITYGROUP);
        authorizeSecurityGroupIngress(ec2Client, 1883, 1883, RABBITMQ_SECURITYGROUP);
        authorizeSecurityGroupIngress(ec2Client, 35672, 35682, RABBITMQ_SECURITYGROUP);
        authorizeSecurityGroupIngress(ec2Client, 61613, 61614, RABBITMQ_SECURITYGROUP);
        authorizeSecurityGroupIngress(ec2Client, 25672, 25672, RABBITMQ_SECURITYGROUP);
        authorizeSecurityGroupIngress(ec2Client, 15675, 15675, RABBITMQ_SECURITYGROUP);
        authorizeSecurityGroupIngress(ec2Client, 25672, 45672, RABBITMQ_SECURITYGROUP);
        authorizeSecurityGroupIngress(ec2Client, 15674, 15674, RABBITMQ_SECURITYGROUP);
        authorizeSecurityGroupIngress(ec2Client, 15672, 15672, RABBITMQ_SECURITYGROUP);

        System.out.println("Fetching security group id");
        //Fetch security group id
        DescribeSecurityGroupsRequest dsgrr = new DescribeSecurityGroupsRequest();
        dsgrr.setGroupNames(Arrays.asList(RABBITMQ_SECURITYGROUP));
        String securityGroupID = ec2Client.describeSecurityGroups(dsgrr).getSecurityGroups().get(0).getGroupId();

        System.out.println("Creating load balancer");
        //Creating load balancer with security group and correct listeners
        CreateLoadBalancerRequest clbr = new CreateLoadBalancerRequest();
        clbr.setLoadBalancerName(RABBITMQ_CLUSTER_LOADBALANCER);
        clbr.setSecurityGroups(Arrays.asList(securityGroupID));

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

        System.out.println("Set cross-zone load balancing");
        //Updating attributes of load balancer
        ModifyLoadBalancerAttributesRequest m = new ModifyLoadBalancerAttributesRequest();
        LoadBalancerAttributes attrib = new LoadBalancerAttributes();
        attrib.setCrossZoneLoadBalancing(new CrossZoneLoadBalancing().withEnabled(true));
        m.setLoadBalancerAttributes(attrib);
        m.setLoadBalancerName(RABBITMQ_CLUSTER_LOADBALANCER);
        loadbalancerClient.modifyLoadBalancerAttributes(m);
    }

    public static void authorizeSecurityGroupIngress(AmazonEC2 ec2Client, int start, int end, String securityGroupName) {
        try {
            AuthorizeSecurityGroupIngressRequest asgir = new AuthorizeSecurityGroupIngressRequest();
            asgir.setCidrIp("0.0.0.0/0");
            asgir.setIpProtocol("TCP");
            asgir.setFromPort(start);
            asgir.setToPort(end);
            asgir.setGroupName(securityGroupName);
            ec2Client.authorizeSecurityGroupIngress(asgir);
        } catch (AmazonEC2Exception e) {

        }
    }

}
