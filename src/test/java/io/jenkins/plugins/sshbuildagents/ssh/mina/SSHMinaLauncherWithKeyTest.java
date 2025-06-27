package io.jenkins.plugins.sshbuildagents.ssh.mina;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.model.Computer;
import hudson.model.FreeStyleProject;
import hudson.plugins.sshslaves.verifiers.NonVerifyingKeyVerificationStrategy;
import hudson.slaves.DumbSlave;
import hudson.slaves.OfflineCause;
import org.apache.commons.io.IOUtils;
import org.awaitility.Awaitility;
import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.RealJenkinsRule;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

public class SSHMinaLauncherWithKeyTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public RealJenkinsRule j = new RealJenkinsRule().javaOptions("-Xmx512m")
        .withDebugPort(8000).withDebugServer(true).withDebugSuspend(true);

    @Test
    public void sshConnectWithKey() throws Throwable {

        Assume.assumeTrue("Not docker env here", DockerClientFactory.instance().isDockerAvailable());

        try(GenericContainer<?> sshContainer = SSHMinaLauncherTest.createContainer("base")) {
            sshContainer.start();
            String host = sshContainer.getHost();
            int port = sshContainer.getMappedPort(22);
            String privateKey = IOUtils.toString(Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResourceAsStream("rsa2048")), StandardCharsets.UTF_8);
            j.then( j -> {

                SystemCredentialsProvider.getInstance().getDomainCredentialsMap().put(Domain.global(),
                        Collections.singletonList(new BasicSSHUserPrivateKey(CredentialsScope.SYSTEM, "simpleCredentials", "foo", new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(privateKey), "theaustraliancricketteamisthebest", null)));

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

                FreeStyleProject p = j.jenkins.createProject(FreeStyleProject.class, "p");
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
