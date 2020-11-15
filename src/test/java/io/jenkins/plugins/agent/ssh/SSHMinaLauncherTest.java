package io.jenkins.plugins.agent.ssh;

import java.io.IOException;
import java.io.PrintStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.slaves.DumbSlave;
import hudson.slaves.SlaveComputer;

public class SSHMinaLauncherTest {

  /*
  @Rule
  public JenkinsRule j = new JenkinsRule();
*/
  @Rule
  public TemporaryFolder folder= new TemporaryFolder();

  @Test
  public void retryTest() throws IOException, InterruptedException, Descriptor.FormException {
    final SSHMinaLauncher launcher = new SSHMinaLauncher();
    launcher.launch();
    //DumbSlave agent = new DumbSlave("agent",folder.newFolder().getPath(), launcher);
    //j.jenkins.addNode(agent);
    //Thread.sleep(25000);
    //String log = agent.getComputer().getLog();
  }
}
