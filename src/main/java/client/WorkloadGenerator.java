package client;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class WorkloadGenerator {

    public WorkloadGenerator(String nrOfClients, String timeInterval,String bucketName, String queueURL, String filePath) throws IOException, TimeoutException {

        int clients = Integer.parseInt(nrOfClients);
        int time = Integer.parseInt(timeInterval);


        for(int i=0; i<clients;i++) {
            ClientCore core = new ClientCore(bucketName,queueURL,filePath,time);
            core.run();
        }

    }

    public static void main(String[] args) throws IOException, TimeoutException {

        if (args.length < 5) {
            System.out.println("-------------Invalid input-------------\n" +
                    "Expected args: [numberOfClients] [timeInterval] [bucketName] [queueURL] [fileToConvert/path]");
            System.exit(0);
        }

        new WorkloadGenerator(args[0], args[1],args[2], args[3],args[4]);

    }

}
