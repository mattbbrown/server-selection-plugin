package hudson.plugins.serverselection;

import hudson.util.FormValidation;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;
import org.apache.commons.lang.StringUtils;
import net.sf.json.JSONObject;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.SimpleParameterDefinition;
import hudson.model.StringParameterValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import jenkins.model.Jenkins;

/**
 * @author huybrechts
 */
public class ServSelTarParameterDefinition extends SimpleParameterDefinition {

    public static final String CHOICES_DELIMETER = "\\r?\\n";

    private final List<String> servers;
    private final String defaultValue;

    public static boolean areValidChoices(String choices) {
        String strippedChoices = choices.trim();
        return !StringUtils.isEmpty(strippedChoices) && strippedChoices.split(CHOICES_DELIMETER).length > 0;
    }

    @DataBoundConstructor
    public ServSelTarParameterDefinition() {
        super("TARGET", "Note: selecting a specific server will override the Target Server Type");
        ServSelJobProperty.DescriptorImpl descriptor = Jenkins.getInstance().getDescriptorByType(ServSelJobProperty.DescriptorImpl.class);
        servers = descriptor.getAllServersList();
        defaultValue = null;
    }

    private ServSelTarParameterDefinition(String name, List<String> servers, String defaultValue, String description) {
        super(name, description);
        this.servers = servers;
        this.defaultValue = defaultValue;
    }

    @Override
    public ParameterDefinition copyWithDefaultValue(ParameterValue defaultValue) {
        if (defaultValue instanceof StringParameterValue) {
            StringParameterValue value = (StringParameterValue) defaultValue;
            return new ServSelTarParameterDefinition(getName(), servers, value.value, getDescription());
        } else {
            return this;
        }
    }

    @Exported
    public List<String> getServers() {
        return servers;
    }

    public String getChoicesText() {
        return StringUtils.join(servers, "\n");
    }

    @Override
    public StringParameterValue getDefaultParameterValue() {
        return new StringParameterValue(getName(), defaultValue == null ? servers.get(0) : defaultValue, getDescription());
    }

    private ServSelParameterValue checkValue(ServSelParameterValue value) {
        if (!servers.contains(value.server)) {
            throw new IllegalArgumentException("Illegal choice: " + value.server);
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
    public ServSelParameterValue createValue(String server) {
        return checkValue(new ServSelParameterValue(getName(), server, getDescription()));
    }

    @Extension
    public static class DescriptorImpl extends ParameterDescriptor {

        @Override
        public String getDisplayName() {
            return "Server Target Parameter";
        }

        @Override
        public String getHelpFile() {
            return "/help/parameter/choice.html";
        }

    }

}
