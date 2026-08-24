package com.sat.lms.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws")
public class AwsProperties {
    private String region = "ap-northeast-2";
    private final S3 s3 = new S3();

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public S3 getS3() { return s3; }

    public static class S3 {
        private String bucket = "";
        private long presignedExpirationMinutes = 5;

        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
        public long getPresignedExpirationMinutes() { return presignedExpirationMinutes; }
        public void setPresignedExpirationMinutes(long presignedExpirationMinutes) {
            this.presignedExpirationMinutes = presignedExpirationMinutes;
        }
    }
}
