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
        boolean newServersAdded = false;
        List<String> newServers = new ArrayList<String>();
        if (serverTypes != null && !serverTypes.isEmpty()) {
            for (String targetServerType : serverTypes) {
                List<TargetServer> serverList = new ArrayList<TargetServer>();
                try {
                    String command = "nodes.find(:tags =>'" + targetServerType + "'){|n|puts(n.name+','+n.environment+','+n['vht']['installed_version']+','+n.tags.include?('in_use').to_s)}";
                    BufferedReader reader = createAndExecuteTempFile("jenkinsChefCall.rb", command);
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] info = line.split(",");
                        String server = info[0], build = info[1], version = info[2], inUse = info[3];
                        TargetServer targetServer = descriptor.getTargetServer(server);
                        if (targetServer == null) {
                            newServersAdded = true;
                            newServers.add(server);
                            targetServer = new TargetServer(server, targetServerType);
                        }
                        targetServer.setBuild(build);
                        targetServer.setVersion(version);
                        targetServer.setInUse(inUse);
                        descriptor.setTargetServer(targetServer);
                        serverList.add(targetServer);
                    }

                    descriptor.setServers(targetServerType, serverList);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "[Server Selection] Chef call exception raised: ", e);
                }
            }
        }
        String command = "nodes.all{|n|puts(n.name)}";
        BufferedReader reader = createAndExecuteTempFile("jenkinsAllNodes.rb", command);
        List<TargetServer> noTypeServers = new ArrayList<TargetServer>();
        String server;
        while ((server = reader.readLine()) != null) {
            TargetServer targetServer = descriptor.getTargetServer(server);
            if (targetServer == null) {
                newServersAdded = true;
                newServers.add(server);
                targetServer = new TargetServer(server, "NoType");
            }
            if (targetServer.getServerType().equals("NoType")) {
                descriptor.setTargetServer(targetServer);
                noTypeServers.add(targetServer);
            }
        }
        if (newServersAdded) {
            LOGGER.log(Level.INFO, "[Server Selection] New Servers Added: {0}", newServers.toArray());
        }
        descriptor.setServers("NoType", noTypeServers);
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
                        return (new File(current, name).isDirectory()) && name.matches(regex);
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
