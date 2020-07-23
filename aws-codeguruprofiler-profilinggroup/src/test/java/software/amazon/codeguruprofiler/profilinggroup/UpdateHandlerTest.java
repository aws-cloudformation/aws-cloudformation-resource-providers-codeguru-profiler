package software.amazon.codeguruprofiler.profilinggroup;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import software.amazon.awssdk.services.codeguruprofiler.model.ActionGroup;
import software.amazon.awssdk.services.codeguruprofiler.model.AddNotificationChannelsRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.Channel;
import software.amazon.awssdk.services.codeguruprofiler.model.ConflictException;
import software.amazon.awssdk.services.codeguruprofiler.model.EventPublisher;
import software.amazon.awssdk.services.codeguruprofiler.model.GetNotificationConfigurationRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.GetNotificationConfigurationResponse;
import software.amazon.awssdk.services.codeguruprofiler.model.GetPolicyRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.GetPolicyResponse;
import software.amazon.awssdk.services.codeguruprofiler.model.InternalServerException;
import software.amazon.awssdk.services.codeguruprofiler.model.NotificationConfiguration;
import software.amazon.awssdk.services.codeguruprofiler.model.PutPermissionRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.PutPermissionResponse;
import software.amazon.awssdk.services.codeguruprofiler.model.RemoveNotificationChannelRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.RemoveNotificationChannelResponse;
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
import software.amazon.codeguruprofiler.profilinggroup.UpdateHandler.UpdateTagsFunction;

import java.util.Arrays;
import java.util.Collections;
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

    @SuppressWarnings("unchecked")
    private UpdateTagsFunction<AmazonWebServicesClientProxy, ResourceModel, String, String, Logger> updateTagFunction = mock(UpdateTagsFunction.class);

    private UpdateHandler subject = new UpdateHandler(updateTagFunction);

    private ResourceHandlerRequest<ResourceModel> request;

    private final String profilingGroupName = "IronMan-2020";

    private final String revisionId = "TestRevisionId-123";

    private final GetPolicyRequest getPolicyRequest =
            GetPolicyRequest.builder().profilingGroupName(profilingGroupName).build();

    private final List<String> principals = Arrays.asList("arn:aws:iam::123456789012:role/UnitTestRole");

    @Nested
    class WhenNotificationChannelConfigurationIsProvided {
        private final GetNotificationConfigurationRequest getNotificationConfigurationRequest =
                GetNotificationConfigurationRequest.builder().profilingGroupName(profilingGroupName).build();

        private final RemoveNotificationChannelRequest removeChannel1Request =
                RemoveNotificationChannelRequest.builder()
                        .profilingGroupName(profilingGroupName)
                        .channelId("channelId")
                        .build();

        private final AddNotificationChannelsRequest addChannel2Request = AddNotificationChannelsRequest.builder()
                .profilingGroupName(profilingGroupName)
                .channels(Channel.builder()
                        .uri("channelUri2")
                        .eventPublishers(ImmutableSet.of(EventPublisher.ANOMALY_DETECTION))
                        .build())
                .build();

        @BeforeEach
        public void setup() {
            request = makeRequest(ResourceModel.builder().profilingGroupName(profilingGroupName).anomalyDetectionNotificationConfiguration(
                    Collections.singletonList(software.amazon.codeguruprofiler.profilinggroup.Channel.builder()
                            .channelUri("channelUri")
                            .build()))
                    .build());

            doReturn(GetPolicyResponse.builder().build())
                    .when(proxy).injectCredentialsAndInvokeV2(eq(getPolicyRequest), any());

            doReturn(RemoveNotificationChannelResponse.builder().build())
                    .when(proxy).injectCredentialsAndInvokeV2(eq(removeChannel1Request), any());
        }

        @Test
        public void itOnlyCallsAddNotificationChannelWhenUpdatedModelHasAdditionalChannelsNoIdSet() {
            doReturn(GetNotificationConfigurationResponse.builder()
                    .notificationConfiguration(NotificationConfiguration.builder()
                            .channels(Channel.builder()
                                    .id("channelId")
                                    .uri("channelUri")
                                    .eventPublishers(EventPublisher.ANOMALY_DETECTION)
                                    .build())
                            .build())
                    .build())
                    .when(proxy).injectCredentialsAndInvokeV2(eq(getNotificationConfigurationRequest), any());

            // the new model should have the additional channel
            request = makeRequest(ResourceModel.builder().profilingGroupName(profilingGroupName).anomalyDetectionNotificationConfiguration(
                    ImmutableList.of(
                            software.amazon.codeguruprofiler.profilinggroup.Channel.builder()
                                    .channelUri("channelUri")
                                    .build(),
                            software.amazon.codeguruprofiler.profilinggroup.Channel.builder()
                                    .channelUri("channelUri2")
                                    .build()))
                    .build());

            subject.handleRequest(proxy, request, null, logger);

            verify(proxy, times(1)).injectCredentialsAndInvokeV2(eq(getPolicyRequest), any());
            verify(proxy, times(1)).injectCredentialsAndInvokeV2(eq(getNotificationConfigurationRequest), any());
            verify(proxy, times(1)).injectCredentialsAndInvokeV2(eq(addChannel2Request), any());
            verifyNoMoreInteractions(proxy);
        }

        @Test
        public void itOnlyCallsRemoveNotificationChannelWhenUpdatedModelHasLessChannelsNoIdSet() {
            doReturn(GetNotificationConfigurationResponse.builder()
                    .notificationConfiguration(NotificationConfiguration.builder()
                            .channels(Channel.builder()
                                            .id("channelId")
                                            .uri("channelUri")
                                            .eventPublishers(EventPublisher.ANOMALY_DETECTION)
                                            .build(),
                                    Channel.builder()
                                            .id("channelId2")
                                            .uri("channelUri2")
                                            .build())
                            .build())
                    .build())
                    .when(proxy).injectCredentialsAndInvokeV2(eq(getNotificationConfigurationRequest), any());

            request = makeRequest(ResourceModel.builder().profilingGroupName(profilingGroupName).anomalyDetectionNotificationConfiguration(
                    ImmutableList.of(software.amazon.codeguruprofiler.profilinggroup.Channel.builder()
                            .channelUri("channelUri2")
                            .build()))
                    .build());

            subject.handleRequest(proxy, request, null, logger);

            verify(proxy, times(1)).injectCredentialsAndInvokeV2(eq(getPolicyRequest), any());
            verify(proxy, times(1)).injectCredentialsAndInvokeV2(eq(getNotificationConfigurationRequest), any());
            verify(proxy, times(1)).injectCredentialsAndInvokeV2(eq(removeChannel1Request), any());
            verifyNoMoreInteractions(proxy);

        }

        @Test
        public void itUpdatesChannelWithSameUriButDifferentId() {
            doReturn(GetNotificationConfigurationResponse.builder()
                    .notificationConfiguration(NotificationConfiguration.builder()
                            .channels(Channel.builder()
                                            .id("channelId")
                                            .uri("channelUri")
                                            .eventPublishers(EventPublisher.ANOMALY_DETECTION)
                                            .build(),
                                    Channel.builder()
                                            .id("channelId2")
                                            .uri("channelUri2")
                                            .build())
                            .build())
                    .build())
                    .when(proxy).injectCredentialsAndInvokeV2(eq(getNotificationConfigurationRequest), any());

            request = makeRequest(ResourceModel.builder().profilingGroupName(profilingGroupName).anomalyDetectionNotificationConfiguration(
                    ImmutableList.of(
                            software.amazon.codeguruprofiler.profilinggroup.Channel.builder()
                                    .channelId("channelIdNew")
                                    .channelUri("channelUri")
                                    .build(),
                            software.amazon.codeguruprofiler.profilinggroup.Channel.builder()
                                    .channelUri("channelUri2")
                                    .build()))
                    .build());

            subject.handleRequest(proxy, request, null, logger);

            verify(proxy, times(1)).injectCredentialsAndInvokeV2(eq(getPolicyRequest), any());
            verify(proxy, times(1)).injectCredentialsAndInvokeV2(eq(getNotificationConfigurationRequest), any());
            verify(proxy, times(1)).injectCredentialsAndInvokeV2(eq(removeChannel1Request), any());
            verify(proxy, times(1)).injectCredentialsAndInvokeV2(eq(AddNotificationChannelsRequest.builder().profilingGroupName(profilingGroupName).channels(
                    Channel.builder().id("channelIdNew").uri("channelUri").eventPublishers(EventPublisher.ANOMALY_DETECTION).build())
                            .build()), any());
            verifyNoMoreInteractions(proxy);

        }

        @Test
        public void itDoesNotAddOrDeleteNotificationChannelWithNoChangeSet() {
            doReturn(GetNotificationConfigurationResponse.builder()
                    .notificationConfiguration(NotificationConfiguration.builder()
                            .channels(Channel.builder()
                                            .id("channelId")
                                            .uri("channelUri")
                                            .eventPublishers(EventPublisher.ANOMALY_DETECTION)
                                            .build(),
                                    Channel.builder()
                                            .id("channelId2")
                                            .uri("channelUri2")
                                            .build())
                            .build())
                    .build())
                    .when(proxy).injectCredentialsAndInvokeV2(eq(getNotificationConfigurationRequest), any());

            request = makeRequest(ResourceModel.builder().profilingGroupName(profilingGroupName).anomalyDetectionNotificationConfiguration(
                    ImmutableList.of(
                            software.amazon.codeguruprofiler.profilinggroup.Channel.builder()
                                    .channelUri("channelUri")
                                    .build(),
                            software.amazon.codeguruprofiler.profilinggroup.Channel.builder()
                                    .channelUri("channelUri2")
                                    .build()))
                    .build());

            subject.handleRequest(proxy, request, null, logger);

            verify(proxy, times(1)).injectCredentialsAndInvokeV2(eq(getPolicyRequest), any());
            verify(proxy, times(1)).injectCredentialsAndInvokeV2(eq(getNotificationConfigurationRequest), any());
            verifyNoMoreInteractions(proxy);
        }
    }

    @Nested
    class WhenNoPermissionsIsProvided {
        @BeforeEach
        public void setup() {
            request =  makeRequest(ResourceModel.builder().profilingGroupName(profilingGroupName).build());
            doReturn(GetPolicyResponse.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(eq(getPolicyRequest), any());
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
                    eq(RemovePermissionRequest.builder().profilingGroupName(profilingGroupName)
                           .actionGroup(ActionGroup.AGENT_PERMISSIONS)
                           .revisionId(revisionId)
                           .build()
                    ),
                    any());
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
                verifyNoMoreInteractions(proxy);
            }
        }
    }

    @Nested
    class WhenTagsAreProvided {
        private final String groupArn = "arn:aws:codeguru-profiler:us-east-1:123456789012:profilingGroup/" + profilingGroupName;
        private final List<Tag> newTags = Arrays.asList(Tag.builder().key("TestKey").value("TestValue").build());
        private ResourceModel desiredModel = ResourceModel.builder()
                                                 .profilingGroupName(profilingGroupName)
                                                 .arn(groupArn)
                                                 .tags(newTags)
                                                 .build();

        @BeforeEach
        public void setup() {
            request = makeRequest(desiredModel);

            doReturn(GetPolicyResponse.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(any(GetPolicyRequest.class), any());
        }

        @Test
        public void itUpdatesTagsWithCorrectParameters() {
            subject.handleRequest(proxy, request, null, logger);

            verify(updateTagFunction, times(1))
                .apply(proxy, desiredModel, request.getAwsAccountId(), groupArn, logger );
        }

        @Nested
        class WhenArnIsNotProvided {
            private ResourceModel desiredModel = ResourceModel.builder()
                                                     .profilingGroupName(profilingGroupName)
                                                     .tags(newTags)
                                                     .build();

            @BeforeEach
            public void setup() {
                request = makeRequest(desiredModel);
            }

            @Test
            public void itGeneratesArnAndUpdatesTagsWithCorrectParameters() {
                subject.handleRequest(proxy, request, null, logger);

                verify(updateTagFunction, times(1))
                    .apply(proxy,
                        desiredModel,
                        request.getAwsAccountId(),
                        String.join(":",
                            "arn",
                            "aws",
                            "codeguru-profiler",
                            request.getRegion(),
                            request.getAwsAccountId(),
                            "profilingGroup/" + profilingGroupName)
                        ,logger);
            }
        }
    }

    @Nested
    class WhenThereIsAnException {
        @BeforeEach
        public void setup() {
            request =  makeValidRequest();
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
