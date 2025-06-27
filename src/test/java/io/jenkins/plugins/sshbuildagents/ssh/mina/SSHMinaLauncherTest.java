package io.jenkins.plugins.sshbuildagents.ssh.mina;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Computer;
import hudson.model.FreeStyleProject;
import hudson.plugins.sshslaves.verifiers.NonVerifyingKeyVerificationStrategy;
import hudson.slaves.DumbSlave;
import hudson.slaves.OfflineCause;
import org.awaitility.Awaitility;
import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.RealJenkinsRule;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

public class SSHMinaLauncherTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public RealJenkinsRule j = new RealJenkinsRule().javaOptions("-Xmx512m")
            .withDebugPort(8000).withDebugServer(true).withDebugSuspend(true);

    static GenericContainer<?> createContainer(String target) {
        return new GenericContainer<>(
                new ImageFromDockerfile("localhost/testcontainers/sshd-" + target, Boolean.parseBoolean(System.getenv("CI"))).
                withFileFromClasspath(".", "/io/jenkins/plugins/sshbuildagents/ssh/mina/simpleImage").
                withTarget(target)).
            withExposedPorts(22);
    }

    /**
     * Tests for the specific issue and also does a smoke test. Creates an agent and checks that the logs exist and we
     * can build on it. It reconnects and checks again the logs exist (this is where the issue was on reconnect we
     * didn't have logs) Then tests we can still build on the agent.
     *
     * @throws Exception
     */
    @Test
    public void logSurvivesReconnections() throws Throwable {

        Assume.assumeTrue("Not docker env here", DockerClientFactory.instance().isDockerAvailable());

        try(GenericContainer<?> sshContainer = createContainer("base")) {
            sshContainer.start();
            String host = sshContainer.getHost();
            int port = sshContainer.getMappedPort(22);
            j.then( j -> {

                SystemCredentialsProvider.getInstance().getDomainCredentialsMap().put(Domain.global(),
                        Collections.singletonList(new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM, "simpleCredentials",
                                null, "foo", "beer")));


                SSHApacheMinaLauncher launcher = new SSHApacheMinaLauncher(host, port, "simpleCredentials");
                launcher.setJavaPath("/usr/java/latest/bin/java");
                launcher.setSshHostKeyVerificationStrategy(new NonVerifyingKeyVerificationStrategy());

                DumbSlave agent = new DumbSlave("agent" + j.jenkins.getNodes().size(),"/home/foo/agent", launcher);
                j.jenkins.addNode(agent);

                Computer computer = agent.toComputer();
                try {
                    computer.connect(false).get();
                } catch (ExecutionException x) {
                    throw new AssertionError("failed to connect: " + computer.getLog(), x);
                }

                assertThat(computer.getLog(), containsString("Agent successfully connected and online"));

                FreeStyleProject p = j.createFreeStyleProject();
                p.setAssignedNode(agent);

                try {
                    computer.disconnect(OfflineCause.create(null)).get();
                } catch (ExecutionException x) {
                    throw new AssertionError("failed to disconnect: " + computer.getLog(), x);
                }

                //Wait for the real disconnections
                Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> computer.getLog().contains("Connection terminated"));

                try {
                    computer.connect(true).get();
                } catch (ExecutionException x) {
                    throw new AssertionError("failed to connect: " + computer.getLog(), x);
                }

                assertThat(computer.getLog(), containsString("Agent successfully connected and online"));
                j.buildAndAssertSuccess(p);
            });
        }
    }
}
