package aws;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.PropertiesCredentials;

import java.io.IOException;

public class CredentialsFetch {

    private static final String CREDENTIALS_FILE = "/AwsCredentials.properties";

    public static AWSCredentialsProvider getCredentialsProvider() {

        AWSCredentials credentials = null;
        try {
            credentials = new PropertiesCredentials(CredentialsFetch.class.getResourceAsStream(CREDENTIALS_FILE));
        } catch (IOException e) {
            e.printStackTrace();
        }

        final AWSCredentials finalCredentials = credentials;
        return new AWSCredentialsProvider() {
            @Override
            public AWSCredentials getCredentials() {
                return finalCredentials;
            }

            @Override
            public void refresh() {

            }
        };
    }
}
