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
import infrastructure.instances.manager.ManagerInstance;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class TempCore {


    public static void main(String args[]) {
        AWSCredentialsProvider cp = CredentialsFetch.getCredentialsProvider();

        AmazonCloudWatch cloudWatchCLient = AmazonCloudWatchClientBuilder
                .standard()
                .withCredentials(cp)
                .withRegion(Regions.EU_CENTRAL_1)
                .build();

        while(true) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            GetMetricStatisticsRequest gmdr = new GetMetricStatisticsRequest();
            gmdr.setMetricName("CPUUtilization");
            gmdr.setStatistics(Arrays.asList("Maximum"));
            gmdr.setDimensions(Arrays.asList(new Dimension().withName("InstanceId").withValue("i-02d24de703f1f592a")));
            Calendar c = Calendar.getInstance();
            c.add(Calendar.SECOND, -3*60);
            gmdr.setStartTime(c.getTime());
            gmdr.setPeriod(60);
            gmdr.setEndTime(Calendar.getInstance().getTime());
            gmdr.setNamespace("AWS/EC2");
            GetMetricStatisticsResult res = cloudWatchCLient.getMetricStatistics(gmdr);

            List<Datapoint> sorted = res.getDatapoints().stream().sorted(Comparator.comparing(Datapoint::getTimestamp)).collect(Collectors.toList());

            for(Datapoint d : sorted) {
                System.out.println(d.getTimestamp().toGMTString() + " " + d.getMaximum() + " " + d.getUnit());
            }
        }
    }
}
