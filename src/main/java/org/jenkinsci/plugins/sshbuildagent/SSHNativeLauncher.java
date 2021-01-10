package org.jenkinsci.plugins.sshbuildagent;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import hudson.util.ProcessTree;
import hudson.util.StreamCopyThread;
import org.apache.commons.lang.SystemUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.*;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;

public class SSHNativeLauncher extends ComputerLauncher {
  private String host;
  private String port;

  private static class StreamGobbler implements Runnable {
    private InputStream inputStream;
    private Consumer<String> consumer;

    public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
      this.inputStream = inputStream;
      this.consumer = consumer;
    }

    @Override
    public void run() {
      new BufferedReader(new InputStreamReader(inputStream)).lines()
        .forEach(consumer);
    }
  }

  @DataBoundConstructor
  public SSHNativeLauncher() {
  }

  @Override
  public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
    PrintStream logger = listener.getLogger();
    ProcessBuilder builder = new ProcessBuilder();
    Slave node = computer.getNode();
    String workDir = getWorkingDirectory(node);
    String cmd = getCmdSSH() + " 'cd " + workDir + " && pwd && ls && " + getCmdRemote() + getCmdWorkDir(workDir) + "'";
    if (SystemUtils.IS_OS_WINDOWS) {
      logger.println("Jenkins instance running on Windows");
      builder.command("cmd.exe", "/c", cmd);
    } else {
      logger.println("Jenkins instance running on Unix");
      builder.command("sh", "-c", cmd);
    }
    builder.directory(new File(System.getProperty("user.home")));
    logger.println("Starting SSH Build Agent : " + cmd);
    Process process = builder.start();
    //StreamGobbler streamGobbler =
    //  new StreamGobbler(process.getInputStream(), System.out::println);
    //Executors.newSingleThreadExecutor().submit(streamGobbler);
    //int exitCode = process.waitFor();
    //logger.println(process.getInputStream().read());
    //logger.println(process.getErrorStream().read());
    //assert exitCode == 0;
    //computer.setChannel(process.getInputStream(), process.getOutputStream(), listener.getLogger(), null);

    new StreamCopyThread("stderr copier for remote agent on " + computer.getDisplayName(),
      process.getErrorStream(), listener.getLogger()).start();

    computer.setChannel(process.getInputStream(), process.getOutputStream(), listener.getLogger(), null);
    logger.println("SSH Build Agent Started");
  }

  @NonNull
  private String getCmdSSH() {
    return "ssh" + getCmdHost() + getCmdPort();
  }

  @NonNull
  private String getCmdPort() {
    return " -p " + getPort();
  }

  @NonNull
  private String getCmdHost() {
    return " inifc" + "@" + getHost();
  }

  @NonNull
  private String getCmdRemote() {
    return " " + getJava() + " -jar " + "remoting.jar";
  }

  @NonNull
  private String getJava() {
    return "java";
  }

  @NonNull
  private static String getWorkingDirectory(@NonNull Slave agent) {
    String workingDirectory = agent.getRemoteFS();
    while (workingDirectory.endsWith("/")) {
      workingDirectory = workingDirectory.substring(0, workingDirectory.length() - 1);
    }
    return workingDirectory;
  }

  @NonNull
  private static String getCmdWorkDir(@NonNull String workDir) {
    return " -workDir " + workDir;
  }

  @Override
  public void afterDisconnect(SlaveComputer computer, TaskListener listener) {
  }

  @Override
  public void beforeDisconnect(SlaveComputer computer, TaskListener listener) {
  }

  public String getHost() {
    return host;
  }

  @DataBoundSetter
  public void setHost(String host) {
    this.host = host;
  }

  public String getPort() {
    return port;
  }

  @DataBoundSetter
  public void setPort(String port) {
    this.port = port;
  }

  @Extension
  @Symbol({"ssh", "sshNativeLauncher"})
  public static class DescriptorImpl extends Descriptor<ComputerLauncher> {
    public DescriptorImpl(Class<? extends ComputerLauncher> clazz) {
      super(clazz);
    }

    public DescriptorImpl() {
      super();
    }

    @NonNull
    @Override
    public String getDisplayName() {
      return "SSH Native Launcher";
    }
  }
}
