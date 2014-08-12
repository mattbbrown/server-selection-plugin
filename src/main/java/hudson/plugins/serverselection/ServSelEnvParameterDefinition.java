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
public class ServSelEnvParameterDefinition extends SimpleParameterDefinition {

    public static final String CHOICES_DELIMETER = "\\r?\\n";

    private final List<String> environments;
    private final String defaultValue;

    public static boolean areValidChoices(String choices) {
        String strippedChoices = choices.trim();
        return !StringUtils.isEmpty(strippedChoices) && strippedChoices.split(CHOICES_DELIMETER).length > 0;
    }

    @DataBoundConstructor
    public ServSelEnvParameterDefinition() {
        super("ENVIRONMENT", "");
        ServSelJobProperty.DescriptorImpl descriptor = Jenkins.getInstance().getDescriptorByType(ServSelJobProperty.DescriptorImpl.class);
        environments = descriptor.getEnvironments();
        defaultValue = null;
    }

    private ServSelEnvParameterDefinition(String name, List<String> environments, String defaultValue, String description) {
        super("ENVIRONMENT", "");
        ServSelJobProperty.DescriptorImpl descriptor = Jenkins.getInstance().getDescriptorByType(ServSelJobProperty.DescriptorImpl.class);
        this.environments = descriptor.getEnvironments();
        this.defaultValue = defaultValue;
    }

    @Override
    public ParameterDefinition copyWithDefaultValue(ParameterValue defaultValue) {
        if (defaultValue instanceof StringParameterValue) {
            StringParameterValue value = (StringParameterValue) defaultValue;
            return new ServSelEnvParameterDefinition(getName(), environments, value.value, "");
        } else {
            return this;
        }
    }

    @Exported
    public List<String> getEnvironments() {
        return environments;
    }

    public String getChoicesText() {
        return StringUtils.join(environments, "\n");
    }

    @Override
    public StringParameterValue getDefaultParameterValue() {
        return new StringParameterValue(getName(), defaultValue == null ? environments.get(0) : defaultValue, getDescription());
    }

    private StringParameterValue checkValue(StringParameterValue value) {
        if (!environments.contains(value.value)) {
            throw new IllegalArgumentException("Illegal choice: " + value.value);
        }
        return value;
    }

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        StringParameterValue value = req.bindJSON(StringParameterValue.class, jo);
        value.setDescription(getDescription());
        return checkValue(value);
    }

    @Override
    public StringParameterValue createValue(String environment) {
        return checkValue(new StringParameterValue(getName(), environment, getDescription()));
    }

    @Extension
    public static class DescriptorImpl extends ParameterDescriptor {

        @Override
        public String getDisplayName() {
            return "Server Environment Parameter";
        }

        @Override
        public String getHelpFile() {
            return "/help/parameter/choice.html";
        }

    }

}
