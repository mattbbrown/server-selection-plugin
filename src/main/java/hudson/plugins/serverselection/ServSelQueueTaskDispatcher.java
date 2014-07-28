package hudson.plugins.serverselection;

import hudson.Extension;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractProject;
import hudson.model.Queue;
import hudson.model.Queue.Task;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueTaskDispatcher;
import java.io.IOException;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

@Extension
public class ServSelQueueTaskDispatcher extends QueueTaskDispatcher {

    @Override
    public CauseOfBlockage canRun(Queue.Item item) {
        try {
            Task task = item.task;
            ServSelJobProperty tjp = getThrottleJobProperty(task);
            if (!shouldBeThrottled(item, tjp)) {
                return null;
            }

            String targetServerType = tjp.getCategories().get(0);
            String serverTaken;
            String params = item.getParams().concat("\n");
            String specificServer = "First Available Server";
            if (params.contains("TARGET=")) {
                int indOfTarget = params.indexOf("TARGET=") + 7;
                specificServer = params.substring(indOfTarget, params.indexOf("\n", indOfTarget));
            }
            if (task instanceof MatrixConfiguration && task.getFullDisplayName().toLowerCase().contains("deploy")) {
                specificServer = task.getDisplayName();
            }
            ServSelJobProperty.DescriptorImpl descriptor = (ServSelJobProperty.DescriptorImpl) tjp.getDescriptor();
            serverTaken = descriptor.assignServer(targetServerType, item, specificServer);
            if (serverTaken == null && !specificServer.equals("First Available Server")) {
                String taskUsingServer = descriptor.getServerAssignment(specificServer);
                if (taskUsingServer == null) {
                    taskUsingServer = "No Task";
                }
                return CauseOfBlockage.fromMessage(Messages._ThrottleQueueTaskDispatcher_SpecificServerBusy(specificServer, taskUsingServer));
            }
            if (serverTaken == null) {
                return CauseOfBlockage.fromMessage(Messages._ThrottleQueueTaskDispatcher_NoFreeServers(targetServerType));
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Exception raised in canRun: ", e);
        }
        return null;
    }

    private boolean shouldBeThrottled(@Nonnull Queue.Item item, @CheckForNull ServSelJobProperty tjp) throws IOException, InterruptedException {
        Task task = item.task;
        if (tjp == null) {
            return false;
        }
        if (!tjp.getThrottleEnabled()) {
            return false;
        }

        if (task instanceof MatrixProject) {
            return false;
        }
        
        return true;
    }

    @CheckForNull
    private ServSelJobProperty getThrottleJobProperty(Task task) {
        if (task instanceof AbstractProject) {
            AbstractProject<?, ?> p = (AbstractProject<?, ?>) task;
            if (task instanceof MatrixConfiguration) {
                p = (AbstractProject<?, ?>) ((MatrixConfiguration) task).getParent();
            }
            ServSelJobProperty tjp = p.getProperty(ServSelJobProperty.class);
            return tjp;
        }
        return null;
    }

    private static final Logger LOGGER = Logger.getLogger(ServSelQueueTaskDispatcher.class.getName());
}
