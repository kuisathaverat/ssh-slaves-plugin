package io.jenkins.plugins.agent.ssh;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.jenkinsci.Symbol;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import static io.jenkins.plugins.agent.ssh.Messages.*;

public class SSHMinaLauncher extends ComputerLauncher {
  private static final Logger LOGGER = Logger.getLogger(SSHMinaLauncher.class.getName());

  /**
   * Optional environment variables to add to the current environment. Can be null.
   */
  private final EnvVars env = null;
  /**
   * sets up the server key verifier.
   * RejectAllServerKeyVerifier - rejects all server key - usually used in tests or as a fallback verifier if none of it predecesors validated the server key
   * RequiredServerKeyVerifier - accepts only one specific server key.
   * KnownHostsServerKeyVerifier - uses the known_hosts file to validate the server key.
   */
  private ServerKeyVerifier serverKeyVerifier = AcceptAllServerKeyVerifier.INSTANCE;
  /**
   * Username to use in the connection.
   */
  private String user = "inifc";
  private String host = "127.0.0.1";
  private int port = 22;
  private long timeout = 120;
  private String command = "java --version";
  private Charset terminalCharset = StandardCharsets.UTF_8;

  /**
   * Launch an Agent using the ssh client from commandline.
   * @param computer computer object.
   * @param listener Task listener.
   * @throws AbortException
   */
  public void launch( /*SlaveComputer computer, TaskListener listener*/) throws IOException{
    Process _proc = null;
    EnvVars _cookie = null;
    /*
    Slave node = computer.getNode();
    if (node == null) {
      throw new AbortException("Cannot launch commands on deleted nodes");
    }

    listener.getLogger().println(SSHNativeLauncher_Launching(getTimestamp()));
*/
    SshClient client = SshClient.setUpDefaultClient();
    client.setServerKeyVerifier(serverKeyVerifier);
    client.start();

    try (ClientSession session = client.connect(user, host, port).verify(timeout, TimeUnit.SECONDS).getSession()) {
      //session.addPasswordIdentity(...password..); // for password-based authentication
      //session.addPublicKeyIdentity(...key-pair...); // for password-less authentication
      session.auth().verify(timeout, TimeUnit.SECONDS);
      ByteArrayOutputStream stdout = new ByteArrayOutputStream();
      ByteArrayOutputStream stderr = new ByteArrayOutputStream();
      session.executeRemoteCommand(command, stdout, stderr, terminalCharset);

      /*
          // In order to override the PTY and/or environment
          Map<String, ?> env = ...some environment...
          PtyChannelConfiguration ptyConfig = ...some configuration...
          try (ClientChannel channel = session.createShellChannel(ptyConfig, env)) {
              ... same code as before ...
          }
       */
      ChannelShell sshChannel = session.createShellChannel();
/*
      computer.setChannel(sshChannel.getIn(),
                          sshChannel.getOut(),
                          listener.getLogger(),
                          new Channel.Listener() {
                            @Override
                            public void onClosed(Channel channel, IOException cause) {
                              LOGGER.log(Level.SEVERE, SSHNativeLauncher_unableToLaunchTheAgent(cause.getMessage()),
                                         cause);
                              if (client != null) {
                                client.stop();
                              }
                            }
                          });*/
    } catch (IOException e) {
      String msg = SSHNativeLauncher_unableToLaunchTheAgent(e.getMessage());
      LOGGER.log(Level.SEVERE, msg, e);
      throw new IOException(msg, e);
    } finally {
      if(client != null){
        client.stop();
      }
    }
  }

  /**
   * Gets the formatted current time stamp.
   */
  private static String getTimestamp() {
    return String.format("[%1$tD %1$tT]", new Date());
  }

  /**
   * Descriptor.
   */
  @Extension
  @Symbol("ssh-native")
  public static class DescriptorImpl extends Descriptor<ComputerLauncher> {
    public String getDisplayName() {
      return SSHNativeLauncher_displayName();
    }
  }
}
