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
public class TargetServer implements Comparable<TargetServer> {

    private final String name;
    private String serverType;
    private String task;
    private String environment;
    private String version;
    private String shouldDeploy;
    private boolean inUse;
    private boolean busy;
    private boolean stillHere;
    private boolean lastDeployPassed;

    public TargetServer(String name) {
        this.name = name;
        busy = false;
        lastDeployPassed = true;
    }

    public TargetServer(String name, String serverType, String environment, String version, boolean inUse) {
        this.name = name;
        this.serverType = serverType;
        this.environment = environment;
        this.version = version;
        this.inUse = inUse;
        busy = false;
        lastDeployPassed = true;
    }

    public void setInUse(boolean inUse) {
        this.inUse = inUse;
    }

    public boolean getInUse() {
        return inUse;
    }

    public boolean isStillHere() {
        return stillHere;
    }

    public void setStillHere(boolean stillHere) {
        this.stillHere = stillHere;
    }

    public boolean isBusy() {
        return busy;
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
    }

    public boolean getLastDeployPassed() {
        return lastDeployPassed;
    }

    public void setLastDeployPassed(boolean lastDeployPassed) {
        this.lastDeployPassed = lastDeployPassed;
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

    public String getEnvironment() {
        return environment;
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

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        boolean result = false;
        if (o != null && o instanceof TargetServer) {
            TargetServer ts = (TargetServer) o;
            if (name != null && ts != null) {
                result = name.equals(ts.getName());
            }
        }
        return result;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 17 * hash + (this.name != null ? this.name.hashCode() : 0);
        return hash;
    }

    @Override
    public int compareTo(TargetServer ts) {
        return name.compareTo(ts.getName());
    }
}
