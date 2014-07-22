/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.serverselection;

import hudson.Extension;
import hudson.Launcher;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.model.*;
import hudson.model.listeners.RunListener;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author mbrown
 */
@Extension
public final class ServSelRunListener extends RunListener<AbstractBuild> {

    Map<String, String> assignments = Collections.synchronizedMap(new HashMap<String, String>());

    public ServSelRunListener() {
        super(AbstractBuild.class);
    }

    @Override
    public Environment setUpEnvironment(final AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException, Run.RunnerAbortedException {
        return new Environment() {
            @Override
            public void buildEnvVars(Map<String, String> env) {
                AbstractProject project = build.getProject();
                ServSelJobProperty tjp;
                if (project instanceof MatrixConfiguration) {
                    tjp = (ServSelJobProperty) ((AbstractProject) project.getParent()).getProperty(ServSelJobProperty.class);
                } else {
                    tjp = (ServSelJobProperty) project.getProperty(ServSelJobProperty.class);
                }
                if (tjp != null) {
                    ServSelJobProperty.DescriptorImpl descriptor = (ServSelJobProperty.DescriptorImpl) tjp.getDescriptor();
                    String target = descriptor.UsingServer(getShortName(build));
                    assignments.put(build.getFullDisplayName(), target);
                    env.put("TARGET", target);
                    env.put("YAML_TARGET", getYmlTarget(tjp, target));
                    descriptor.removeFromAssignments(target);
                }
            }
        };
    }

    @Override
    public void onStarted(AbstractBuild build, TaskListener listener) {
        AbstractProject project = build.getProject();
        ServSelJobProperty tjp;
        if (project instanceof MatrixConfiguration) {
            tjp = (ServSelJobProperty) ((AbstractProject) project.getParent()).getProperty(ServSelJobProperty.class);
        } else {
            tjp = (ServSelJobProperty) project.getProperty(ServSelJobProperty.class);
        }
        if (tjp != null && !(project instanceof MatrixProject)) {
            String target = assignments.get(build.getFullDisplayName());
            listener.getLogger().println("[Server Selector] Target server set to " + target);
        }
    }

    @Override
    public void onCompleted(AbstractBuild build, TaskListener listener) {
        AbstractProject project = build.getProject();
        ServSelJobProperty tjp;
        if (project instanceof MatrixConfiguration) {
            tjp = (ServSelJobProperty) ((AbstractProject) project.getParent()).getProperty(ServSelJobProperty.class);
        } else {
            tjp = (ServSelJobProperty) project.getProperty(ServSelJobProperty.class);
        }
        if (tjp != null && !(project instanceof MatrixProject)) {
            ServSelJobProperty.DescriptorImpl descriptor = (ServSelJobProperty.DescriptorImpl) tjp.getDescriptor();
            String target = assignments.get(build.getFullDisplayName());
            listener.getLogger().println("[Server Selector] Releasing server " + target);
            if (build.getBuildVariables().get("GET_LOCK") == null || ((String) build.getBuildVariables().get("GET_LOCK")).toLowerCase().equals("yes")) {
                descriptor.releaseServer(target);
            }
            assignments.remove(build.getFullDisplayName());
        }
    }

    private String getShortName(AbstractBuild build) {
        String fullName = build.getFullDisplayName();
        int poundIndex = fullName.indexOf('#');
        String shortName = fullName.substring(0, poundIndex - 1);
        return shortName;
    }

    private String getYmlTarget(ServSelJobProperty tjp, String target) {
        String serverType = tjp.getCategories().get(0);
        String shortTargetName = target.substring(0, target.indexOf('.'));
        if (serverType.charAt(0) == 'H') {
            return shortTargetName.concat("_cluster");
        } else {
            return shortTargetName;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ServSelQueueTaskDispatcher.class.getName());
}
