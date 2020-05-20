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

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private Logger logger;

    private CreateHandler subject;

    private ResourceHandlerRequest<ResourceModel> request;

    private final String profilingGroupName = "Silver-2020";
    private final String clientToken = "clientTokenXXX";
    private final CreateProfilingGroupRequest createPgRequest = CreateProfilingGroupRequest.builder()
            .profilingGroupName(profilingGroupName)
            .clientToken(clientToken)
            .build();

    @BeforeEach
    public void setup() {
        proxy = mock(AmazonWebServicesClientProxy.class);
        logger = mock(Logger.class);
        subject = new CreateHandler();
    }

    @Nested
    class WithNoPermissions {

        @BeforeEach
        public void setup() {
            request = makeRequest(ResourceModel.builder().profilingGroupName(profilingGroupName).build());

            doReturn(CreateProfilingGroupResponse.builder()
                    .profilingGroup(ProfilingGroupDescription.builder()
                            .name(profilingGroupName)
                            .build())
                    .build())
                    .when(proxy).injectCredentialsAndInvokeV2(eq(createPgRequest), any());
        }

        @Test
        public void testSuccess() {
            final ProgressEvent<ResourceModel, CallbackContext> response = subject.handleRequest(proxy, request, null, logger);

            assertResponse(response);
        }

        @Test
        public void testCorrectOperationIsCalled() {
            subject.handleRequest(proxy, request, null, logger);

            verify(proxy, times(1)).injectCredentialsAndInvokeV2(eq(createPgRequest), any());
            verifyNoMoreInteractions(proxy);
        }
    }

    @Nested
    class WithPermissions {

        @BeforeEach
        public void setup() {
            doReturn(CreateProfilingGroupResponse.builder().build())
                    .when(proxy).injectCredentialsAndInvokeV2(any(CreateProfilingGroupRequest.class), any());
        }

        @Nested
        class NoPrincipals {

            @Test
            public void testNullPermissions() {
                ResourceModel model = newResourceModel(null);
                request = makeRequest(model);
                assertResponse(subject.handleRequest(proxy, request, null, logger));
            }

            @Test
            public void testNullPrincipals() {
                ResourceModel model = newResourceModel(AgentPermissions.builder().principals(null).build());
                request = makeRequest(model);
                assertResponse(subject.handleRequest(proxy, request, null, logger));
            }

            @AfterEach
            public void testCorrectOperationIsCalled() {
                verify(proxy, times(1)).injectCredentialsAndInvokeV2(eq(createPgRequest), any());
                verifyNoMoreInteractions(proxy);
            }
        }

        @Nested
        class WithPrincipals {

            @BeforeEach
            public void setup() {
                ResourceModel model = newResourceModel(AgentPermissions.builder().principals(Arrays.asList("a", "bc")).build());
                request = makeRequest(model);
            }

            @Test
            public void testSuccess() {
                assertResponse(subject.handleRequest(proxy, request, null, logger));
                verify(proxy, times(1)).injectCredentialsAndInvokeV2(eq(createPgRequest), any());
                verify(proxy, times(1)).injectCredentialsAndInvokeV2(any(PutPermissionRequest.class), any());
                verifyNoMoreInteractions(proxy);
            }

            @Test
            public void testPutPermissionsFails() {
                doThrow(ConflictException.builder().build())
                        .when(proxy).injectCredentialsAndInvokeV2(any(PutPermissionRequest.class), any());

                CfnAlreadyExistsException exception = assertThrows(CfnAlreadyExistsException.class,
                        () -> subject.handleRequest(proxy, request, null, logger));

                assertThat(exception).hasCauseExactlyInstanceOf(ConflictException.class);
                assertThat(exception).hasNoSuppressedExceptions();

                verify(proxy, times(1)).injectCredentialsAndInvokeV2(eq(createPgRequest), any());
                verify(proxy, times(1)).injectCredentialsAndInvokeV2(any(PutPermissionRequest.class), any());
                verify(proxy).injectCredentialsAndInvokeV2(any(DeleteProfilingGroupRequest.class), any());
                verifyNoMoreInteractions(proxy);
            }

            @Test
            public void testPutPermissionsFailsAndDeleteProfilingGroupFails() {
                Throwable deleteException = InternalServerException.builder().build();
                doThrow(ConflictException.builder().build())
                        .when(proxy).injectCredentialsAndInvokeV2(any(PutPermissionRequest.class), any());
                doThrow(deleteException)
                        .when(proxy).injectCredentialsAndInvokeV2(any(DeleteProfilingGroupRequest.class), any());

                CfnAlreadyExistsException exception = assertThrows(CfnAlreadyExistsException.class,
                        () -> subject.handleRequest(proxy, request, null, logger));

                assertThat(exception).hasCauseExactlyInstanceOf(ConflictException.class);
                assertThat(exception.getCause()).hasSuppressedException(deleteException);

                verify(proxy, times(1)).injectCredentialsAndInvokeV2(eq(createPgRequest), any());
                verify(proxy, times(1)).injectCredentialsAndInvokeV2(any(PutPermissionRequest.class), any());
                verify(proxy, times(1)).injectCredentialsAndInvokeV2(any(DeleteProfilingGroupRequest.class), any());
                verifyNoMoreInteractions(proxy);
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
        public void testConflictException() {
            doThrow(ConflictException.builder().build())
                    .when(proxy).injectCredentialsAndInvokeV2(any(), any());

            CfnAlreadyExistsException exception = assertThrows(CfnAlreadyExistsException.class, () -> subject.handleRequest(proxy, request, null, logger));
            assertThat(exception).hasCauseExactlyInstanceOf(ConflictException.class);
        }

        @Test
        public void testServiceQuotaExceededException() {
            doThrow(ServiceQuotaExceededException.builder().build())
                    .when(proxy).injectCredentialsAndInvokeV2(any(), any());

            CfnServiceLimitExceededException exception = assertThrows(CfnServiceLimitExceededException.class, () ->
                    subject.handleRequest(proxy, request, null, logger));
            assertThat(exception).hasCauseExactlyInstanceOf(ServiceQuotaExceededException.class);
        }

        @Test
        public void testInternalServerException() {
            doThrow(InternalServerException.builder().build())
                    .when(proxy).injectCredentialsAndInvokeV2(any(), any());

            CfnServiceInternalErrorException exception = assertThrows(CfnServiceInternalErrorException.class, () ->
                    subject.handleRequest(proxy, request, null, logger));
            assertThat(exception).hasCauseExactlyInstanceOf(InternalServerException.class);
        }

        @Test
        public void testThrottlingException() {
            doThrow(ThrottlingException.builder().build())
                    .when(proxy).injectCredentialsAndInvokeV2(any(), any());

            CfnThrottlingException exception = assertThrows(CfnThrottlingException.class, () ->
                    subject.handleRequest(proxy, request, null, logger));
            assertThat(exception).hasCauseExactlyInstanceOf(ThrottlingException.class);
        }

        @Test
        public void testValidationException() {
            doThrow(ValidationException.builder().build())
                    .when(proxy).injectCredentialsAndInvokeV2(any(), any());

            CfnInvalidRequestException exception = assertThrows(CfnInvalidRequestException.class, () ->
                    subject.handleRequest(proxy, request, null, logger));
            assertThat(exception).hasCauseExactlyInstanceOf(ValidationException.class);
        }

        @Test
        public void testAnyOtherCodeGuruExceptionException() {
            doThrow(CodeGuruProfilerException.builder().build())
                    .when(proxy).injectCredentialsAndInvokeV2(any(), any());

            CodeGuruProfilerException exception = assertThrows(CodeGuruProfilerException.class, () ->
                    subject.handleRequest(proxy, request, null, logger));
            assertThat(exception).hasNoCause();
        }
    }

    private ResourceModel newResourceModel(final AgentPermissions permissions) {
        return ResourceModel.builder()
                .profilingGroupName(profilingGroupName)
                .agentPermissions(permissions)
                .build();
    }

    private void assertResponse(ProgressEvent<ResourceModel, CallbackContext> response) {
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }
}
