/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.serverselection;

/**
 *
 * @author mbrown
 */
public class TargetServer {

    private final String name;
    private String serverType;
    private String task;
    private String build;
    private String version;
    private String shouldDeploy;

    public TargetServer(String name, String serverType) {
        this.name = name;
        this.serverType = serverType;
    }

    public String getShouldDeploy() {
        return shouldDeploy;
    }

    public void setShouldDeploy(String shouldDeploy) {
        this.shouldDeploy = shouldDeploy;
    }

    public String getName() {
        return name;
    }

    public String getServerType() {
        return serverType;
    }

    public String getTask() {
        return task;
    }

    public String getBuild() {
        return build;
    }

    public String getVersion() {
        return version;
    }

    public void setServerType(String serverType) {
        this.serverType = serverType;
    }

    public void setTask(String task) {
        this.task = task;
    }

    public void setBuild(String build) {
        this.build = build;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String toString() {
        return name;
    }
}
