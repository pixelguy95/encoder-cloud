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
import infrastructure.instances.manager.ManagerInstance;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class TempCore {

    private static AmazonEC2 ec2Client;

    public static void main(String args[]) {
        AWSCredentialsProvider cp = CredentialsFetch.getCredentialsProvider();

        ec2Client = AmazonEC2ClientBuilder.standard()
                .withRegion(Regions.EU_CENTRAL_1)
                .withCredentials(cp)
                .build();

        AmazonCloudWatch cloudWatchCLient = AmazonCloudWatchClientBuilder
                .standard()
                .withCredentials(cp)
                .withRegion(Regions.EU_CENTRAL_1)
                .build();

        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
        int encoders = 0;

        while(true) {

            String dataLine = time;
            dataLine = dataLine + "," + encoders;

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            List<Instance> allEncoders = getInstancesWithTag("encoder");
            double sum = 0.0;
            for(Instance encoder : allEncoders) {
                GetMetricStatisticsRequest gmdr = new GetMetricStatisticsRequest();
                gmdr.setMetricName("CPUUtilization");
                gmdr.setStatistics(Arrays.asList("Maximum"));
                gmdr.setDimensions(Arrays.asList(new Dimension().withName("InstanceId").withValue(encoder.getInstanceId())));
                Calendar c = Calendar.getInstance();
                c.add(Calendar.SECOND, -4*60);
                gmdr.setStartTime(c.getTime());
                gmdr.setPeriod(60);
                gmdr.setEndTime(Calendar.getInstance().getTime());
                gmdr.setNamespace("AWS/EC2");
                GetMetricStatisticsResult res = cloudWatchCLient.getMetricStatistics(gmdr);

                if(res.getDatapoints().size() == 0) {
                    System.out.println("No data yet");
                    System.out.println(encoder.getInstanceId());
                    break;
                }



                Datapoint latest = res.getDatapoints().stream().sorted(Comparator.comparing(Datapoint::getTimestamp)).collect(Collectors.toList()).get(res.getDatapoints().size()-1);
                sum += latest.getMaximum();
            }

            dataLine = dataLine + "," + sum;
            System.out.println(dataLine);

            time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
            encoders = 0;
        }
    }


    public static List<Instance> getInstancesWithTag(String startsWith) {

        List<Instance> all = new ArrayList<>();
        try {
            ec2Client.describeInstances().getReservations()
                    .forEach(reservation -> reservation.getInstances().stream().filter(i -> i.getState().getCode() == 16)
                            .forEach(instance -> instance.getTags().forEach(tag -> {
                                if (tag.getValue().startsWith(startsWith)) {
                                    all.add(instance);
                                }
                            })));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return all;
    }
}
