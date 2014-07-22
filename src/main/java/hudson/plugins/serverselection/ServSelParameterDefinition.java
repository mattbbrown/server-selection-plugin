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
public class ServSelParameterDefinition extends SimpleParameterDefinition {

    public static final String CHOICES_DELIMETER = "\\r?\\n";

    private final List<String> choices;
    private final String defaultValue;

    public static boolean areValidChoices(String choices) {
        String strippedChoices = choices.trim();
        return !StringUtils.isEmpty(strippedChoices) && strippedChoices.split(CHOICES_DELIMETER).length > 0;
    }

    @DataBoundConstructor
    public ServSelParameterDefinition() {
        super("TARGET", "Select a server to run job against. Note: selecting a specific server will override the Target Server Type");
        ServSelJobProperty.DescriptorImpl descriptor = Jenkins.getInstance().getDescriptorByType(ServSelJobProperty.DescriptorImpl.class);
        choices = descriptor.getAllServersList();
        defaultValue = null;
    }

    private ServSelParameterDefinition(String name, List<String> choices, String defaultValue, String description) {
        super(name, description);
        this.choices = choices;
        this.defaultValue = defaultValue;
    }

    @Override
    public ParameterDefinition copyWithDefaultValue(ParameterValue defaultValue) {
        if (defaultValue instanceof StringParameterValue) {
            StringParameterValue value = (StringParameterValue) defaultValue;
            return new ServSelParameterDefinition(getName(), choices, value.value, getDescription());
        } else {
            return this;
        }
    }

    @Exported
    public List<String> getChoices() {
        return choices;
    }

    public String getChoicesText() {
        return StringUtils.join(choices, "\n");
    }

    @Override
    public StringParameterValue getDefaultParameterValue() {
        return new StringParameterValue(getName(), defaultValue == null ? choices.get(0) : defaultValue, getDescription());
    }

    private ServSelParameterValue checkValue(ServSelParameterValue value) {
        if (!choices.contains(value.value)) {
            throw new IllegalArgumentException("Illegal choice: " + value.value);
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
        return checkValue(new ServSelParameterValue(getName(), value, getDescription()));
    }

    @Extension
    public static class DescriptorImpl extends ParameterDescriptor {

        @Override
        public String getDisplayName() {
            return "Server Selection Parameter Definition";
        }

        @Override
        public String getHelpFile() {
            return "/help/parameter/choice.html";
        }

    }

}
