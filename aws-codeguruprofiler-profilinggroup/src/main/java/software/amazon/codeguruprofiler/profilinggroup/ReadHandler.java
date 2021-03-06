package software.amazon.codeguruprofiler.profilinggroup;

import software.amazon.awssdk.services.codeguruprofiler.CodeGuruProfilerClient;
import software.amazon.awssdk.services.codeguruprofiler.model.DescribeProfilingGroupRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.DescribeProfilingGroupResponse;
import software.amazon.awssdk.services.codeguruprofiler.model.InternalServerException;
import software.amazon.awssdk.services.codeguruprofiler.model.NotificationConfiguration;
import software.amazon.awssdk.services.codeguruprofiler.model.ResourceNotFoundException;
import software.amazon.awssdk.services.codeguruprofiler.model.ThrottlingException;
import software.amazon.awssdk.services.codeguruprofiler.model.ValidationException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnServiceInternalErrorException;
import software.amazon.cloudformation.exceptions.CfnThrottlingException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.codeguruprofiler.profilinggroup.AgentPermissionHelper.GetPrincipalsFunction;

import java.util.ArrayList;
import java.util.List;

import static software.amazon.codeguruprofiler.profilinggroup.NotificationChannelHelper.convertNotificationConfigurationIntoChannelsList;
import static software.amazon.codeguruprofiler.profilinggroup.NotificationChannelHelper.getNotificationChannel;
import static software.amazon.codeguruprofiler.profilinggroup.TagHelper.convertTagMapIntoSet;

public class ReadHandler extends BaseHandler<CallbackContext> {

    private final CodeGuruProfilerClient profilerClient = CodeGuruProfilerClientBuilder.create();

    private final GetPrincipalsFunction<AmazonWebServicesClientProxy, String, List<String>> getPrincipalsFunction;

    public ReadHandler() {
        super();
        getPrincipalsFunction = AgentPermissionHelper::getPrincipalsFromPolicy;
    }

    public ReadHandler(GetPrincipalsFunction<AmazonWebServicesClientProxy, String, List<String>> getPrincipals) {
        super();
        getPrincipalsFunction = getPrincipals;
    }

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger) {

        final ResourceModel model = request.getDesiredResourceState();
        final String awsAccountId = request.getAwsAccountId();

        try {
            String pgName = model.getProfilingGroupName();
            DescribeProfilingGroupRequest describeProfilingGroupRequest = DescribeProfilingGroupRequest.builder()
                    .profilingGroupName(pgName)
                    .build();

            DescribeProfilingGroupResponse response = proxy.injectCredentialsAndInvokeV2(describeProfilingGroupRequest, profilerClient::describeProfilingGroup);
            model.setProfilingGroupName(response.profilingGroup().name()); // This is not needed but making sure the response is the same as the request!
            model.setArn(response.profilingGroup().arn());
            model.setComputePlatform(response.profilingGroup().computePlatformAsString());
            model.setTags(new ArrayList<>(convertTagMapIntoSet(response.profilingGroup().tags())));

            NotificationConfiguration notificationConfiguration = getNotificationChannel(pgName, proxy, profilerClient).notificationConfiguration();
            model.setAnomalyDetectionNotificationConfiguration(convertNotificationConfigurationIntoChannelsList(notificationConfiguration));

            model.setAgentPermissions(AgentPermissions.builder().principals(getPrincipalsFunction.apply(proxy, pgName)).build());

            logger.log(String.format("%s [%s] for accountId [%s] has been successfully read!", ResourceModel.TYPE_NAME, model.getProfilingGroupName(), awsAccountId));

            return ProgressEvent.defaultSuccessHandler(model);

        } catch (ResourceNotFoundException e) {
            throw new CfnNotFoundException(e);
        } catch (InternalServerException e) {
            throw new CfnServiceInternalErrorException(e);
        } catch (ThrottlingException e) {
            throw new CfnThrottlingException(e);
        } catch (ValidationException e) {
            throw new CfnInvalidRequestException(ResourceModel.TYPE_NAME + e.getMessage(), e);
        }
    }
}
