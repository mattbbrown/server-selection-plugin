package hudson.plugins.serverselection;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;
import org.apache.commons.lang.StringUtils;
import net.sf.json.JSONObject;
import hudson.Extension;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.SimpleParameterDefinition;
import hudson.model.StringParameterValue;

import java.util.List;
import jenkins.model.Jenkins;

/**
 * @author huybrechts
 */
public class ServSelParameterDefinition extends SimpleParameterDefinition {

    public static final String CHOICES_DELIMETER = "\\r?\\n";

    private final List<String> servers;
    private final List<String> environments;
    private final String defaultValue;

    public static boolean areValidChoices(String choices) {
        String strippedChoices = choices.trim();
        return !StringUtils.isEmpty(strippedChoices) && strippedChoices.split(CHOICES_DELIMETER).length > 0;
    }

    @DataBoundConstructor
    public ServSelParameterDefinition() {
        super("Server Selection", "Note: selecting a specific server will override the Target Server Type and the build/version parameters");
        ServSelJobProperty.DescriptorImpl descriptor = Jenkins.getInstance().getDescriptorByType(ServSelJobProperty.DescriptorImpl.class);
        servers = descriptor.getAllServersList();
        environments = descriptor.getEnvironments();
        defaultValue = null;
    }

    private ServSelParameterDefinition(String name, List<String> servers, List<String> environments, String defaultValue, String description) {
        super(name, description);
        this.servers = servers;
        this.defaultValue = defaultValue;
        this.environments = environments;
    }

    @Override
    public ParameterDefinition copyWithDefaultValue(ParameterValue defaultValue) {
        if (defaultValue instanceof StringParameterValue) {
            StringParameterValue value = (StringParameterValue) defaultValue;
            return new ServSelParameterDefinition(getName(), servers, environments, value.value, getDescription());
        } else {
            return this;
        }
    }

    @Exported
    public List<String> getServers() {
        return servers;
    }

    @Override
    public StringParameterValue getDefaultParameterValue() {
        return new StringParameterValue(getName(), defaultValue == null ? servers.get(0) : defaultValue, getDescription());
    }

    private ServSelParameterValue checkValue(ServSelParameterValue value) {
        if (!servers.contains(value.server)) {
            throw new IllegalArgumentException("Illegal choice: " + value.server);
        }
        if (!environments.contains(value.environ)) {
            throw new IllegalArgumentException("Illegal choice: " + value.environ);
        }
        return value;
    }

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        ServSelParameterValue value = req.bindJSON(ServSelParameterValue.class, jo);

        value.setDescription(getDescription());
        return checkValue(value);
    }

    @Override
    public ServSelParameterValue createValue(String value) {
        return checkValue(new ServSelParameterValue(getName(), value, null, null, getDescription()));
    }

    @Extension
    public static class DescriptorImpl extends ParameterDescriptor {

        @Override
        public String getDisplayName() {
            return "Server Selection Parameter";
        }

        @Override
        public String getHelpFile() {
            return "/help/parameter/choice.html";
        }

    }

}
