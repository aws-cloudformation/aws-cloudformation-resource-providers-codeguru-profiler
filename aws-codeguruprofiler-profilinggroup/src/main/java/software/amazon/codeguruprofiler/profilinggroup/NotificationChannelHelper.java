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
import software.amazon.awssdk.services.codeguruprofiler.model.RemoveNotificationChannelRequest;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;

public class NotificationChannelHelper {
    private NotificationChannelHelper() {
        // prevent instantiation
    }

    public static void addChannelNotifications(String pgName, List<software.amazon.codeguruprofiler.profilinggroup.Channel> channels,
                                               AmazonWebServicesClientProxy proxy, CodeGuruProfilerClient profilerClient) {
        AddNotificationChannelsRequest.Builder addNotificationChannelsRequest = AddNotificationChannelsRequest.builder()
                .profilingGroupName(pgName);
        addNotificationChannelsRequest.channels(channels.stream().map(channel -> Channel.builder().uri(channel.getChannelUri())
                .eventPublishers(ImmutableSet.of(EventPublisher.ANOMALY_DETECTION))
                .id(channel.getChannelId()).build()).collect(Collectors.toList()));

        proxy.injectCredentialsAndInvokeV2(addNotificationChannelsRequest.build(), profilerClient::addNotificationChannels);
    }

    public static void addChannelNotification(String pgName, Channel channel, AmazonWebServicesClientProxy proxy, CodeGuruProfilerClient profilerClient) {
        addChannelNotifications(pgName, Collections.singletonList(software.amazon.codeguruprofiler.profilinggroup.Channel.builder()
                .channelId(channel.id())
                .channelUri(channel.uri())
                .build()), proxy, profilerClient);
    }

    public static void deleteNotificationChannel(final String pgName, final String channelId, final AmazonWebServicesClientProxy proxy, CodeGuruProfilerClient profilerClient) {
        RemoveNotificationChannelRequest removeNotificationChannelRequest = RemoveNotificationChannelRequest.builder()
                .channelId(channelId)
                .profilingGroupName(pgName)
                .build();
        proxy.injectCredentialsAndInvokeV2(removeNotificationChannelRequest, profilerClient::removeNotificationChannel);
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
