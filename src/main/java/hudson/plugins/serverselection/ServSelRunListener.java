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
import hudson.plugins.serverselection.ServSelJobProperty.DescriptorImpl;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/**
 *
 * @author mbrown
 */
@Extension
public final class ServSelRunListener extends RunListener<AbstractBuild> {

    DescriptorImpl descriptor = Jenkins.getInstance().getDescriptorByType(DescriptorImpl.class);

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
                if (tjp != null && tjp.getThrottleEnabled()) {
                    TargetServer targetServer = descriptor.getFullAssignments(nameNoSpaces(build));
                    String targetName = targetServer != null ? targetServer.getName() : null;
                    LOGGER.log(Level.SEVERE, "target: {0}", targetName);
                    if (targetName != null) {
                        env.put("TARGET", targetName);
                        env.put("YAML_TARGET", getYmlTarget(tjp, targetName));
                    }
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
        if (tjp != null && tjp.getThrottleEnabled() && !(project instanceof MatrixProject)) {
            String target = descriptor.UsingServer(getShortName(build));
            descriptor.putFullAssignments(nameNoSpaces(build), target);
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
        if (tjp != null && tjp.getThrottleEnabled() && !(project instanceof MatrixProject)) {
            TargetServer targetServer = descriptor.getFullAssignments(nameNoSpaces(build));
            String targetName = targetServer.getName();
            String getLock = (String) build.getBuildVariables().get("GET_LOCK");
            if (getLock == null || getLock.toLowerCase().equals("yes")) {
                listener.getLogger().println("[Server Selector] Releasing server " + targetName);
                descriptor.releaseServer(targetName);
                descriptor.removeFullAssignments(nameNoSpaces(build));
            }
        }
    }

    private String nameNoSpaces(AbstractBuild build) {
        return build.getFullDisplayName().replace(" ", "_");
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
