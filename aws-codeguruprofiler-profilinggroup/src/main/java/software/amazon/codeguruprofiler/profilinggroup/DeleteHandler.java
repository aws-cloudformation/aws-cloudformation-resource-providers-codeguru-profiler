package software.amazon.codeguruprofiler.profilinggroup;

import software.amazon.awssdk.services.codeguruprofiler.CodeGuruProfilerClient;
import software.amazon.awssdk.services.codeguruprofiler.model.DeleteProfilingGroupRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.InternalServerException;
import software.amazon.awssdk.services.codeguruprofiler.model.ResourceNotFoundException;
import software.amazon.awssdk.services.codeguruprofiler.model.ThrottlingException;
import software.amazon.awssdk.services.codeguruprofiler.model.ValidationException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnServiceInternalErrorException;
import software.amazon.cloudformation.exceptions.CfnThrottlingException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class DeleteHandler extends BaseHandler<CallbackContext> {

    private final CodeGuruProfilerClient profilerClient = CodeGuruProfilerClientBuilder.create();

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger) {

        final ResourceModel model = request.getDesiredResourceState();
        final String awsAccountId = request.getAwsAccountId();
        final String profilingGroupName = model.getProfilingGroupName();

        try {
            DeleteProfilingGroupRequest deleteProfilingGroupRequest = DeleteProfilingGroupRequest.builder()
                    .profilingGroupName(profilingGroupName)
                    .build();

            proxy.injectCredentialsAndInvokeV2(deleteProfilingGroupRequest, profilerClient::deleteProfilingGroup);

            logger.log(String.format("%s [%s] for accountId [%s] has been successfully deleted!", ResourceModel.TYPE_NAME, profilingGroupName, awsAccountId));

            // Delete handler should not return a model in case of success, according to https://w.amazon.com/bin/view/AWS21/Design/Uluru/ContractTests
            return ProgressEvent.defaultSuccessHandler(null);
        } catch (ResourceNotFoundException e) {
            return ProgressEvent.defaultFailureHandler(e, HandlerErrorCode.NotFound);
        } catch (InternalServerException e) {
            throw new CfnServiceInternalErrorException(e);
        } catch (ThrottlingException e) {
            throw new CfnThrottlingException(e);
        } catch (ValidationException e) {
            throw new CfnInvalidRequestException(ResourceModel.TYPE_NAME + e.getMessage(), e);
        }
    }

}
