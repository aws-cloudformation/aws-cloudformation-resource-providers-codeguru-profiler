package software.amazon.codeguruprofiler.profilinggroup;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

// A few tests for the regular expressions included in the aws-codeguruprofiler-profilinggroup.json file
//
// All AWS Regions and their partitions: https://tiny.amazon.com/nvhuvr9j (Internal Amazon Link)
public class ValidationPatternsTest {
    @Nested
    class DescribeProfilingGroupArnPattern {
        // NOTE: This should be kept in sync with ProfilingGroupArn in aws-codeguruprofiler-profilinggroup.json
        // See https://docs.aws.amazon.com/codeguru/latest/profiler-ug/security_iam_service-with-iam.html
        private Pattern pattern = Pattern.compile("^arn:aws([-\\w]*):codeguru-profiler:(([a-z]+-)+[0-9]+):([0-9]{12}):profilingGroup/[^.]+$");

        @Test
        public void itAcceptsACorrectProfilingGroupArn() {
            assertThat("arn:aws:codeguru-profiler:us-iso-east-1:123456789012:profilingGroup/my-profiling-group").matches(pattern);
        }

        @Test
        public void itAcceptsAllPartitions() {
            assertThat("arn:aws-cn:codeguru-profiler:cn-north-1:123456789012:profilingGroup/my-profiling-group").matches(pattern);
            assertThat("arn:aws-iso:codeguru-profiler:us-iso-east-1:123456789012:profilingGroup/my-profiling-group").matches(pattern);
            assertThat("arn:aws-iso-b:codeguru-profiler:us-isob-east-1:123456789012:profilingGroup/my-profiling-group").matches(pattern);
            assertThat("arn:aws-us-gov:codeguru-profiler:us-gov-east-1:123456789012:profilingGroup/my-profiling-group").matches(pattern);
        }

        @Test
        public void itDoesNotAcceptSomethingOtherThanAProfilingGroupArn() {
            assertThat("arn:aws:codeguru-profiler:us-east-2:123456789012:NOTVALID/my-profiling-group").doesNotMatch(pattern);
        }
    }

    @Nested
    class DescribeIamArnPattern {
        // NOTE: This should be kept in sync with IamArn in aws-codeguruprofiler-profilinggroup.json
        // See https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_identifiers.html#identifiers-arns
        private Pattern pattern = Pattern.compile("^arn:aws([-\\w]*):iam::([0-9]{12}):[^.]+$");

        @Test
        public void itAcceptsACorrectIamArn() {
            assertThat("arn:aws:iam::123456789012:group/division_abc/subdivision_xyz/product_A/Developers").matches(pattern);
        }

        @Test
        public void itAcceptsAllPartitions() {
            assertThat("arn:aws-cn:iam::123456789012:group/division_abc/subdivision_xyz/product_A/Developers").matches(pattern);
            assertThat("arn:aws-iso:iam::123456789012:group/division_abc/subdivision_xyz/product_A/Developers").matches(pattern);
            assertThat("arn:aws-iso-b:iam::123456789012:group/division_abc/subdivision_xyz/product_A/Developers").matches(pattern);
            assertThat("arn:aws-us-gov:iam::123456789012:group/division_abc/subdivision_xyz/product_A/Developers").matches(pattern);
        }

        @Test
        public void itDoesNotAcceptSomethingOtherThanAIamArn() {
            assertThat("arn:aws:notiam::123456789012:group/division_abc/subdivision_xyz/product_A/Developers").doesNotMatch(pattern);
        }
    }

    @Nested
    class DescribeChannelUriPattern {
        // NOTE: This should be kept in sync with ChannelUri in aws-codeguruprofiler-profilinggroup.json
        // See https://docs.aws.amazon.com/general/latest/gr/aws-arns-and-namespaces.html
        private Pattern pattern = Pattern.compile("^arn:aws([-\\w]*):[a-z-]+:(([a-z]+-)+[0-9]+)?:([0-9]{12}):[^.]+$");

        @Test
        public void itAcceptsACorrectArn() {
            assertThat("arn:aws:sns:us-east-1:123456789012:mytopic").matches(pattern);
        }

        @Test
        public void itAcceptsAllPartitions() {
            assertThat("arn:aws-cn:sns:cn-north-1:123456789012:mytopic").matches(pattern);
            assertThat("arn:aws-iso:sns:us-iso-east-1:123456789012:mytopic").matches(pattern);
            assertThat("arn:aws-iso-b:sns:us-isob-east-1:123456789012:mytopic").matches(pattern);
            assertThat("arn:aws-us-gov:sns:us-gov-east-1:123456789012:mytopic").matches(pattern);
        }

        @Test
        public void itAcceptsArnsWithoutRegion() {
            assertThat("arn:aws:imaginary-service-without-region::123456789012:resource").matches(pattern);
        }

        // Note: ChannelUriPattern knowingly does not specifically enforce SNS Arns as to allow more services to be
        // added in the future.
    }
}
