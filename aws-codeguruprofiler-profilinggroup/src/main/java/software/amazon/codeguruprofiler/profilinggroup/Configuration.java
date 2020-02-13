package software.amazon.codeguruprofiler.profilinggroup;

import java.util.Map;
import org.json.JSONObject;
import org.json.JSONTokener;

class Configuration extends BaseConfiguration {

    public Configuration() {
        super("aws-codeguruprofiler-profilinggroup.json");
    }
}
