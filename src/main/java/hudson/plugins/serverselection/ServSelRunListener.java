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
                    String shouldDeploy = targetServer != null ? targetServer.getShouldDeploy() : null;
                    if (targetName != null) {
                        env.put("TARGET", targetName);
                        env.put("YAML_TARGET", getYmlTarget(tjp, targetName));
                    }
                    if (shouldDeploy != null) {
                        env.put("DEPLOY", shouldDeploy);
                    }

                }
            }
        };
    }

    @Override
    public void onStarted(AbstractBuild build, TaskListener listener) {
        AbstractProject project = build.getProject();
        Map<String, String> envVars = build.getBuildVariables();
        ServSelJobProperty tjp;
        if (project instanceof MatrixConfiguration) {
            tjp = (ServSelJobProperty) ((AbstractProject) project.getParent()).getProperty(ServSelJobProperty.class);
        } else {
            tjp = (ServSelJobProperty) project.getProperty(ServSelJobProperty.class);
        }
        if (tjp != null && tjp.getThrottleEnabled() && !(project instanceof MatrixProject)) {
            String target = descriptor.UsingServer(getShortName(build));
            descriptor.getTargetServer(target).setTask(build.getFullDisplayName());
            descriptor.putFullAssignments(nameNoSpaces(build), target);
            listener.getLogger().println("[Server Selector] Target server set to " + target);
            listener.getLogger().println("[Server Selector] Version set to " + envVars.get("VERSION"));
            listener.getLogger().println("[Server Selector] Environment set to " + envVars.get("ENVIRONMENT"));
        }
    }

    @Override
    public void onCompleted(AbstractBuild build, TaskListener listener) {
        AbstractProject project = build.getProject();
        Map<String, String> buildVars = build.getBuildVariables();
        ServSelJobProperty tjp;
        if (project instanceof MatrixConfiguration) {
            tjp = (ServSelJobProperty) ((AbstractProject) project.getParent()).getProperty(ServSelJobProperty.class);
        } else {
            tjp = (ServSelJobProperty) project.getProperty(ServSelJobProperty.class);
        }
        if (tjp != null && tjp.getThrottleEnabled() && !(project instanceof MatrixProject)) {
            TargetServer targetServer = descriptor.getFullAssignments(nameNoSpaces(build));
            if (targetServer != null) {
                String targetName = targetServer.getName();
                if (build.getFullDisplayName().contains("Deploy")) {
                    if (build.getResult().isBetterOrEqualTo(Result.SUCCESS)) {
                        targetServer.setLastDeployPassed(true);
                    } else {
                        targetServer.setLastDeployPassed(false);
                    }
                }
                String getLock = (String) buildVars.get("GET_LOCK");
                if (getLock == null || getLock.toLowerCase().equals("false")) {
                    listener.getLogger().println("[Server Selector] Releasing server " + targetName);
                    descriptor.releaseServer(targetName);
                }
                descriptor.removeFullAssignments(nameNoSpaces(build));
            }
        }
    }

    public void onFinalized(AbstractBuild<?, ?> build) {
        TargetServer targetServer = descriptor.getFullAssignments(nameNoSpaces(build));
        if (targetServer != null) {
            descriptor.releaseServer(targetServer.getName());
            descriptor.removeFullAssignments(nameNoSpaces(build));
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
        String firstChar = "" + shortTargetName.charAt(0);
        String nameWithCorrectCase = firstChar.toUpperCase() + shortTargetName.toLowerCase().substring(1);
        if (serverType.charAt(0) == 'H') {
            return nameWithCorrectCase.concat("_cluster");
        } else {
            return nameWithCorrectCase;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ServSelQueueTaskDispatcher.class.getName());
}
