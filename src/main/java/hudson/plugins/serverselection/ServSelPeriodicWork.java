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
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
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
        return TimeUnit2.MINUTES.toMillis(5);
    }

    @Override
    protected void doRun() throws Exception {
        ServSelJobProperty.DescriptorImpl descriptor = Jenkins.getInstance().getDescriptorByType(ServSelJobProperty.DescriptorImpl.class);
        if (descriptor != null) {
            List<String> serverTypes = descriptor.getCategoryNames();
            if (serverTypes != null && !serverTypes.isEmpty()) {
                for (String targetServerType : serverTypes) {
                    List<TargetServer> serverList = new ArrayList<TargetServer>();
                    Process p;
                    try {
                        Runtime R = Runtime.getRuntime();
                        String command = "rvm use ruby-1.9.3-p547@knife do knife exec -E \"nodes.find(:tags =>\\\"SA_Windows_2012\\\"){|n|puts(n.name+\\\",\\\"+n.environment+\\\",\\\"+n[\\\"vht\\\"][\\\"installed_version\\\"]+\\\",\\\"+n.tags.include?(\\\"in_use\\\").to_s)}\"";                        
                        String test = "rvm use ruby-1.9.3-p547@knife do knife exec -E \"puts(\\\"HelloWorld\\\")\"";
                        String[] cmd = {"rvm", "ruby-1.9.3-p547@knife", "do", "knife", "search", "tags:" + targetServerType, "-i"};
                        p = R.exec(cmd);
                        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                        p.waitFor();

                        String server;
                        reader.readLine();
                        reader.readLine();
                        while ((server = reader.readLine()) != null) {
                            TargetServer targetServer = descriptor.getTargetServer(server);
                            if (targetServer == null) {
                                targetServer = new TargetServer(server, targetServerType);
                            }
                            targetServer.setBuild("master");
                            targetServer.setVersion("not-latest");
                            targetServer.setServerType(targetServerType);
                            descriptor.setTargetServer(targetServer);
                            serverList.add(targetServer);
                        }
                        LOGGER.log(Level.SEVERE, "{0} servers from Chef: {1}", new Object[]{targetServerType, serverList});
                        descriptor.setServers(targetServerType, serverList);
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Chef call exception raised: {0}", e);
                    }
                }
            }
        }
    }

    @Override
    public long getInitialDelay() {
        return 0;
    }

    private static final Logger LOGGER = Logger.getLogger(ServSelPeriodicWork.class.getName());
}
