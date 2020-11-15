package io.jenkins.plugins.agent.ssh;

import java.io.IOException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import hudson.util.ProcessTree;
import hudson.util.StreamCopyThread;

public class SSHNativeLauncher extends ComputerLauncher {
  private static final Logger LOGGER = Logger.getLogger(SSHNativeLauncher.class.getName());

  /**
   * Optional environment variables to add to the current environment. Can be null.
   */
  private final EnvVars env = null;

  /**
   * Launch an Agent using the ssh client from commandline.
   * @param computer computer object.
   * @param listener Task listener.
   * @throws AbortException
   */
  public void launch(SlaveComputer computer, TaskListener listener) throws AbortException{
    Process _proc = null;
    EnvVars _cookie = null;
    Slave node = computer.getNode();
    if (node == null) {
      throw new AbortException("Cannot launch commands on deleted nodes");
    }

    listener.getLogger().println(io.jenkins.plugins.agent.ssh.Messages.SSHNativeLauncher_Launching(getTimestamp()));
    ProcessBuilder pb = new ProcessBuilder(Util.tokenize("java --version"));
    try {
      // add ssh key 'ssh-add - <<< "$(cat $HOME/.ssh/id_rsa)"'
      final EnvVars cookie = _cookie = EnvVars.createCookie();
      pb.environment().putAll(cookie);
      pb.environment().put("WORKSPACE", StringUtils.defaultString(computer.getAbsoluteRemoteFs(), node.getRemoteFS())); //path for local slave log
      if (env != null) {
        pb.environment().putAll(env);
      }

      final Process proc = _proc = pb.start();

      // capture error information from stderr. this will terminate itself
      // when the process is killed.
      new StreamCopyThread("stderr copier for remote agent on " + computer.getDisplayName(),
                           proc.getErrorStream(), listener.getLogger()).start();

      computer.setChannel(proc.getInputStream(), proc.getOutputStream(), listener.getLogger(), new Channel.Listener() {
        @Override
        public void onClosed(Channel channel, IOException cause) {
          reportProcessTerminated(proc, listener);

          try {
            ProcessTree.get().killAll(proc, cookie);
          } catch (InterruptedException e) {
            LOGGER.log(Level.INFO, "interrupted", e);
          }
        }
      });

      LOGGER.info("agent launched for " + computer.getDisplayName());
    } catch (IOException | InterruptedException e) {
      listener.error( io.jenkins.plugins.agent.ssh.Messages.SSHNativeLauncher_unableToLaunchTheAgent(e.getMessage())
        , e);
    }

  }

  /**
   * Report the exis code of the native command.
   * @param proc process.
   * @param listener Task listener.
   */
  private static void reportProcessTerminated(Process proc, TaskListener listener) {
    try {
      int exitCode = proc.exitValue();
      listener.error("Process terminated with exit code " + exitCode);
    } catch (IllegalThreadStateException e) {
      // hasn't terminated yet
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
      return io.jenkins.plugins.agent.ssh.Messages.SSHNativeLauncher_displayName();
    }
  }
}
