package software.amazon.codeguruprofiler.profilinggroup;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableSet;
import software.amazon.awssdk.services.codeguruprofiler.CodeGuruProfilerClient;
import software.amazon.awssdk.services.codeguruprofiler.model.AddNotificationChannelsRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.Channel;
import software.amazon.awssdk.services.codeguruprofiler.model.EventPublisher;
import software.amazon.awssdk.services.codeguruprofiler.model.GetNotificationConfigurationRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.GetNotificationConfigurationResponse;
import software.amazon.awssdk.services.codeguruprofiler.model.NotificationConfiguration;
import software.amazon.awssdk.services.codeguruprofiler.model.RemoveNotificationChannelRequest;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;

public class NotificationChannelHelper {
    private NotificationChannelHelper() {
        // prevent instantiation
    }

    public static void addChannelNotifications(String pgName, List<software.amazon.codeguruprofiler.profilinggroup.Channel> channels,
                                               AmazonWebServicesClientProxy proxy, CodeGuruProfilerClient profilerClient) {
        addConvertedChannelNotifications(pgName, channels.stream().map(pgChannel -> Channel.builder()
                .uri(pgChannel.getChannelUri())
                .eventPublishers(ImmutableSet.of(EventPublisher.ANOMALY_DETECTION))
                .id(pgChannel.getChannelId())
                .build()
        ).collect(Collectors.toList()), proxy, profilerClient);
    }

    private static void addConvertedChannelNotifications(String pgName, List<Channel> channels, AmazonWebServicesClientProxy proxy, CodeGuruProfilerClient profilerClient) {
        AddNotificationChannelsRequest addNotificationChannelsRequest = AddNotificationChannelsRequest.builder()
                .profilingGroupName(pgName).channels(channels).build();

        proxy.injectCredentialsAndInvokeV2(addNotificationChannelsRequest, profilerClient::addNotificationChannels);
    }

    public static void addChannelNotification(String pgName, Channel channel, AmazonWebServicesClientProxy proxy, CodeGuruProfilerClient profilerClient) {
        addConvertedChannelNotifications(pgName, Collections.singletonList(channel), proxy, profilerClient);
    }

    public static void deleteNotificationChannel(final String pgName, final String channelId, final AmazonWebServicesClientProxy proxy, CodeGuruProfilerClient profilerClient) {
        RemoveNotificationChannelRequest removeNotificationChannelRequest = RemoveNotificationChannelRequest.builder()
                .channelId(channelId)
                .profilingGroupName(pgName)
                .build();
        proxy.injectCredentialsAndInvokeV2(removeNotificationChannelRequest, profilerClient::removeNotificationChannel);
    }

    public static GetNotificationConfigurationResponse getNotificationChannel(final String pgName, final AmazonWebServicesClientProxy proxy, CodeGuruProfilerClient profilerClient) {
        GetNotificationConfigurationRequest getNotificationConfigurationRequest =
            GetNotificationConfigurationRequest.builder().profilingGroupName(pgName).build();

        return proxy.injectCredentialsAndInvokeV2(getNotificationConfigurationRequest, profilerClient::getNotificationConfiguration);
    }

    public static List<software.amazon.codeguruprofiler.profilinggroup.Channel> convertNotificationConfigurationIntoChannelsList(final NotificationConfiguration configuration) {
        return configuration
            .channels()
            .stream()
            .map(s -> software.amazon.codeguruprofiler.profilinggroup.Channel.builder().channelId(s.id()).channelUri(s.uri()).build())
            .collect(Collectors.toList());
    }

    // Since we don't have a PUT operation, this emulates a channel update, when the updated channel contains a new Id
    public static void updateChannelId(final String pgName, final String channelId, final Channel requestedChannel, final AmazonWebServicesClientProxy proxy,
                                     CodeGuruProfilerClient profilerClient) {
        deleteNotificationChannel(pgName, channelId, proxy, profilerClient);
        addChannelNotification(pgName, requestedChannel, proxy, profilerClient);
    }

    public static Optional<List<software.amazon.codeguruprofiler.profilinggroup.Channel>> anomalyDetectionNotificationConfiguration(final ResourceModel model) {
        return model.getAnomalyDetectionNotificationConfiguration() == null || model.getAnomalyDetectionNotificationConfiguration().isEmpty() ?
                Optional.empty() : Optional.of(model.getAnomalyDetectionNotificationConfiguration());
    }
}
