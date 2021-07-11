package io.jenkins.plugins.sshbuildagents.ssh;

import com.cloudbees.plugins.credentials.Credentials;

import java.io.OutputStream;

public class ConnectionImpl implements Connection{
  public ConnectionImpl(String host, int port) {

  }

  @Override
  public int exec(String command, OutputStream stdout) {

    return 0;
  }

  @Override
  public String getHostname() {
    return null;
  }

  @Override
  public int getPort() {
    return 0;
  }

  @Override
  public void close() {

  }

  @Override
  public void copyFile(String fileName, byte[] bytes, boolean overwrite, boolean checkSameContent) {

  }

  @Override
  public void setServerHostKeyAlgorithms(String[] preferredKeyAlgorithms) {

  }

  @Override
  public Session openSession() {
    return null;
  }

  @Override
  public void setTCPNoDelay(boolean tcpNoDelay) {

  }

  @Override
  public void connect(ServerHostKeyVerifier serverHostKeyVerifier, int connectionTimeoutmillis, Credentials credentials) {

  }
}
