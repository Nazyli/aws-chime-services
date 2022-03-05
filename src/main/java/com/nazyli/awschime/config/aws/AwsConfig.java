package com.nazyli.awschime.config.aws;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.chime.AmazonChime;
import com.amazonaws.services.chime.AmazonChimeClientBuilder;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AwsConfig {
    @Value("${aws.credentials.accessKey}")
    private String amazonAWSAccessKey;

    @Value("${aws.credentials.secretKey}")
    private String amazonAWSSecretKey;

    @Value("${aws.s3.region}")
    private String region;

    @Bean
    public AmazonChime chimeClient() {
        return AmazonChimeClientBuilder.standard().withRegion(region)
                .withCredentials(
                        new AWSStaticCredentialsProvider(
                                amazonAWSCredentials()
                        )).build();
    }

    @Bean
    public AmazonS3 s3client() {
        return AmazonS3ClientBuilder.standard().withRegion(Regions.fromName(region))
                .withCredentials(
                        new AWSStaticCredentialsProvider(
                                amazonAWSCredentials()
                        )).build();
    }

    @Bean
    public AmazonCloudWatch cwClient() {
        return AmazonCloudWatchClientBuilder.standard().withRegion(Regions.fromName(region))
                .withCredentials(
                        new AWSStaticCredentialsProvider(
                                amazonAWSCredentials()
                        )).build();
    }

    @Bean
    public AWSCredentials amazonAWSCredentials() {
        return new BasicAWSCredentials(
                amazonAWSAccessKey, amazonAWSSecretKey);
    }

}
