/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.serverselection;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.PeriodicWork;
import hudson.util.TimeUnit2;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/**
 *
 * @author mbrown
 */
@Extension
public class ServSelPeriodicWork extends PeriodicWork {

    @Override
    public long getRecurrencePeriod() {
        return TimeUnit2.MINUTES.toMillis(1);
    }

    @Override
    public long getInitialDelay() {
        ServSelJobProperty.DescriptorImpl descriptor = Jenkins.getInstance().getDescriptorByType(ServSelJobProperty.DescriptorImpl.class);
        if (descriptor != null) {
            descriptor.getFullAssigns().clear();
            descriptor.getServers().clear();
            descriptor.getEnvironments().clear();
            descriptor.getLatests().clear();
            descriptor.getItemNumbers().clear();
        }
        return 0;
    }

    @Override
    protected void doRun() throws Exception {
        ServSelJobProperty.DescriptorImpl descriptor = Jenkins.getInstance().getDescriptorByType(ServSelJobProperty.DescriptorImpl.class);
        if (descriptor != null) {
            updateServerList(descriptor);
            updateEnvironmentList(descriptor);
            updateLatestBuildInfo(descriptor);
            releaseStuckServers(descriptor);
        }
    }

    private void updateServerList(ServSelJobProperty.DescriptorImpl descriptor) throws IOException {
        List<String> serverTypes = descriptor.getCategoryNames();
        String command = "nodes.all{ |n| print n.name + ',' + n.environment + ',';print n['vht']['installed_version'].to_s unless n['vht'] == nil; n.tags.each{|tag| print ',' + tag}; puts ''}";
        BufferedReader reader = createAndExecuteTempFile("jenkinsChefCall.rb", command);
        String line;
        while ((line = reader.readLine()) != null) {
            String[] info = line.split(",");
            String server = info[0], environment = info[1], version = "";
            if (info.length > 2) {
                version = info[2];
            }
            List<String> tags = new ArrayList<String>();
            for (int i = 3; i < info.length; i++) {
                tags.add(info[i]);
            }
            String targetServerType = "NoType";
            for (String serverType : serverTypes) {
                if (tags.contains(serverType)) {
                    targetServerType = serverType;
                    break;
                }
            }
            boolean inUse = tags.contains("in_use");
            TargetServer targetServer = descriptor.getTargetServer(server);
            if (targetServer == null) {
                targetServer = new TargetServer(server, targetServerType, environment, version, inUse);
                LOGGER.log(Level.INFO, "[Server Selection] Added Server {0}", server);
                descriptor.removeServer(targetServer);
                descriptor.addServer(targetServer);
            } else {
                if (!targetServer.getServerType().equals(targetServerType)) {
                    LOGGER.log(Level.INFO, "[Server Selection] {0} server type changed from {1} to {2}", new Object[]{targetServer, targetServer.getServerType(), targetServerType});
                }
                targetServer.setServerType(targetServerType);
                if (!targetServer.getEnvironment().equals(environment)) {
                    LOGGER.log(Level.INFO, "[Server Selection] {0} environment changed from {1} to {2}", new Object[]{targetServer, targetServer.getEnvironment(), environment});
                }
                targetServer.setEnvironment(environment);
                if (!targetServer.getVersion().equals(version)) {
                    LOGGER.log(Level.INFO, "[Server Selection] {0} version changed from {1} to {2}", new Object[]{targetServer, targetServer.getVersion(), version});
                }
                targetServer.setVersion(version);
                if (targetServer.getInUse() == true && inUse == false) {
                    LOGGER.log(Level.INFO, "[Server Selection] in_use tag removed from {0}", targetServer);
                }
                if (targetServer.getInUse() == false && inUse == true) {
                    LOGGER.log(Level.INFO, "[Server Selection] in_use tag added to {0}", targetServer);
                }
                targetServer.setInUse(inUse);
            }
            targetServer.setStillHere(true);
        }
        removeServersNoLongerInChef(descriptor);
    }

    private void removeServersNoLongerInChef(ServSelJobProperty.DescriptorImpl descriptor) {
        List<TargetServer> servers = descriptor.getServers();
        for (int i = 0; i < servers.size(); i++) {
            if (servers.get(i).isStillHere()) {
                servers.get(i).setStillHere(false);
            } else {
                LOGGER.log(Level.INFO, "[Server Selection] Removed Server {0}", servers.get(i));
                servers.remove(i);
                i--;
            }
        }
    }

    private void updateEnvironmentList(ServSelJobProperty.DescriptorImpl descriptor) throws IOException {
        String command = "environments.all{|e|puts(e.name)}";
        BufferedReader reader = createAndExecuteTempFile("getEnvironments.rb", command);
        String line;
        List<String> environments = new ArrayList<String>();
        while ((line = reader.readLine()) != null) {
            if (line.charAt(0) != '_') {
                environments.add(line);
                if (!descriptor.getEnvironments().contains(line)) {
                    LOGGER.log(Level.INFO, "[Server Selection] Added Environment {0}", line);
                }
            }
        }
        descriptor.setEnvironments(environments);
    }

    private void updateLatestBuildInfo(ServSelJobProperty.DescriptorImpl descriptor) throws FileNotFoundException, IOException {
        List<String> environments = descriptor.getEnvironments();
        for (String environ : environments) {
            String command = "environments.find(:name=>'" + environ + "'){|e|attr=e.default_attributes['vht'];puts(attr['build_folder']+','+attr['build_username']+','+attr['build_share'])unless(attr == nil)}";
            BufferedReader reader = createAndExecuteTempFile("jenkinsEnvChefCall.rb", command);
            if (reader != null) {
                String line = reader.readLine();
                if (line == null) {
                    LOGGER.log(Level.SEVERE, "[Server Selection] Could not find a latest build for {0}, latest set to \"unknown\"", environ);
                    descriptor.setLatest(environ, "unknown");
                } else {
                    String[] info = line.split(",");
                    String build_folder = info[0], build_username = info[1];
                    String build_location = "mnt/" + build_username.substring(0, build_username.indexOf("\\")) + "builds/" + build_folder + "/";
                    File file = new File(build_location);
                    final String regex = "\\d+\\.\\d+\\.\\d+\\.\\d+$";
                    String[] directories = file.list(new FilenameFilter() {
                        @Override
                        public boolean accept(File current, String name) {
                            return (new File(current, name).isDirectory()) && name.matches(regex);
                        }
                    });
                    List<String> dirs = directories == null ? null : Arrays.asList(directories);
                    String latestBuild = (dirs == null || dirs.isEmpty()) ? null : dirs.get(dirs.size() - 1);
                    if (latestBuild == null) {
                        LOGGER.log(Level.SEVERE, "[Server Selection] Could not find a latest build for {0}, latest set to \"unknown\"", environ);
                        descriptor.setLatest(environ, "unknown");
                    } else if (descriptor.getLatest(environ) == null || !descriptor.getLatest(environ).equals(latestBuild)) {
                        LOGGER.log(Level.SEVERE, "[Server Selection] Updating {0} latest to {1}", new Object[]{environ, latestBuild});
                        descriptor.setLatest(environ, latestBuild);
                    }
                }
            }
        }
    }

    private BufferedReader createAndExecuteTempFile(String filename, String command) throws FileNotFoundException, IOException {
        createTempFile(filename, command);
        return executeTempFile(filename);
    }

    private void createTempFile(String filename, String command) throws FileNotFoundException {
        Map<String, String> env = System.getenv();
        String jenkins_home = env.get("JENKINS_HOME");
        PrintWriter writer = new PrintWriter(jenkins_home + "/" + filename);
        writer.println(command);
        writer.close();
    }

    private BufferedReader executeTempFile(String filename) throws IOException {
        try {
            Map<String, String> env = System.getenv();
            String jenkins_home = env.get("JENKINS_HOME");
            Process p = Runtime.getRuntime().exec("rvm use ruby-1.9.3-p547@knife do knife exec " + jenkins_home + "/" + filename);
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            try {
                p.waitFor();
            } catch (InterruptedException ex) {
                Logger.getLogger(ServSelPeriodicWork.class.getName()).log(Level.SEVERE, null, ex);
            }
            return reader;
        } catch (IOException e) {
            throw e;
        }
    }

    private void releaseStuckServers(ServSelJobProperty.DescriptorImpl descriptor) {
        for (TargetServer ts : descriptor.getServers()) {
            if (ts.isBusy() && ts.getTask() == null) {
                ts.incrementStuckCounter();
                if (serverHasBeenStuckForXMinutes(1, ts)) {
                    LOGGER.log(Level.SEVERE, "[Server Selection] Releasing apparently stuck server {0}", new Object[]{ts, ts.getTask()});
                    descriptor.releaseServer(ts);
                    ts.zeroStuckCounter();
                }
            } else {
                ts.zeroStuckCounter();
            }
            
            if (ts.getTask() != null && buildNotRunningOnAnyNodes(ts.getTask())) {
                LOGGER.log(Level.SEVERE, "[Server Selection] Releasing apparently stuck server {0} from {1}", new Object[]{ts, ts.getTask()});
                descriptor.releaseServer(ts);
                ts.zeroStuckCounter();
            }
        }
    }

    private boolean buildNotRunningOnAnyNodes(String task) {
        if (buildRunningOnNode(Hudson.getInstance(), task)) {
            return false;
        }
        for (Node node : Hudson.getInstance().getNodes()) {
            if (buildRunningOnNode(node, task)) {
                return false;
            }
        }
        return true;
    }

    private boolean buildRunningOnNode(Node node, String task) {
        Computer computer = node.toComputer();
        if (computer != null) {
            for (Executor e : computer.getExecutors()) {
                if (buildRunningOnExecutor(task, e)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean buildRunningOnExecutor(String task, Executor exec) {
        if (exec.getCurrentExecutable() != null) {
            AbstractBuild<?, ?> build = (AbstractBuild<?, ?>) exec.getCurrentExecutable();
            if (build.getFullDisplayName().equals(task)) {
                return true;
            }
        }
        return false;
    }

    private static final Logger LOGGER = Logger.getLogger(ServSelPeriodicWork.class.getName());

    private boolean serverHasBeenStuckForXMinutes(int minutes, TargetServer ts) {
        int timesStuck = ts.getStuckCounter();
        return timesStuck > minutes;
    }
}
