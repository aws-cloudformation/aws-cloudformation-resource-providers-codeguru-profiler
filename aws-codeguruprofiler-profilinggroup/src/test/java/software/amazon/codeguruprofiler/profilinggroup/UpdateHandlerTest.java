package software.amazon.codeguruprofiler.profilinggroup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import software.amazon.awssdk.services.codeguruprofiler.model.ActionGroup;
import software.amazon.awssdk.services.codeguruprofiler.model.ComputePlatform;
import software.amazon.awssdk.services.codeguruprofiler.model.ConflictException;
import software.amazon.awssdk.services.codeguruprofiler.model.DescribeProfilingGroupRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.DescribeProfilingGroupResponse;
import software.amazon.awssdk.services.codeguruprofiler.model.GetPolicyRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.GetPolicyResponse;
import software.amazon.awssdk.services.codeguruprofiler.model.InternalServerException;
import software.amazon.awssdk.services.codeguruprofiler.model.ProfilingGroupDescription;
import software.amazon.awssdk.services.codeguruprofiler.model.PutPermissionRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.PutPermissionResponse;
import software.amazon.awssdk.services.codeguruprofiler.model.RemovePermissionRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.ResourceNotFoundException;
import software.amazon.awssdk.services.codeguruprofiler.model.ThrottlingException;
import software.amazon.awssdk.services.codeguruprofiler.model.ValidationException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnServiceInternalErrorException;
import software.amazon.cloudformation.exceptions.CfnThrottlingException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static software.amazon.codeguruprofiler.profilinggroup.RequestBuilder.makeRequest;
import static software.amazon.codeguruprofiler.profilinggroup.RequestBuilder.makeValidRequest;

public class UpdateHandlerTest {
    @Mock
    private AmazonWebServicesClientProxy proxy = mock(AmazonWebServicesClientProxy.class);

    @Mock
    private Logger logger = mock(Logger.class);

    private UpdateHandler subject = new UpdateHandler();

    private ResourceHandlerRequest<ResourceModel> request;

    private final String profilingGroupName = "IronMan-2020";

    private final String revisionId = "TestRevisionId-123";

    private final GetPolicyRequest getPolicyRequest =
        GetPolicyRequest.builder().profilingGroupName(profilingGroupName).build();

    private final List<String> principals = Arrays.asList("arn:aws:iam::123456789012:role/UnitTestRole");

    private final DescribeProfilingGroupResponse describeProfilingGroupResponse =
        DescribeProfilingGroupResponse.builder().profilingGroup(ProfilingGroupDescription.builder()
            .name(profilingGroupName)
            .arn("arn:of:profilinggroup")
            .computePlatform(ComputePlatform.DEFAULT.toString())
            .build()).build();

    @Nested
    class WhenNoPermissionsIsProvided {
        @BeforeEach
        public void setup() {
            request =  makeRequest(ResourceModel.builder().profilingGroupName(profilingGroupName).build());
            doReturn(GetPolicyResponse.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(eq(getPolicyRequest), any());
            doReturn(describeProfilingGroupResponse)
                .when(proxy).injectCredentialsAndInvokeV2(any(DescribeProfilingGroupRequest.class), any());
        }

        @Test
        public void testSuccess() {
            final ProgressEvent<ResourceModel, CallbackContext> response
                = subject.handleRequest(proxy, request, null, logger);

            assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
            assertThat(response.getCallbackContext()).isNull();
            assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
            assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
            assertThat(response.getMessage()).isNull();
            assertThat(response.getErrorCode()).isNull();
        }

        @Test
        public void itOnlyCallsGetPolicy() {
            subject.handleRequest(proxy, request, null, logger);

            verify(proxy, times(1)).injectCredentialsAndInvokeV2(eq(getPolicyRequest), any());
            verify(proxy, times(1)).injectCredentialsAndInvokeV2(any(DescribeProfilingGroupRequest.class), any());
            verifyNoMoreInteractions(proxy);
        }

        @Nested
        class WhenPolicyWasAttached {
            @BeforeEach
            public void setup() {
                doReturn(
                    GetPolicyResponse.builder()
                        .policy("RandomString")
                        .revisionId(revisionId)
                        .build()
                ).when(proxy).injectCredentialsAndInvokeV2(eq(getPolicyRequest), any());
            }

            @Test
            public void itCallsGetPolicyAndRemovePermissions() {
                subject.handleRequest(proxy, request, null, logger);

                verify(proxy, times(1)).injectCredentialsAndInvokeV2(eq(getPolicyRequest), any());
                verify(proxy, times(1)).injectCredentialsAndInvokeV2(
                    eq(RemovePermissionRequest.builder()
                           .profilingGroupName(profilingGroupName)
                           .actionGroup(ActionGroup.AGENT_PERMISSIONS)
                           .revisionId(revisionId)
                           .build()
                    ),
                    any());
                verify(proxy, times(1)).injectCredentialsAndInvokeV2(any(DescribeProfilingGroupRequest.class), any());
                verifyNoMoreInteractions(proxy);
            }
        }
    }

    @Nested
    class WhenPermissionsIsProvided {
        private final PutPermissionRequest putPermissionRequest =
            PutPermissionRequest.builder()
                .profilingGroupName(profilingGroupName)
                .actionGroup(ActionGroup.AGENT_PERMISSIONS)
                .principals(principals)
                .build();

        @BeforeEach
        public void setup() {
            request =  makeRequest(
                ResourceModel.builder()
                    .profilingGroupName(profilingGroupName)
                    .agentPermissions(AgentPermissions.builder().principals(principals).build())
                    .build()
            );
            doReturn(GetPolicyResponse.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(eq(getPolicyRequest), any());
            doReturn(PutPermissionResponse.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(eq(putPermissionRequest), any());
            doReturn(describeProfilingGroupResponse)
                .when(proxy).injectCredentialsAndInvokeV2(any(DescribeProfilingGroupRequest.class), any());
        }

        @Test
        public void testSuccess() {
            final ProgressEvent<ResourceModel, CallbackContext> response
                = subject.handleRequest(proxy, request, null, logger);

            assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
            assertThat(response.getCallbackContext()).isNull();
            assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
            assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
            assertThat(response.getMessage()).isNull();
            assertThat(response.getErrorCode()).isNull();
        }

        @Test
        public void itCallsGetPolicyAndPutPermissions() {
            subject.handleRequest(proxy, request, null, logger);

            verify(proxy, times(1)).injectCredentialsAndInvokeV2(eq(getPolicyRequest), any());
            verify(proxy, times(1)).injectCredentialsAndInvokeV2(eq(putPermissionRequest), any());
            verify(proxy, times(1)).injectCredentialsAndInvokeV2(any(DescribeProfilingGroupRequest.class), any());
            verifyNoMoreInteractions(proxy);
        }

        @Nested
        class WhenPolicyWasAttached {
            private final PutPermissionRequest putPermissionRequest =
                PutPermissionRequest.builder()
                    .profilingGroupName(profilingGroupName)
                    .actionGroup(ActionGroup.AGENT_PERMISSIONS)
                    .principals(principals)
                    .revisionId(revisionId)
                    .build();

            @BeforeEach
            public void setup() {
                doReturn(GetPolicyResponse.builder()
                             .policy("RandomString")
                             .revisionId(revisionId)
                             .build()
                ).when(proxy).injectCredentialsAndInvokeV2(eq(getPolicyRequest), any());

                doReturn(PutPermissionResponse.builder().build())
                    .when(proxy).injectCredentialsAndInvokeV2(eq(putPermissionRequest), any());
            }

            @Test
            public void itCallsGetPolicyAndPutPermission() {
                subject.handleRequest(proxy, request, null, logger);

                verify(proxy, times(1)).injectCredentialsAndInvokeV2(eq(getPolicyRequest), any());
                verify(proxy, times(1)).injectCredentialsAndInvokeV2(eq(putPermissionRequest), any());
                verify(proxy, times(1)).injectCredentialsAndInvokeV2(any(DescribeProfilingGroupRequest.class), any());
                verifyNoMoreInteractions(proxy);
            }
        }
    }

    @Nested
    class WhenComputePlatformIsNotChanged {
        @BeforeEach
        public void setup() {
            doReturn(describeProfilingGroupResponse)
                .when(proxy).injectCredentialsAndInvokeV2(
                    ArgumentMatchers.eq(DescribeProfilingGroupRequest
                        .builder()
                        .profilingGroupName(profilingGroupName)
                        .build()), any());
            doReturn(GetPolicyResponse.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(eq(getPolicyRequest), any());
        }

        @Test
        public void testSuccessWhenRequestWithNoComputePlatformForDefaultProfilingGroup() {
            request =  makeRequest(ResourceModel.builder().profilingGroupName(profilingGroupName).build());

            final ProgressEvent<ResourceModel, CallbackContext> response
                = subject.handleRequest(proxy, request, null, logger);

            assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
            assertThat(response.getResourceModel().getComputePlatform()).isNull();
        }

        @Test
        public void testSuccessWhenRequestWithDefaultComputePlatformForDefaultProfilingGroup() {
            request =  makeRequest(ResourceModel.builder().profilingGroupName(profilingGroupName)
                .computePlatform(ComputePlatform.DEFAULT.toString()).build());

            final ProgressEvent<ResourceModel, CallbackContext> response
                = subject.handleRequest(proxy, request, null, logger);

            assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
            assertThat(response.getResourceModel().getComputePlatform()).isEqualTo(ComputePlatform.DEFAULT.toString());
        }

        @Test
        public void testSuccessWhenRequestWithLambdaComputePlatformForLambdaProfilingGroup() {
            doReturn(DescribeProfilingGroupResponse.builder()
                .profilingGroup(ProfilingGroupDescription.builder()
                    .name(profilingGroupName)
                    .arn("arn:of:profilinggroup")
                    .computePlatform(ComputePlatform.AWS_LAMBDA.toString())
                    .build())
                .build())
                .when(proxy).injectCredentialsAndInvokeV2(
                    ArgumentMatchers.eq(DescribeProfilingGroupRequest
                        .builder()
                        .profilingGroupName(profilingGroupName)
                        .build()), any());

            request =  makeRequest(ResourceModel.builder().profilingGroupName(profilingGroupName)
                .computePlatform(ComputePlatform.AWS_LAMBDA.toString()).build());

            final ProgressEvent<ResourceModel, CallbackContext> response
                = subject.handleRequest(proxy, request, null, logger);

            assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
            assertThat(response.getResourceModel().getComputePlatform()).isEqualTo(ComputePlatform.AWS_LAMBDA.toString());
        }
    }

    @Nested
    class WhenComputePlatformIsChanged {
        @BeforeEach
        public void setup() {
            doReturn(GetPolicyResponse.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(eq(getPolicyRequest), any());
        }

        @Test
        public void testExceptionWhenRequestWithLambdaComputePlatformForDefaultProfilingGroup() {
            doReturn(describeProfilingGroupResponse)
                .when(proxy).injectCredentialsAndInvokeV2(
                    ArgumentMatchers.eq(DescribeProfilingGroupRequest
                        .builder()
                        .profilingGroupName(profilingGroupName)
                        .build()), any());

            request =  makeRequest(ResourceModel.builder().profilingGroupName(profilingGroupName)
                .computePlatform(ComputePlatform.AWS_LAMBDA.toString()).build());

            assertThrows(CfnInvalidRequestException.class, () -> subject.handleRequest(proxy, request, null, logger));
        }

        @Nested
        class WhenRequestHasDefaultComputePlatformForLambdaProfilingGroup {
            @BeforeEach
            public void setup() {
                doReturn(DescribeProfilingGroupResponse.builder()
                    .profilingGroup(ProfilingGroupDescription.builder()
                        .name(profilingGroupName)
                        .arn("arn:of:profilinggroup")
                        .computePlatform(ComputePlatform.AWS_LAMBDA.toString())
                        .build())
                    .build())
                    .when(proxy).injectCredentialsAndInvokeV2(
                        ArgumentMatchers.eq(DescribeProfilingGroupRequest
                            .builder()
                            .profilingGroupName(profilingGroupName)
                            .build()), any());
            }

            @Test
            public void testExceptionWhenRequestWithNoComputePlatform() {
                request = makeRequest(ResourceModel.builder().profilingGroupName(profilingGroupName).build());

                assertThrows(CfnInvalidRequestException.class, () -> subject.handleRequest(proxy, request, null, logger));
            }

            @Test
            public void testExceptionWhenRequestWithDefaultComputePlatform() {
                request = makeRequest(ResourceModel.builder().profilingGroupName(profilingGroupName)
                    .computePlatform(ComputePlatform.DEFAULT.toString()).build());

                assertThrows(CfnInvalidRequestException.class, () -> subject.handleRequest(proxy, request, null, logger));
            }
        }
    }

    @Nested
    class WhenThereIsAnException {
        @BeforeEach
        public void setup() {
            request =  makeValidRequest();
            doReturn(describeProfilingGroupResponse)
                .when(proxy).injectCredentialsAndInvokeV2(any(DescribeProfilingGroupRequest.class), any());
        }

        @Test
        public void itThrowsConflictException() {
            doThrow(ConflictException.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

            assertThrows(CfnAlreadyExistsException.class, () -> subject.handleRequest(proxy, request, null, logger));
        }

        @Test
        public void itThrowsNotFoundException() {
            doThrow(ResourceNotFoundException.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

            assertThrows(CfnNotFoundException.class, () -> subject.handleRequest(proxy, request, null, logger));
        }

        @Test
        public void itThrowsInternalServerException() {
            doThrow(InternalServerException.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

            assertThrows(CfnServiceInternalErrorException.class, () -> subject.handleRequest(proxy, request, null, logger));
        }

        @Test
        public void itThrowsThrottlingException() {
            doThrow(ThrottlingException.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

            assertThrows(CfnThrottlingException.class, () -> subject.handleRequest(proxy, request, null, logger));
        }

        @Test
        public void itThrowsValidationException() {
            doThrow(ValidationException.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

            assertThrows(CfnInvalidRequestException.class, () -> subject.handleRequest(proxy, request, null, logger));
        }
    }
}
