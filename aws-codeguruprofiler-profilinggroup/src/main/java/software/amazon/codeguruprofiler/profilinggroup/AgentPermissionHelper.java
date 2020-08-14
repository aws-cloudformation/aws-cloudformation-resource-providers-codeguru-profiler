package software.amazon.codeguruprofiler.profilinggroup;

import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.codeguruprofiler.CodeGuruProfilerClient;
import software.amazon.awssdk.services.codeguruprofiler.model.GetPolicyRequest;
import software.amazon.awssdk.services.codeguruprofiler.model.GetPolicyResponse;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;

import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class AgentPermissionHelper {
    private static final CodeGuruProfilerClient profilerClient = CodeGuruProfilerClientBuilder.create();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @FunctionalInterface
    public interface GetPrincipalsFunction<Proxy, PgName, Principals> {
        Principals apply(Proxy s, PgName t);
    }

    @SuppressWarnings("unchecked")
    public static List<String> getPrincipalsFromPolicy(AmazonWebServicesClientProxy proxy, String pgName) {
        GetPolicyRequest getPolicyRequest = GetPolicyRequest.builder().profilingGroupName(pgName).build();
        GetPolicyResponse getPolicyResponse = proxy.injectCredentialsAndInvokeV2(getPolicyRequest, profilerClient::getPolicy);
        String policyInJson = getPolicyResponse.policy();

        if (policyInJson == null || policyInJson.isEmpty()) return emptyList();

        try {
            // An example policy returned from the response can be found in [AgentPermissionHelperTest]
            Map<String, List<Map<String, Map<String, Object>>>> policyMap = objectMapper.readValue(policyInJson, Map.class);
            Object principals = policyMap.get("Statement").get(0).get("Principal").get("AWS");

            if (principals instanceof String) {
                return singletonList((String) principals);
            } else {
                return (List<String>) principals;
            }
        } catch (Exception e) {
            throw new CfnInternalFailureException(e);
        }
    }
}
