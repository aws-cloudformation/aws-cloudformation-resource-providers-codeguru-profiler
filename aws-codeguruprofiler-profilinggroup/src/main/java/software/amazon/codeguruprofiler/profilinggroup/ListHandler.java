package software.amazon.codeguruprofiler.profilinggroup;

import software.amazon.awssdk.services.codeguruprofiler.CodeGuruProfilerClient;
import software.amazon.awssdk.services.codeguruprofiler.model.InternalServerException;
import software.amazon.awssdk.services.codeguruprofiler.model.ListProfilingGroupsRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.ListProfilingGroupsResponse;
import software.amazon.awssdk.services.codeguruprofiler.model.ThrottlingException;
import software.amazon.cloudformation.exceptions.CfnServiceInternalErrorException;
import software.amazon.cloudformation.exceptions.CfnThrottlingException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.ArrayList;
import java.util.List;

public class ListHandler extends BaseHandler<CallbackContext> {

    private final CodeGuruProfilerClient profilerClient = CodeGuruProfilerClientBuilder.create();

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger) {

        final List<ResourceModel> models = new ArrayList<>();
        final String awsAccountId = request.getAwsAccountId();

        try {
            ListProfilingGroupsRequest listProfilingGroupsRequest = ListProfilingGroupsRequest.builder()
                    .includeDescription(true)
                    .maxResults(100)
                    .nextToken(request.getNextToken())
                    .build();

            ListProfilingGroupsResponse response = proxy.injectCredentialsAndInvokeV2(listProfilingGroupsRequest, profilerClient::listProfilingGroups);

            response.profilingGroups().forEach(pg ->
                    models.add(ResourceModel.builder()
                            .profilingGroupName(pg.name())
                            .arn(pg.arn()).build())
            );

            logger.log(String.format("%d \"%s\" for accountId [%s] has been successfully listed for token %s!", models.size(), ResourceModel.TYPE_NAME, awsAccountId, request.getNextToken()));

            return ProgressEvent.<ResourceModel, CallbackContext>builder()
                    .resourceModels(models)
                    .nextToken(response.nextToken())
                    .status(OperationStatus.SUCCESS)
                    .build();

        } catch (InternalServerException e) {
            throw new CfnServiceInternalErrorException(e);
        } catch (ThrottlingException e) {
            throw new CfnThrottlingException(e);
        }
    }
}
