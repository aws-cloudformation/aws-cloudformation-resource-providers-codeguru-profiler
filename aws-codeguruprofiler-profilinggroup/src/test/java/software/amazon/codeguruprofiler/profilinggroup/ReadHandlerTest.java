package software.amazon.codeguruprofiler.profilinggroup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.codeguruprofiler.model.DescribeProfilingGroupRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.DescribeProfilingGroupResponse;
import software.amazon.awssdk.services.codeguruprofiler.model.InternalServerException;
import software.amazon.awssdk.services.codeguruprofiler.model.ProfilingGroupDescription;
import software.amazon.awssdk.services.codeguruprofiler.model.ResourceNotFoundException;
import software.amazon.awssdk.services.codeguruprofiler.model.ThrottlingException;
import software.amazon.awssdk.services.codeguruprofiler.model.ValidationException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnServiceInternalErrorException;
import software.amazon.cloudformation.exceptions.CfnThrottlingException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static software.amazon.codeguruprofiler.profilinggroup.RequestBuilder.makeInvalidRequest;
import static software.amazon.codeguruprofiler.profilinggroup.RequestBuilder.makeValidRequest;

@ExtendWith(MockitoExtension.class)
public class ReadHandlerTest {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private Logger logger;

    private ResourceHandlerRequest<ResourceModel> request;

    @BeforeEach
    public void setup() {
        proxy = mock(AmazonWebServicesClientProxy.class);
        logger = mock(Logger.class);

        request = makeValidRequest();
    }

    @Test
    public void testSuccessState() {
        final String arn = "arn:aws:codeguru-profiler:us-east-1:000000000000:profilingGroup/IronMan-Suit-34";

        doReturn(DescribeProfilingGroupResponse.builder()
                .profilingGroup(ProfilingGroupDescription.builder()
                        .name("IronMan-Suit-34")
                        .arn(arn)
                        .computePlatform("Default")
                        .tags(new HashMap<String, String>() {{ put("superhero", "blackWidow"); }})
                        .build())
                .build())
                .when(proxy).injectCredentialsAndInvokeV2(
                    ArgumentMatchers.eq(DescribeProfilingGroupRequest
                        .builder()
                        .profilingGroupName("IronMan-Suit-34")
                        .build()), any());

        final ProgressEvent<ResourceModel, CallbackContext> response
                = new ReadHandler().handleRequest(proxy, request, null, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModel().getArn()).isEqualTo(arn);
        assertThat(response.getResourceModel().getComputePlatform()).isEqualTo("Default");
        assertThat(response.getResourceModel().getTags()).containsOnly(Tag.builder().key("superhero").value("blackWidow").build());
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void testNotFoundException() {
        doThrow(ResourceNotFoundException.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnNotFoundException.class, () ->
                new ReadHandler().handleRequest(proxy, request, null, logger));
    }

    @Test
    public void testInternalServerException() {
        doThrow(InternalServerException.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnServiceInternalErrorException.class, () ->
                new ReadHandler().handleRequest(proxy, request, null, logger));
    }

    @Test
    public void testThrottlingException() {
        doThrow(ThrottlingException.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnThrottlingException.class, () ->
                new ReadHandler().handleRequest(proxy, request, null, logger));
    }

    @Test
    public void testValidationException() {
        doThrow(ValidationException.builder().build())
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnInvalidRequestException.class, () ->
                new ReadHandler().handleRequest(proxy, makeInvalidRequest(), null, logger));
    }
}
