package temp;

import aws.CredentialsFetch;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.Instance;
import infrastructure.instances.encoder.EncoderInstance;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class Temp2Core {

    private static AmazonEC2 ec2Client;

    public static void main(String args[]) {
        AWSCredentialsProvider cp = CredentialsFetch.getCredentialsProvider();

        ec2Client = AmazonEC2ClientBuilder.standard()
                .withRegion(Regions.EU_CENTRAL_1)
                .withCredentials(cp)
                .build();

        EncoderInstance.start(cp, "encoder-bucket-eyfzgpfavfxzpv5eigihs8ofeu9vm40rztrgkgzgr0k", "rabbitmq-cluster-loadbalancer-528595232.eu-central-1.elb.amazonaws.com", 1);

    }

}
