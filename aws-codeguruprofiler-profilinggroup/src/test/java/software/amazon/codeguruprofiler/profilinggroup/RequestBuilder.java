package software.amazon.codeguruprofiler.profilinggroup;

import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class RequestBuilder {
    static ResourceHandlerRequest<ResourceModel> makeValidRequest() {
        return makeRequest(ResourceModel.builder().profilingGroupName("IronMan-Suite-34").build());
    }

    static ResourceHandlerRequest<ResourceModel> makeInvalidRequest() {
        return makeRequest(ResourceModel.builder().build());
    }

    static ResourceHandlerRequest<ResourceModel> makeRequest(ResourceModel model) {
        return ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(model).build();
    }
}
