package hudson.plugins.serverselection;

import hudson.Extension;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Queue.Task;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueTaskDispatcher;
import hudson.plugins.throttleconcurrents.Messages;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

@Extension
public class ServSelQueueTaskDispatcher extends QueueTaskDispatcher {

    @Override
    public CauseOfBlockage canRun(Queue.Item item) {
        Task task = item.task;
        ServSelJobProperty tjp = getThrottleJobProperty(task);
        if (!shouldBeThrottled(task, tjp)) {
            return null;
        }
        String targetServerType = tjp.getCategories().get(0);
        ServSelJobProperty.DescriptorImpl descriptor = (ServSelJobProperty.DescriptorImpl) tjp.getDescriptor();
        String serverTaken = descriptor.assignFirstFreeServer(targetServerType, task);
        if (serverTaken == null) {
            return CauseOfBlockage.fromMessage(Messages._ThrottleQueueTaskDispatcher_NoFreeServers(targetServerType));
        }
        return null;
    }

    @Nonnull
    private ServSelMatrixProjectOptions getMatrixOptions(Task task) {
        ServSelJobProperty tjp = getThrottleJobProperty(task);
        if (tjp == null) {
            return ServSelMatrixProjectOptions.DEFAULT;
        }
        ServSelMatrixProjectOptions matrixOptions = tjp.getMatrixOptions();
        return matrixOptions != null ? matrixOptions : ServSelMatrixProjectOptions.DEFAULT;
    }

    private boolean shouldBeThrottled(@Nonnull Task task, @CheckForNull ServSelJobProperty tjp) {
        if (tjp == null) {
            return false;
        }
        if (!tjp.getThrottleEnabled()) {
            return false;
        }

        ServSelMatrixProjectOptions matrixOptions = tjp.getMatrixOptions();
        if (matrixOptions == null) {
            matrixOptions = ServSelMatrixProjectOptions.DEFAULT;
        }
        if (!matrixOptions.isThrottleMatrixConfigurations() && task instanceof MatrixConfiguration) {
            return false;
        }
        if (!matrixOptions.isThrottleMatrixBuilds() && task instanceof MatrixProject) {
            return false;
        }

        return true;
    }

    private boolean serverIsFree(String serverName) {
        boolean serverFree = true;
        if (serverIsBeingUsedOnNode(Hudson.getInstance(), serverName)) {
            serverFree = false;
        } else {
            for (Node node : Hudson.getInstance().getNodes()) {
                if (serverIsBeingUsedOnNode(node, serverName)) {
                    serverFree = false;
                }
            }
        }
        return serverFree;
    }

    private boolean serverIsBeingUsedOnNode(Node node, String serverName) {
        boolean serverBeingUsedOnNode = false;
        Computer computer = node.toComputer();
        if (computer != null) {
            for (Executor e : computer.getExecutors()) {
                if (buildOnExecutorUsingServer(serverName, e)) {
                    serverBeingUsedOnNode = true;
                    break;
                }
            }
        }
        return serverBeingUsedOnNode;
    }

    private boolean buildOnExecutorUsingServer(String serverName, Executor exec) {
        if (exec.getCurrentExecutable() != null) {
            Task task = exec.getCurrentExecutable().getParent().getOwnerTask();
            ServSelJobProperty tjp = getThrottleJobProperty(task);
            if (tjp != null) {
                ServSelJobProperty.DescriptorImpl descriptor = (ServSelJobProperty.DescriptorImpl) tjp.getDescriptor();
                return descriptor.UsingServer(task).equals(serverName);
            }
        }
        return false;
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
