package software.amazon.codeguruprofiler.profilinggroup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.codeguruprofiler.model.InternalServerException;
import software.amazon.awssdk.services.codeguruprofiler.model.ListProfilingGroupsRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.ListProfilingGroupsResponse;
import software.amazon.awssdk.services.codeguruprofiler.model.ProfilingGroupDescription;
import software.amazon.awssdk.services.codeguruprofiler.model.ThrottlingException;
import software.amazon.cloudformation.exceptions.CfnServiceInternalErrorException;
import software.amazon.cloudformation.exceptions.CfnThrottlingException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static software.amazon.codeguruprofiler.profilinggroup.RequestBuilder.makeValidRequest;

@ExtendWith(MockitoExtension.class)
public class ListHandlerTest {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private Logger logger;

    @BeforeEach
    public void setup() {
        proxy = mock(AmazonWebServicesClientProxy.class);
        logger = mock(Logger.class);
    }

    @Test
    public void testSuccessState() {
        final ResourceHandlerRequest<ResourceModel> request = makeValidRequest();
        final String arn = "arn:aws:codeguru-profiler:us-east-1:000000000000:profilingGroup/IronMan-Suite-34";
        final ProfilingGroupDescription profilingGroupDescription = ProfilingGroupDescription.builder()
                        .name("IronMan-Suite-34")
                        .arn(arn)
                        .build();

        doReturn(ListProfilingGroupsResponse.builder()
                .profilingGroups(profilingGroupDescription)
                .nextToken("test-token")
                .build())
                .when(proxy).injectCredentialsAndInvokeV2(
                        ArgumentMatchers.eq(ListProfilingGroupsRequest
                                .builder()
                                .nextToken(null)
                                .maxResults(100)
                                .includeDescription(true)
                                .build()),
                        any());

        final ProgressEvent<ResourceModel, CallbackContext> response
                = new ListHandler().handleRequest(proxy, request, null, logger);

        final ResourceModel expectedModel = ResourceModel.builder()
                .profilingGroupName(profilingGroupDescription.name())
                .arn(profilingGroupDescription.arn())
                .build();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).containsExactly(expectedModel);
        assertThat(response.getNextToken()).isEqualTo("test-token");
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void testInternalServerException() {
        final ResourceHandlerRequest<ResourceModel> request = makeValidRequest();

        doThrow(InternalServerException.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnServiceInternalErrorException.class, () ->
                new ListHandler().handleRequest(proxy, request, null, logger));
    }

    @Test
    public void testThrottlingException() {
        final ResourceHandlerRequest<ResourceModel> request = makeValidRequest();

        doThrow(ThrottlingException.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnThrottlingException.class, () ->
                new ListHandler().handleRequest(proxy, request, null, logger));
    }
}
