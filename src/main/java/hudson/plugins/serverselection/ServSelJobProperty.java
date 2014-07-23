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
import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.Hudson;
import hudson.model.Queue;
import hudson.model.Queue.Task;
import hudson.model.TaskListener;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
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

    private Integer maxConcurrentPerNode;
    private Integer maxConcurrentTotal;
    private List<String> categories;
    private boolean throttleEnabled;
    private String throttleOption;
    private transient boolean throttleConfiguration;
    private @CheckForNull
    ServSelMatrixProjectOptions matrixOptions;
    private String target;

    /**
     * Store a config version so we're able to migrate config on various
     * functionality upgrades.
     */
    private Long configVersion;

    @DataBoundConstructor
    public ServSelJobProperty(Integer maxConcurrentPerNode,
            Integer maxConcurrentTotal,
            List<String> categories,
            boolean throttleEnabled,
            String throttleOption,
            String target,
            @CheckForNull ServSelMatrixProjectOptions matrixOptions
    ) {
        this.maxConcurrentPerNode = maxConcurrentPerNode == null || maxConcurrentPerNode == 0 ? 1 : maxConcurrentPerNode;
        this.maxConcurrentTotal = maxConcurrentTotal == null || maxConcurrentTotal == 0 ? 1 : maxConcurrentTotal;
        this.categories = categories == null ? new ArrayList<String>() : categories;
        this.throttleEnabled = throttleEnabled;
        this.throttleOption = throttleOption == null ? "category" : throttleOption;
        this.matrixOptions = matrixOptions;
        this.target = target == null ? "First Available Server" : target;
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

        if (configVersion < 1 && throttleOption == null) {
            if (categories.isEmpty()) {
                throttleOption = "project";
            } else {
                throttleOption = "category";
                maxConcurrentPerNode = 0;
                maxConcurrentTotal = 0;
            }
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
        if (throttleEnabled && categories != null) {
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

    public String getTarget() {
        if (target == null) {
            target = "First Available Server";
        }
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public boolean getThrottleEnabled() {
        return throttleEnabled;
    }

    public String getThrottleOption() {
        return throttleOption;
    }

    public List<String> getCategories() {
        return categories;
    }

    public Integer getMaxConcurrentPerNode() {
        if (maxConcurrentPerNode == null) {
            maxConcurrentPerNode = 0;
        }

        return maxConcurrentPerNode;
    }

    public Integer getMaxConcurrentTotal() {
        if (maxConcurrentTotal == null) {
            maxConcurrentTotal = 0;
        }

        return maxConcurrentTotal;
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
            if (t.getThrottleEnabled()) {
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
        private List<ThrottleCategory> categories = new ArrayList<ThrottleCategory>();
        private List<String> allServersList = updateServerList();
        private List<Integer> items = new ArrayList<Integer>();
        private boolean simple;
        private Map<String, TargetServer> allTargetServers = new HashMap<String, TargetServer>();
        private Map<String, List<TargetServer>> allServersByType = new HashMap<String, List<TargetServer>>();
        private Map<String, List<TargetServer>> allFreeServers = new HashMap<String, List<TargetServer>>();
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
            newAllServersList.add("First Available Server");
            for (ThrottleCategory category : categories) {
                String serverType = category.getCategoryName();
                List<TargetServer> serverOfType = allServersByType.get(serverType);
                for (TargetServer servers : serverOfType) {
                    newAllServersList.add(servers.getName());
                }
            }
            return newAllServersList;
        }

        public List<String> getAllServersList() {
            allServersList = updateServerList();
            return allServersList;
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

        public synchronized String assignServer(String targetServerType, Queue.Item item, String specificTarget) throws IOException, InterruptedException {
            Task task = item.task;
            String params = item.getParams().concat("\n");
            //String build = null, version = null;

            if (items.contains(item.id)) {
                items.remove(items.indexOf(item.id));
                return "Server Already Selected";
            }
            LOGGER.log(Level.SEVERE, "item parameters for {0}: {1}", new Object[]{item.id, item.getParams()});
            if (params.toLowerCase().contains("get_lock=no")) {
                assign(specificTarget, task);
                items.add(item.id);
                return specificTarget;
            }
            /*
             SPECIFIC BUILD/VERSION CODE FOR NEXT VERSION
             if (params.contains("BUILD=")) {
             int indOfBuild = params.indexOf("BUILD=") + 7;
             build = params.substring(indOfBuild, params.indexOf("\n", indOfBuild));
             }

             if (params.contains("VERSION=")) {
             int indOfVersion = params.indexOf("VERSION=") + 7;
             version = params.substring(indOfVersion, params.indexOf("\n", indOfVersion));
             }
             */
            String freeServer = null;
            if (specificTarget.equals("First Available Server")) {
                List<TargetServer> servers = allFreeServers.get(targetServerType);
                if (servers == null) {
                    allFreeServers.put(targetServerType, allServersByType.get(targetServerType));
                    servers = allFreeServers.get(targetServerType);
                }
                if (!servers.isEmpty()) {
                    freeServer = servers.remove(0).getName();
                    assign(freeServer, task);
                    items.add(item.id);
                }
                /*
                 SPECIFIC BUILD/VERSION CODE FOR NEXT VERSION
                 for (TargetServer targetServer : servers) {
                 boolean buildGood = true;
                 boolean versionGood = true;
                 if (build != null) {
                 buildGood = targetServer.getBuild().equals(build);
                 }
                 if (version != null) {
                 versionGood = targetServer.getVersion().equals(version);
                 }
                 if (buildGood && versionGood) {
                 freeServer = servers.get(servers.indexOf(targetServer)).getName();
                 assign(freeServer, task);
                 items.add(item.id);
                 break;
                 }
                 }
                 */
            } else {
                for (ThrottleCategory category : categories) {
                    String serverType = category.getCategoryName();
                    List<TargetServer> servers = allFreeServers.get(serverType);
                    if (servers == null) {
                        allFreeServers.put(serverType, allServersByType.get(serverType));
                        servers = allFreeServers.get(serverType);
                    }
                    for (TargetServer targetServer : servers) {
                        if (targetServer.getName().equals(specificTarget)) {
                            freeServer = specificTarget;
                            servers.remove(targetServer);
                            assign(freeServer, task);
                            items.add(item.id);
                            break;
                        }
                    }
                }
            }
            return freeServer;
        }

        public void assign(String freeServer, Task task) {
            int num = 1;
            boolean foundTask = false;
            String taskName = null;
            while (!foundTask) {
                taskName = task.getFullDisplayName().replace(" ", "_") + "_num_" + num;
                if (taskAssignments.get(taskName) == null) {
                    foundTask = true;
                } else {
                    num++;
                }
            }
            LOGGER.log(Level.SEVERE, "task name: {0}", taskName);
            taskAssignments.put(taskName, freeServer);
            LOGGER.log(Level.SEVERE, "taskAssignments: {0}", taskAssignments);
        }

        public void setServers(String targetServerType, List<TargetServer> servers) {
            allServersByType.put(targetServerType, servers);
        }

        public String UsingServer(String displayName) {
            int num = 1;
            boolean foundServer = false;
            String taskName = null;
            while (num < 10) {
                taskName = displayName.replace(" ", "_") + "_num_" + num;
                if (taskAssignments.get(taskName) != null) {
                    foundServer = true;
                    break;
                } else {
                    num++;
                }
            }
            if (!foundServer) {
                LOGGER.log(Level.SEVERE, "NO SERVER FOUND for {0}, taskAssignments: {1}", new Object[]{displayName, taskAssignments});
            }
            String server = taskAssignments.remove(taskName);
            return server;
        }

        public String getServerAssignment(String server) {
            return getTargetServer(server).getTask();
        }

        public void releaseServer(String server) {
            TargetServer targetServer = getTargetServer(server);
            targetServer.setTask(null);
            List<TargetServer> servers = allFreeServers.get(targetServer.getServerType());
            servers.add(targetServer);
        }

        public TargetServer getTargetServer(String server) {
            return allTargetServers.get(server);
        }

        public void setTargetServer(TargetServer targetServer) {
            allTargetServers.put(targetServer.getName(), targetServer);
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

        public void setSimple(boolean simple) {
            this.simple = simple;
        }

        public boolean getSimple() {
            return simple;
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

        private Integer maxConcurrentPerNode;
        private Integer maxConcurrentTotal;
        private String categoryName;
        private List<NodeLabeledPair> nodeLabeledPairs;

        @DataBoundConstructor
        public ThrottleCategory(String categoryName,
                Integer maxConcurrentPerNode,
                Integer maxConcurrentTotal,
                List<NodeLabeledPair> nodeLabeledPairs) {
            this.maxConcurrentPerNode = maxConcurrentPerNode == null || maxConcurrentPerNode == 0 ? 1 : maxConcurrentPerNode;
            this.maxConcurrentTotal = maxConcurrentTotal == null || maxConcurrentTotal == 0 ? 1 : maxConcurrentTotal;
            this.categoryName = categoryName;
            this.nodeLabeledPairs
                    = nodeLabeledPairs == null ? new ArrayList<NodeLabeledPair>() : nodeLabeledPairs;
        }

        public Integer getMaxConcurrentPerNode() {
            if (maxConcurrentPerNode == null || maxConcurrentPerNode == 0) {
                maxConcurrentPerNode = 1;
            }

            return maxConcurrentPerNode;
        }

        public Integer getMaxConcurrentTotal() {
            if (maxConcurrentTotal == null || maxConcurrentTotal == 0) {
                maxConcurrentTotal = 1;
            }

            return maxConcurrentTotal;
        }

        public String getCategoryName() {
            return categoryName;
        }

        public List<NodeLabeledPair> getNodeLabeledPairs() {
            if (nodeLabeledPairs == null) {
                nodeLabeledPairs = new ArrayList<NodeLabeledPair>();
            }

            return nodeLabeledPairs;
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<ThrottleCategory> {

            @Override
            public String getDisplayName() {
                return "";
            }
        }
    }

    /**
     * @author marco.miller@ericsson.com
     */
    public static final class NodeLabeledPair extends AbstractDescribableImpl<NodeLabeledPair> {

        private String throttledNodeLabel;
        private Integer maxConcurrentPerNodeLabeled;

        @DataBoundConstructor
        public NodeLabeledPair(String throttledNodeLabel,
                Integer maxConcurrentPerNodeLabeled) {
            this.throttledNodeLabel = throttledNodeLabel == null ? new String() : throttledNodeLabel;
            this.maxConcurrentPerNodeLabeled
                    = maxConcurrentPerNodeLabeled == null || maxConcurrentPerNodeLabeled == 0 ? new Integer(1) : maxConcurrentPerNodeLabeled;
        }

        public String getThrottledNodeLabel() {
            if (throttledNodeLabel == null) {
                throttledNodeLabel = new String();
            }
            return throttledNodeLabel;
        }

        public Integer getMaxConcurrentPerNodeLabeled() {
            if (maxConcurrentPerNodeLabeled == null || maxConcurrentPerNodeLabeled == 0) {
                maxConcurrentPerNodeLabeled = new Integer(1);
            }
            return maxConcurrentPerNodeLabeled;
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<NodeLabeledPair> {

            @Override
            public String getDisplayName() {
                return "";
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ServSelQueueTaskDispatcher.class
            .getName());
}
