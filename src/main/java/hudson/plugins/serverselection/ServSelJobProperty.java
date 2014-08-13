package hudson.plugins.serverselection;

import hudson.Extension;
import hudson.matrix.MatrixConfiguration;
import hudson.model.AbstractDescribableImpl;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.Util;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.model.Queue;
import hudson.model.Queue.Task;
import java.io.IOException;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Logger;
import java.util.logging.Level;
import javax.annotation.CheckForNull;
import jenkins.model.Jenkins;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

public class ServSelJobProperty extends JobProperty<AbstractProject<?, ?>> {

    // Moving category to categories, to support, well, multiple categories per job.
    @Deprecated
    transient String category;

    private List<String> categories;
    private boolean servSelEnabled;
    private transient boolean throttleConfiguration;
    private @CheckForNull
    ServSelMatrixProjectOptions matrixOptions;

    /**
     * Store a config version so we're able to migrate config on various
     * functionality upgrades.
     */
    private Long configVersion;

    @DataBoundConstructor
    public ServSelJobProperty(List<String> categories,
            boolean servSelEnabled,
            @CheckForNull ServSelMatrixProjectOptions matrixOptions
    ) {
        this.categories = categories == null ? new ArrayList<String>() : categories;
        this.servSelEnabled = servSelEnabled;
        this.matrixOptions = matrixOptions;
    }

    /**
     * Migrates deprecated/obsolete data
     */
    public Object readResolve() {
        if (configVersion == null) {
            configVersion = 0L;
        }
        if (categories == null) {
            categories = new ArrayList<String>();
        }
        if (category != null) {
            categories.add(category);
            category = null;
        }
        configVersion = 1L;

        // Handle the throttleConfiguration in custom builds (not released)
        if (throttleConfiguration && matrixOptions == null) {
            matrixOptions = new ServSelMatrixProjectOptions(false, true);
        }

        return this;
    }

    @Override
    protected void setOwner(AbstractProject<?, ?> owner) {
        super.setOwner(owner);
        if (servSelEnabled && categories != null) {
            DescriptorImpl descriptor = (DescriptorImpl) getDescriptor();
            synchronized (descriptor.propertiesByCategoryLock) {
                for (String c : categories) {
                    Map<ServSelJobProperty, Void> properties = descriptor.propertiesByCategory.get(c);
                    if (properties == null) {
                        properties = new WeakHashMap<ServSelJobProperty, Void>();
                        descriptor.propertiesByCategory.put(c, properties);
                    }
                    properties.put(this, null);
                }
            }
        }
    }

    public boolean getServSelEnabled() {
        return servSelEnabled;
    }

    public List<String> getCategories() {
        return categories;
    }

    @CheckForNull
    public ServSelMatrixProjectOptions getMatrixOptions() {
        return matrixOptions;
    }

    /**
     * Check if the build throttles {@link MatrixBuild}s.
     */
    public boolean isThrottleMatrixBuilds() {
        return matrixOptions != null
                ? matrixOptions.isThrottleMatrixBuilds()
                : ServSelMatrixProjectOptions.DEFAULT.isThrottleMatrixBuilds();
    }

    /**
     * Check if the build throttles {@link MatrixConfiguration}s.
     */
    public boolean isThrottleMatrixConfigurations() {
        return matrixOptions != null
                ? matrixOptions.isThrottleMatrixConfigurations()
                : ServSelMatrixProjectOptions.DEFAULT.isThrottleMatrixConfigurations();
    }

    static List<AbstractProject<?, ?>> getCategoryProjects(String category) {
        assert category != null && !category.equals("");
        List<AbstractProject<?, ?>> categoryProjects = new ArrayList<AbstractProject<?, ?>>();
        Collection<ServSelJobProperty> properties;
        DescriptorImpl descriptor = Jenkins.getInstance().getDescriptorByType(DescriptorImpl.class);
        synchronized (descriptor.propertiesByCategoryLock) {
            Map<ServSelJobProperty, Void> _properties = descriptor.propertiesByCategory.get(category);
            properties = _properties != null ? new ArrayList<ServSelJobProperty>(_properties.keySet()) : Collections.<ServSelJobProperty>emptySet();
        }
        for (ServSelJobProperty t : properties) {
            if (t.getServSelEnabled()) {
                if (t.getCategories() != null && t.getCategories().contains(category)) {
                    AbstractProject<?, ?> p = t.owner;
                    if (/* not deleted */getItem(p.getParent(), p.getName()) == p && /* has not since been reconfigured */ p.getProperty(ServSelJobProperty.class) == t) {
                        categoryProjects.add(p);
                        if (p instanceof MatrixProject && t.isThrottleMatrixConfigurations()) {
                            for (MatrixConfiguration mc : ((MatrixProject) p).getActiveConfigurations()) {
                                categoryProjects.add((AbstractProject<?, ?>) mc);
                            }
                        }
                    }
                }
            }
        }
        return categoryProjects;
    }

    private static Item getItem(ItemGroup group, String name) {
        if (group instanceof Jenkins) {
            return ((Jenkins) group).getItemMap().get(name);
        } else {
            return group.getItem(name);
        }
    }

    @Extension
    public static final class DescriptorImpl extends JobPropertyDescriptor {

        public static final String DELIMETER = "\\r?\\n";
        private String envBranches = "";
        private List<ThrottleCategory> categories = new ArrayList<ThrottleCategory>();
        private List<TargetServer> servers = Collections.synchronizedList(new ArrayList<TargetServer>());
        private List<String> allServersList = updateServerList();
        private Map<Integer, TargetServer> items = new HashMap<Integer, TargetServer>();
        private List<String> environments = Collections.synchronizedList(new ArrayList<String>());
        private Map<String, String> envToBranch = new HashMap<String, String>();
        private Map<String, String> latestByEnviron = new HashMap<String, String>();
        private Map<String, String> taskAssignments = new HashMap<String, String>();
        private Map<String, TargetServer> fullNameAssigns = new HashMap<String, TargetServer>();
        /**
         * Map from category names, to properties including that category.
         */
        private transient Map<String, Map<ServSelJobProperty, Void>> propertiesByCategory
                = new HashMap<String, Map<ServSelJobProperty, Void>>();
        /**
         * A sync object for {@link #propertiesByCategory}
         */
        private final transient Object propertiesByCategoryLock = new Object();

        public DescriptorImpl() {
            super(ServSelJobProperty.class);
            synchronized (propertiesByCategoryLock) {
                load();
                // Explictly handle the persisted data from the version 1.8.1
                if (propertiesByCategory == null) {
                    propertiesByCategory = new HashMap<String, Map<ServSelJobProperty, Void>>();
                }
                if (!propertiesByCategory.isEmpty()) {
                    propertiesByCategory.clear();
                    save(); // Save the configuration to remove obsolete data
                }
            }
        }

        public List<String> updateServerList() {
            List<String> newAllServersList = new ArrayList<String>();
            for (TargetServer ts : servers) {
                newAllServersList.add(ts.getName());
            }
            Collections.sort(newAllServersList);
            newAllServersList.add(0, "First Available Server");
            return newAllServersList;
        }

        public List<String> getAllServersList() {
            allServersList = updateServerList();
            return allServersList;
        }

        public void addToServers(TargetServer targetServer) {
            servers.remove(targetServer);
            servers.add(targetServer);
        }

        public String getEnvBranches() {
            return envBranches;
        }

        public void setEnvBranches(String envBranches) {
            this.envBranches = envBranches;
            parseBranchStringIntoHash(envBranches);
        }

        private void parseBranchStringIntoHash(String envBranches) {
            envBranches = envBranches.trim();
            String[] lines = envBranches.split("\n");
            for (String line : lines) {
                String[] parts = line.split("=");
                String environment = parts[0];
                String branch = parts[1];
                envToBranch.put(environment, branch);
            }
        }

        public String getBranchByEnvironment(String environment) {
            String branch = envToBranch.get(environment);
            if(branch != null){
                return branch;
            }
            return "master";
        }

        public Map<String, TargetServer> getFullAssigns() {
            return fullNameAssigns;
        }

        public void putFullAssignments(String fullName, String server) {
            fullNameAssigns.put(fullName, getTargetServer(server));
        }

        public void removeFullAssignments(String fullName) {
            fullNameAssigns.remove(fullName);
        }

        public TargetServer getFullAssignments(String fullName) {
            return fullNameAssigns.get(fullName);
        }

        public String getLatest(String environ) {
            return latestByEnviron.get(environ);
        }

        public void setLatest(String environ, String latest) {
            latestByEnviron.put(environ, latest);
        }

        public Map<Integer, TargetServer> getItemNumbers() {
            return items;
        }


        public List<String> getEnvironments() {
            if (environments.remove("master")) {
                environments.add(0, "master");
            }
            return environments;
        }

        public void setEnvironments(List<String> newEnvironment) {
            environments = newEnvironment;
        }

        public synchronized String assignServer(String targetServerType, Queue.Item item, String specificTarget) throws IOException, InterruptedException {
            String params = item.getParams().concat("\n");
            String shouldDeploy = "false";
            String version = null;
            String environment = null;

            if (items.containsKey(item.id)) {
                items.remove(item.id);
                return "Server Already Selected";
            }

            if (params.toLowerCase().contains("should_deploy=true")) {
                shouldDeploy = "true";
            }

            if (params.toLowerCase().contains("get_lock=false")) {
                assign(getTargetServer(specificTarget), item, shouldDeploy);
                return specificTarget;
            }
            if (params.contains("ENVIRONMENT=")) {
                int indOfEnviron = params.indexOf("ENVIRONMENT=") + 12;
                environment = params.substring(indOfEnviron, params.indexOf("\n", indOfEnviron));
            }
            if (params.contains("VERSION=")) {
                int indOfVersion = params.indexOf("VERSION=") + 8;
                version = params.substring(indOfVersion, params.indexOf("\n", indOfVersion));
                if (version.equals("latest") && environment != null) {
                    version = getLatest(environment);
                }
            }
            String freeServer;
            if (specificTarget.equals("First Available Server")) {
                freeServer = assignFirstAvailableServer(item, targetServerType, shouldDeploy, version, environment);
            } else {
                freeServer = assignSpecificServer(item, specificTarget, shouldDeploy, version, environment);
            }
            return freeServer;
        }

        public String assignSpecificServer(Queue.Item item, String specificTarget, String shouldDeploy, String version, String environment) {
            String freeServer = null;
            for (TargetServer targetServer : servers) {
                if (targetServer.getName().equals(specificTarget) && !targetServer.isBusy()) {
                    if (shouldDeploy.equals("false")) {
                        shouldDeploy = shouldServerDeploy(targetServer, version, environment);
                    }
                    freeServer = specificTarget;
                    assign(targetServer, item, shouldDeploy);
                    return freeServer;
                }
            }
            return freeServer;
        }

        public String assignFirstAvailableServer(Queue.Item item, String targetServerType, String shouldDeploy, String version, String environment) {
            String freeServer = null;
            boolean foundServer = false;
            for (TargetServer targetServer : servers) {
                if (targetServer.getServerType().equals(targetServerType) && !targetServer.isBusy() && !targetServer.getInUse()) {
                    String needsToDeploy = shouldServerDeploy(targetServer, version, environment);
                    if (needsToDeploy.equals("false")) {
                        foundServer = true;
                        freeServer = targetServer.getName();
                        assign(targetServer, item, shouldDeploy);
                        break;
                    }
                }
            }
            if (!foundServer) {
                for (TargetServer targetServer : servers) {
                    if (targetServer.getServerType().equals(targetServerType) && !targetServer.isBusy() && !targetServer.getInUse()) {
                        freeServer = targetServer.getName();
                        assign(targetServer, item, "true");
                        break;
                    }
                }
            }
            return freeServer;
        }

        public void assign(TargetServer targetServer, Queue.Item item, String shouldDeploy) {
            items.put(item.id, targetServer);
            targetServer.setShouldDeploy(shouldDeploy);
            String serverName = targetServer.getName();
            int num = 1;
            boolean foundTask = false;
            String taskName;
            while (!foundTask) {
                taskName = item.task.getFullDisplayName().replace(" ", "_") + "_" + num;
                if (taskAssignments.get(taskName) == null) {
                    targetServer.setBusy(true);
                    foundTask = true;
                    taskAssignments.put(taskName, serverName);
                } else {
                    num++;
                }
            }

        }

        private String shouldServerDeploy(TargetServer targetServer, String version, String environment) {
            boolean environGood = false;
            boolean versionGood = false;
            if (environment != null) {
                environGood = targetServer.getEnvironment().equals(environment);
            }
            if (version != null) {
                versionGood = targetServer.getVersion().equals(version);
            }
            if (!(environGood && versionGood && targetServer.getLastDeployPassed())) {
                return "true";
            }
            return "false";
        }

        public String UsingServer(String displayName) {
            String taskName;
            int num = 1;
            while (num < 100) {
                taskName = displayName.replace(" ", "_") + "_" + num;
                if (taskAssignments.get(taskName) != null) {
                    return taskAssignments.remove(taskName);
                } else {
                    num++;
                }
            }
            LOGGER.log(Level.SEVERE, "[Server Selection] NO SERVER FOUND for {0}, taskAssignments: {1}", new Object[]{displayName, taskAssignments});
            return null;
        }

        public String getServerAssignment(String server) {
            return getTargetServer(server).getTask();
        }

        public void addServer(TargetServer targetServer) {
            servers.add(targetServer);
        }

        public void removeServer(TargetServer targetServer) {
            servers.remove(targetServer);
        }

        public void releaseServer(TargetServer targetServer) {
            targetServer.setBusy(false);
            targetServer.setTask(null);
        }

        public List<TargetServer> getServers() {
            return servers;
        }

        public Map<String, String> getLatests() {
            return latestByEnviron;
        }

        public TargetServer getTargetServer(String server) {
            for (TargetServer ts : servers) {
                if (ts.getName().equals(server)) {
                    return ts;
                }
            }
            return null;
        }

        @Override
        public String getDisplayName() {
            return "Throttle Concurrent Builds";
        }

        @Override
        @SuppressWarnings("rawtypes")
        public boolean isApplicable(Class<? extends Job> jobType) {
            return AbstractProject.class.isAssignableFrom(jobType);
        }

        public boolean isMatrixProject(Job job) {
            return job instanceof MatrixProject;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            req.bindJSON(this, formData);
            save();
            return true;
        }

        public FormValidation doCheckCategoryName(@QueryParameter String value) {
            if (Util.fixEmptyAndTrim(value) == null) {
                return FormValidation.error("Empty category names are not allowed.");
            } else {
                return FormValidation.ok();
            }
        }

        public FormValidation doCheckMaxConcurrentPerNode(@QueryParameter String value) {
            return checkNullOrInt(value);
        }

        private FormValidation checkNullOrInt(String value) {
            // Allow nulls - we'll just translate those to 0s.
            if (Util.fixEmptyAndTrim(value) != null) {
                return FormValidation.validateNonNegativeInteger(value);
            } else {
                return FormValidation.ok();
            }
        }

        public FormValidation doCheckMaxConcurrentTotal(@QueryParameter String value) {
            return checkNullOrInt(value);
        }

        public ThrottleCategory getCategoryByName(String categoryName) {
            ThrottleCategory category = null;

            for (ThrottleCategory tc : categories) {
                if (tc.getCategoryName().equals(categoryName)) {
                    category = tc;
                }
            }

            return category;
        }

        public void setCategories(List<ThrottleCategory> categories) {
            this.categories = categories;
        }

        public List<String> getCategoryNames() {
            List<String> categoryNames = new ArrayList<String>();
            for (ThrottleCategory category : categories) {
                categoryNames.add(category.getCategoryName());
            }
            return categoryNames;
        }

        public List<ThrottleCategory> getCategories() {
            if (categories == null) {
                categories = new ArrayList<ThrottleCategory>();
            }

            Collections.sort(categories, new Comparator<ThrottleCategory>() {
                public int compare(ThrottleCategory o1, ThrottleCategory o2) {
                    return o1.getCategoryName().compareTo(o2.getCategoryName());
                }
            });

            return categories;
        }

        public ListBoxModel doFillCategoryItems() {
            ListBoxModel m = new ListBoxModel();

            m.add("(none)", "");

            for (ThrottleCategory tc : getCategories()) {
                m.add(tc.getCategoryName());
            }

            return m;
        }

    }

    public static final class ThrottleCategory extends AbstractDescribableImpl<ThrottleCategory> {

        private String categoryName;

        @DataBoundConstructor
        public ThrottleCategory(String categoryName) {
            this.categoryName = categoryName;
        }

        public String getCategoryName() {
            return categoryName;
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<ThrottleCategory> {

            @Override
            public String getDisplayName() {
                return "";
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ServSelQueueTaskDispatcher.class.getName());
}
