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
                    List<String> serverList = new ArrayList<String>();
                    Process p;
                    try {
                        Runtime R = Runtime.getRuntime();
                        p = R.exec(new String[] {"rvm", "ruby-1.9.3-p547@knife", "do", "knife", "search", "tags:" + targetServerType, "-i"});
                        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                        p.waitFor();

                        String server;
                        reader.readLine();
                        reader.readLine();
                        while ((server = reader.readLine()) != null) {
                            serverList.add(server);
                            descriptor.setServerType(server, targetServerType);
                        }
                            LOGGER.log(Level.SEVERE,"{0} servers from Chef: {1}",new Object[] {targetServerType, serverList});
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

    private static final Logger LOGGER = Logger.getLogger(ServSelQueueTaskDispatcher.class.getName());
}
