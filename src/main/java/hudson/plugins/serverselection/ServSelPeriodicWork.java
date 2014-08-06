/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.serverselection;

import hudson.Extension;
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
        return 0;
    }

    @Override
    protected void doRun() throws Exception {
        ServSelJobProperty.DescriptorImpl descriptor = Jenkins.getInstance().getDescriptorByType(ServSelJobProperty.DescriptorImpl.class);
        if (descriptor != null) {
            updateServerList(descriptor);
            updateEnvironmentList(descriptor);
            updateLatestBuildInfo(descriptor);
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
                LOGGER.log(Level.INFO, "Added Server {0}", server);
                descriptor.addServer(targetServer);
            } else {
                targetServer.setServerType(targetServerType);
                targetServer.setEnvironment(environment);
                targetServer.setVersion(version);
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
                String[] info = line.split(",");
                String build_folder = info[0], build_username = info[1];
                String build_location = "mnt/" + build_username.substring(0, build_username.indexOf("\\")) + "builds/" + build_folder + "/";
                File file = new File(build_location);
                final String regex = "\\d+\\.\\d+\\.\\d+\\.\\d+$";
                String[] directories = file.list(new FilenameFilter() {
                    @Override
                    public boolean accept(File current, String name) {
                        String[] fileNames = new File(current, name).list();
                        for (String subFileName : fileNames) {
                            if (subFileName.contains(name)) {
                                return (new File(current, name).isDirectory()) && name.matches(regex);
                            }
                        }
                        return false;
                    }
                });
                List<String> dirs = Arrays.asList(directories);
                String latestBuild = dirs.get(dirs.size() - 1);
                if (!descriptor.getLatest(environ).equals(latestBuild)) {
                    LOGGER.log(Level.SEVERE, "Updating {0} latest to {1}", new Object[]{environ, latestBuild});
                }
                descriptor.setLatest(environ, latestBuild);
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
            return null;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ServSelPeriodicWork.class.getName());
}
