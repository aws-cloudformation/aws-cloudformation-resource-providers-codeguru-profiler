package software.amazon.codeguruprofiler.profilinggroup;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.codeguruprofiler.model.CodeGuruProfilerException;
import software.amazon.awssdk.services.codeguruprofiler.model.ConflictException;
import software.amazon.awssdk.services.codeguruprofiler.model.CreateProfilingGroupRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.CreateProfilingGroupResponse;
import software.amazon.awssdk.services.codeguruprofiler.model.DeleteProfilingGroupRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.InternalServerException;
import software.amazon.awssdk.services.codeguruprofiler.model.ProfilingGroupDescription;
import software.amazon.awssdk.services.codeguruprofiler.model.PutPermissionRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.ServiceQuotaExceededException;
import software.amazon.awssdk.services.codeguruprofiler.model.ThrottlingException;
import software.amazon.awssdk.services.codeguruprofiler.model.ValidationException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnServiceInternalErrorException;
import software.amazon.cloudformation.exceptions.CfnServiceLimitExceededException;
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
import static software.amazon.awssdk.services.codeguruprofiler.model.ActionGroup.AGENT_PERMISSIONS;
import static software.amazon.codeguruprofiler.profilinggroup.RequestBuilder.makeRequest;
import static software.amazon.codeguruprofiler.profilinggroup.RequestBuilder.makeValidRequest;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest {

    @Mock
    private AmazonWebServicesClientProxy proxy = mock(AmazonWebServicesClientProxy.class);

    @Mock
    private Logger logger = mock(Logger.class);

    private CreateHandler subject = new CreateHandler();

    private ResourceHandlerRequest<ResourceModel> request;

    private final String profilingGroupName = "Silver-2020";
    private final String clientToken = "clientTokenXXX";
    private final List<String> principals = Arrays.asList("a", "bc");

    private final CreateProfilingGroupRequest createPgRequest = CreateProfilingGroupRequest.builder()
            .profilingGroupName(profilingGroupName)
            .clientToken(clientToken)
            .build();

    private final PutPermissionRequest putPermissionsRequest = PutPermissionRequest.builder()
            .profilingGroupName(profilingGroupName)
            .actionGroup(AGENT_PERMISSIONS)
            .principals(principals)
            .build();

    private final DeleteProfilingGroupRequest deleteProfilingGroupRequest = DeleteProfilingGroupRequest.builder()
            .profilingGroupName(profilingGroupName)
            .build();

    @Nested
    class WhenPermissionsAreNotSet {

        @BeforeEach
        public void setup() {
            request = makeRequest(ResourceModel.builder().profilingGroupName(profilingGroupName).build());

            doReturn(CreateProfilingGroupResponse.builder()
                         .profilingGroup(ProfilingGroupDescription.builder().name(profilingGroupName).build())
                         .build())
                    .when(proxy).injectCredentialsAndInvokeV2(eq(createPgRequest), any());
        }

        @Test
        public void testSuccess() {
            final ProgressEvent<ResourceModel, CallbackContext> response = subject.handleRequest(proxy, request, null, logger);

            assertSuccessfulResponse(response);
        }

        @Test
        public void itOnlyCallsCreatePG() {
            subject.handleRequest(proxy, request, null, logger);

            verify(proxy, times(1)).injectCredentialsAndInvokeV2(eq(createPgRequest), any());
            verifyNoMoreInteractions(proxy);
        }
    }

    @Nested
    class WhenPermissionsAreSet {

        @BeforeEach
        public void setup() {
            doReturn(CreateProfilingGroupResponse.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(eq(createPgRequest), any());
        }

        @Nested
        class WhenPrincipalsAreNotSet {

            @Test
            public void itSucceedsWithNullPermissions() {
                ResourceModel model = newResourceModel(null);
                request = makeRequest(model);
                assertSuccessfulResponse(subject.handleRequest(proxy, request, null, logger));
            }

            @Test
            public void itSucceedsWithNullPrincipals() {
                ResourceModel model = newResourceModel(AgentPermissions.builder().principals(null).build());
                request = makeRequest(model);
                assertSuccessfulResponse(subject.handleRequest(proxy, request, null, logger));
            }

            @AfterEach
            public void itOnlyCallsCreatePG() {
                verify(proxy, times(1)).injectCredentialsAndInvokeV2(eq(createPgRequest), any());
                verifyNoMoreInteractions(proxy);
            }
        }

        @Nested
        class WhenPrincipalsAreSet {

            @BeforeEach
            public void setup() {
                ResourceModel model = newResourceModel(AgentPermissions.builder().principals(principals).build());
                request = makeRequest(model);
            }

            @Test
            public void testSuccess() {
                assertSuccessfulResponse(subject.handleRequest(proxy, request, null, logger));
            }

            @Test
            public void itCallsCreatePGAndPutPermissions() {
                subject.handleRequest(proxy, request, null, logger);
                verify(proxy, times(1)).injectCredentialsAndInvokeV2(eq(createPgRequest), any());
                verify(proxy, times(1)).injectCredentialsAndInvokeV2(eq(putPermissionsRequest), any());
                verifyNoMoreInteractions(proxy);
            }

            @Nested
            class WhenPutPermissionsFails {
                @Test
                public void itThrowsExceptionWithNoSuppressedExceptionFromUnderlyingDeletePGAction() {
                    doThrow(ConflictException.builder().build())
                        .when(proxy).injectCredentialsAndInvokeV2(eq(putPermissionsRequest), any());

                    CfnAlreadyExistsException exception = assertThrows(CfnAlreadyExistsException.class,
                        () -> subject.handleRequest(proxy, request, null, logger));

                    assertThat(exception).hasCauseExactlyInstanceOf(ConflictException.class);
                    assertThat(exception).hasNoSuppressedExceptions();
                }

                @Nested
                class WhenDeletePGFails {
                    @Test
                    public void itThrowsExceptionWithSuppressedExceptionFromUnderlyingDeletePGAction() {
                        Throwable deleteException = InternalServerException.builder().build();
                        doThrow(ConflictException.builder().build())
                            .when(proxy).injectCredentialsAndInvokeV2(eq(putPermissionsRequest), any());
                        doThrow(deleteException)
                            .when(proxy).injectCredentialsAndInvokeV2(eq(deleteProfilingGroupRequest), any());

                        CfnAlreadyExistsException exception = assertThrows(CfnAlreadyExistsException.class,
                            () -> subject.handleRequest(proxy, request, null, logger));

                        assertThat(exception).hasCauseExactlyInstanceOf(ConflictException.class);
                        assertThat(exception.getCause()).hasSuppressedException(deleteException);
                    }
                }
            }
        }
    }

    @Nested
    class WhenThereIsAnException {

        @BeforeEach
        public void setup() {
            request = makeValidRequest();
        }

        @Test
        public void itThrowsConflictException() {
            doThrow(ConflictException.builder().build()).when(proxy).injectCredentialsAndInvokeV2(any(), any());

            CfnAlreadyExistsException exception = assertThrows(CfnAlreadyExistsException.class,
                () -> subject.handleRequest(proxy, request, null, logger));
            assertThat(exception).hasCauseExactlyInstanceOf(ConflictException.class);
        }

        @Test
        public void itThrowsServiceQuotaExceededException() {
            doThrow(ServiceQuotaExceededException.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

            CfnServiceLimitExceededException exception = assertThrows(CfnServiceLimitExceededException.class,
                () -> subject.handleRequest(proxy, request, null, logger));
            assertThat(exception).hasCauseExactlyInstanceOf(ServiceQuotaExceededException.class);
        }

        @Test
        public void itThrowsInternalServerException() {
            doThrow(InternalServerException.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

            CfnServiceInternalErrorException exception = assertThrows(CfnServiceInternalErrorException.class,
                () -> subject.handleRequest(proxy, request, null, logger));
            assertThat(exception).hasCauseExactlyInstanceOf(InternalServerException.class);
        }

        @Test
        public void itThrowsThrottlingException() {
            doThrow(ThrottlingException.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

            CfnThrottlingException exception = assertThrows(CfnThrottlingException.class,
                () -> subject.handleRequest(proxy, request, null, logger));
            assertThat(exception).hasCauseExactlyInstanceOf(ThrottlingException.class);
        }

        @Test
        public void itThrowsValidationException() {
            doThrow(ValidationException.builder().build()).when(proxy).injectCredentialsAndInvokeV2(any(), any());

            CfnInvalidRequestException exception = assertThrows(CfnInvalidRequestException.class,
                () -> subject.handleRequest(proxy, request, null, logger));
            assertThat(exception).hasCauseExactlyInstanceOf(ValidationException.class);
        }

        @Test
        public void itThrowsAnyOtherCodeGuruExceptionException() {
            doThrow(CodeGuruProfilerException.builder().build()).when(proxy).injectCredentialsAndInvokeV2(any(), any());

            CodeGuruProfilerException exception = assertThrows(CodeGuruProfilerException.class,
                () -> subject.handleRequest(proxy, request, null, logger));
            assertThat(exception).hasNoCause();
        }
    }

    private ResourceModel newResourceModel(final AgentPermissions permissions) {
        return ResourceModel.builder()
                   .profilingGroupName(profilingGroupName)
                   .agentPermissions(permissions)
                   .build();
    }

    private void assertSuccessfulResponse(ProgressEvent<ResourceModel, CallbackContext> response) {
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }
}
