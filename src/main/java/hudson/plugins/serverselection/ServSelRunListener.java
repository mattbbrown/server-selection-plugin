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
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author mbrown
 */
@Extension
public final class ServSelRunListener extends RunListener<AbstractBuild> {

    String target = "";

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
                    target = descriptor.UsingServer(getShortName(build));
                    env.put("TARGET", target);
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
            ServSelJobProperty.DescriptorImpl descriptor = (ServSelJobProperty.DescriptorImpl) tjp.getDescriptor();
            target = descriptor.UsingServer(getShortName(build));
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
            target = descriptor.UsingServer(getShortName(build));
            listener.getLogger().println("[Server Selector] Releasing server " + target);
            descriptor.releaseServer(getShortName(build));
        }
    }

    private String getShortName(AbstractBuild build) {
        String fullName = build.getFullDisplayName();
        int poundIndex = fullName.indexOf('#');
        String shortName = fullName.substring(0, poundIndex - 1);
        return shortName;
    }

    private static final Logger LOGGER = Logger.getLogger(ServSelQueueTaskDispatcher.class.getName());
}
