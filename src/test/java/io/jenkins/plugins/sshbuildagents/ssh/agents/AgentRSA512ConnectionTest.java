package io.jenkins.plugins.sshbuildagents.ssh.agents;

import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.plugins.sshslaves.categories.AgentSSHTest;
import hudson.plugins.sshslaves.categories.SSHKeyAuthenticationTest;
import static org.junit.Assert.assertTrue;

/**
 * Connect to a remote SSH Agent
 *
 * @author Kuisathaverat
 */
@Category({ AgentSSHTest.class, SSHKeyAuthenticationTest.class})
public class AgentRSA512ConnectionTest extends AgentConnectionBase {
  public static final String SSH_AGENT_NAME = "ssh-agent-rsa512";
  public static final String SSH_KEY_PATH = "ssh/rsa-512-key";
  public static final String SSH_KEY_PUB_PATH = "ssh/rsa-512-key.pub";
  public static final String LOGGING_PROPERTIES = "remoting_logger.properties";

  @Rule
  public GenericContainer agentContainer = new GenericContainer(
    new ImageFromDockerfile(SSH_AGENT_NAME, false)
      .withFileFromClasspath(SSH_AUTHORIZED_KEYS, AGENTS_RESOURCES_PATH + "/" + SSH_AGENT_NAME + "/" + SSH_AUTHORIZED_KEYS)
      .withFileFromClasspath(SSH_KEY_PATH, AGENTS_RESOURCES_PATH + "/" + SSH_AGENT_NAME + "/" + SSH_KEY_PATH)
      .withFileFromClasspath(SSH_KEY_PUB_PATH, AGENTS_RESOURCES_PATH + "/" + SSH_AGENT_NAME + "/" + SSH_KEY_PUB_PATH)
      .withFileFromClasspath(SSH_SSHD_CONFIG, AGENTS_RESOURCES_PATH + "/" + SSH_AGENT_NAME + "/" + SSH_SSHD_CONFIG)
      .withFileFromClasspath(DOCKERFILE, AGENTS_RESOURCES_PATH + "/" + SSH_AGENT_NAME + "/" + DOCKERFILE)
      .withFileFromClasspath("ssh/" + LOGGING_PROPERTIES, "/" + LOGGING_PROPERTIES))
      .withExposedPorts(22);

  @Test
  public void connectionTests() throws IOException, InterruptedException, Descriptor.FormException {
    Node node = createPermanentAgent(SSH_AGENT_NAME, agentContainer.getHost(), agentContainer.getMappedPort(SSH_PORT),
    SSH_AGENT_NAME + "/" + SSH_KEY_PATH, "");
    waitForAgentConnected(node);
    assertTrue(isSuccessfullyConnected(node));
  }

  @Test
  public void longConnectionTests() throws IOException, InterruptedException, Descriptor.FormException {
    Node node = createPermanentAgent(SSH_AGENT_NAME, agentContainer.getHost(), agentContainer.getMappedPort(SSH_PORT),
                                     SSH_AGENT_NAME + "/" + SSH_KEY_PATH, "");
    waitForAgentConnected(node);
    assertTrue(isSuccessfullyConnected(node));
    for(int i=0;i<300;i++){
      Thread.sleep(1000);
      assertTrue(node.toComputer().isOnline());
    }
  }

}
