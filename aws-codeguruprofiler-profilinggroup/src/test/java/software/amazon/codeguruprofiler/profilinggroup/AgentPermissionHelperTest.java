package software.amazon.codeguruprofiler.profilinggroup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import software.amazon.awssdk.services.codeguruprofiler.model.GetPolicyRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.GetPolicyResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class AgentPermissionHelperTest {
    @Mock
    private final AmazonWebServicesClientProxy proxy = mock(AmazonWebServicesClientProxy.class);

    private final String pgName = "BlackWidow-2020";
    private final String testPrincipalArn1 = "arn:aws:iam:012345678901:user/User1";
    private final String testPrincipalArn2 = "arn:aws:iam:012345678901:user/User2";

    @Nested
    class DescribeGetPrincipalsFromPolicy {
        String testPolicyJson = createStandardPolicyJson("[\""+ testPrincipalArn1 + "\", \"" + testPrincipalArn2 + "\"]");

        @BeforeEach
        public void setup() {
            doReturn(GetPolicyResponse.builder().policy(testPolicyJson).revisionId("testRevisionId").build())
                .when(proxy).injectCredentialsAndInvokeV2(any(GetPolicyRequest.class), any());
        }

        @Test
        public void itOnlyCallsGetPolicy() {
            AgentPermissionHelper.getPrincipalsFromPolicy(proxy, pgName);

            verify(proxy, times(1))
                .injectCredentialsAndInvokeV2(eq(GetPolicyRequest.builder().profilingGroupName(pgName).build()), any());
            verifyNoMoreInteractions(proxy);
        }

        @Test
        public void itReturnsPrincipals() {
            List<String> principalsFromPolicy = AgentPermissionHelper.getPrincipalsFromPolicy(proxy, pgName);

            assertThat(principalsFromPolicy).containsExactly(testPrincipalArn1, testPrincipalArn2);
        }

        @Nested
        class WhenThereIsOnlyOnePrincipal {
            String testPolicyForOnePrincipalJson = createStandardPolicyJson("\""+ testPrincipalArn1 +"\"");

            @BeforeEach
            public void setup() {
                doReturn(GetPolicyResponse.builder().policy(testPolicyForOnePrincipalJson).revisionId("testRevisionId").build())
                    .when(proxy).injectCredentialsAndInvokeV2(any(GetPolicyRequest.class), any());
            }

            @Test
            public void itReturnsPrincipal() {
                List<String> principalsFromPolicy = AgentPermissionHelper.getPrincipalsFromPolicy(proxy, pgName);

                assertThat(principalsFromPolicy).containsExactly(testPrincipalArn1);
            }
        }

        @Nested
        class WhenThereIsNoAgentPermissionAttached {
            @BeforeEach
            public void setup() {
                doReturn(GetPolicyResponse.builder().build())
                    .when(proxy).injectCredentialsAndInvokeV2(any(GetPolicyRequest.class), any());
            }

            @Test
            public void itReturnsEmptyList() {
                List<String> principalsFromPolicy = AgentPermissionHelper.getPrincipalsFromPolicy(proxy, pgName);

                assertThat(principalsFromPolicy).isEmpty();
            }
        }

        private String createStandardPolicyJson(String principals) {
            return "{" +
                   "  \"Version\": \"2012-10-17\"," +
                   "  \"Statement\": [" +
                   "    {" +
                   "        \"Sid\": \"agentPermissions-statement\"," +
                   "        \"Effect\": \"Allow\"," +
                   "        \"Principal\":" +
                   "          {" +
                   "              \"AWS\": " + principals +
                   "          }," +
                   "        \"Action\": [\"codeguru-profiler:ConfigureAgent\", \"codeguru-profiler:PostAgentProfile\"]," +
                   "        \"Resource\": \"arn:aws:codeguru-profiler:us-east-1:012345678901:profilingGroup/TestGroup\"" +
                   "    }" +
                   "   ]" +
                   "}";
        }
    }
}
