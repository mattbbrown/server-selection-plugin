package hudson.plugins.serverselection;

import hudson.plugins.serverselection.ServSelMatrixProjectOptions;
import hudson.plugins.serverselection.ServSelJobProperty;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.security.ACL;
import hudson.security.AuthorizationStrategy;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;

public class ThrottleJobPropertyTest extends HudsonTestCase {

    private static final String THROTTLE_OPTION_CATEGORY = "category"; // TODO move this into ServSelJobProperty and use consistently; same for "project"

    @Bug(19623)
    public void testGetCategoryProjects() throws Exception {
        String alpha = "alpha", beta = "beta", gamma = "gamma"; // category names
        FreeStyleProject p1 = createFreeStyleProject("p1");
        FreeStyleProject p2 = createFreeStyleProject("p2");
        p2.addProperty(new ServSelJobProperty(1, 1, Arrays.asList(alpha), false, THROTTLE_OPTION_CATEGORY, "", ServSelMatrixProjectOptions.DEFAULT));
        FreeStyleProject p3 = createFreeStyleProject("p3");
        p3.addProperty(new ServSelJobProperty(1, 1, Arrays.asList(alpha, beta), true, THROTTLE_OPTION_CATEGORY, "", ServSelMatrixProjectOptions.DEFAULT));
        FreeStyleProject p4 = createFreeStyleProject("p4");
        p4.addProperty(new ServSelJobProperty(1, 1, Arrays.asList(beta, gamma), true, THROTTLE_OPTION_CATEGORY, "", ServSelMatrixProjectOptions.DEFAULT));
        // TODO when core dep ≥1.480.3, add cloudbees-folder as a test dependency so we can check jobs inside folders
        assertProjects(alpha, p3);
        assertProjects(beta, p3, p4);
        assertProjects(gamma, p4);
        assertProjects("delta");
        p4.renameTo("p-4");
        assertProjects(gamma, p4);
        p4.delete();
        assertProjects(gamma);
        AbstractProject<?, ?> p3b = jenkins.<AbstractProject<?, ?>>copy(p3, "p3b");
        assertProjects(beta, p3, p3b);
        p3.removeProperty(ServSelJobProperty.class);
        assertProjects(beta, p3b);
    }

    private void assertProjects(String category, AbstractProject<?, ?>... projects) {
        jenkins.setAuthorizationStrategy(new RejectAllAuthorizationStrategy());
        try {
            assertEquals(new HashSet<AbstractProject<?, ?>>(Arrays.asList(projects)), new HashSet<AbstractProject<?, ?>>(ServSelJobProperty.getCategoryProjects(category)));
        } finally {
            jenkins.setAuthorizationStrategy(AuthorizationStrategy.UNSECURED); // do not check during e.g. rebuildDependencyGraph from delete
        }
    }

    private static class RejectAllAuthorizationStrategy extends AuthorizationStrategy {

        RejectAllAuthorizationStrategy() {
        }

        @Override
        public ACL getRootACL() {
            return new AuthorizationStrategy.Unsecured().getRootACL();
        }

        @Override
        public Collection<String> getGroups() {
            return Collections.emptySet();
        }

        @Override
        public ACL getACL(Job<?, ?> project) {
            fail("not even supposed to be looking at " + project);
            return super.getACL(project);
        }
    }
}
