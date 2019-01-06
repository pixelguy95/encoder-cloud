package infrastructure;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import java.security.SecureRandom;
import java.util.Base64;

public class S3BucketSetup {

    public static String create(AWSCredentialsProvider cp) {

        AmazonS3 s3Client = AmazonS3ClientBuilder.standard().withCredentials(cp).withRegion(Regions.EU_CENTRAL_1).build();

        byte[] randBytes = new byte[32];
        new SecureRandom().nextBytes(randBytes);
        String bucketName = "encoder-bucket-" + Base64.getUrlEncoder().withoutPadding().encodeToString(randBytes).toLowerCase().replace("_", "");
        s3Client.createBucket(bucketName);

        return bucketName;
    }
}
