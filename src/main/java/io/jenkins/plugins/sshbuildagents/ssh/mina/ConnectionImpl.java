/*
 * The MIT License
 *
 * Copyright (c) 2016, Michael Clarke
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.jenkins.plugins.sshbuildagents.ssh.mina;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import io.jenkins.plugins.sshbuildagents.ssh.Connection;
import io.jenkins.plugins.sshbuildagents.ssh.ServerHostKeyVerifier;
import io.jenkins.plugins.sshbuildagents.ssh.ShellChannel;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.util.ExceptionUtils;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.scp.client.DefaultScpClient;
import hudson.util.Secret;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

/**
 * Implements {@link Connection} using the Apache Mina SSHD library https://github.com/apache/mina-sshd
 * @author Ivan Fernandez Calvo
 */
public class ConnectionImpl implements Connection{
  public static final long WINDOW_SIZE = 4L * 1024 * 1024;
  public static final int HEARTBEAT_INTERVAL = 30000;
  private OutputStream stdout = System.out;
  private OutputStream stderr = System.err;
  private InputStream stdIn = System.in;
  private SshClient client;
  private ServerHostKeyVerifier hostKeyVerifier;
  private long timeoutMillis = 30000;
  private StandardUsernameCredentials credentials;
  private final String host;
  private final int port;
  private int maxNumRetries = 1;
  private int retryWaitTime = 10;
  private String workingDirectory;
  private boolean tcpNoDelay = true;

  public ConnectionImpl(String host, int port) {
    this.host = host;
    this.port = port;
  }

  @Override
  public int execCommand(String command) throws IOException {
    try(ClientSession session = connect()) {
      session.executeRemoteCommand(command, stdout, stderr, StandardCharsets.UTF_8);
    }
    return 0;
  }

  @Override
  public ShellChannel shellChannel() throws IOException {
    return new ShellChannelImpl(connect());
  }

  /**
   * It adds the authentication details configured to the SSH session.
   * @param session session to add authentication details.
   * @throws IOException in case of error.
   */
  private void addAuthentication(ClientSession session)
    throws IOException {
    try {
      if(credentials instanceof StandardUsernamePasswordCredentials){
        StandardUsernamePasswordCredentials userCredentials = (StandardUsernamePasswordCredentials) this.credentials;
        session.addPasswordIdentity(userCredentials.getPassword().getPlainText());
      } else if (credentials instanceof SSHUserPrivateKey) {
        SSHUserPrivateKey userCredentials = (SSHUserPrivateKey) this.credentials;
        Secret secretPassPhrase = userCredentials.getPassphrase();
        String passphrase = secretPassPhrase == null ? null : secretPassPhrase.getPlainText();

        PrivateKeyParser parser = new PrivateKeyParser();
        for(String key: userCredentials.getPrivateKeys()) {
          Collection<KeyPair> keys = parser.parseKey(key, passphrase);
          keys.forEach(it -> session.addPublicKeyIdentity(it));
        }
      }
    } catch (GeneralSecurityException|URISyntaxException e) {
      throw new IOException(e);
    }
  }

  @Override
  public String getHostname() {
    return this.host;
  }

  @Override
  public int getPort() {
    return this.port;
  }

  @Override
  public void close() {
    client.stop();
  }

  @Override
  public void copyFile(String remotePath, byte[] bytes, boolean overwrite, boolean checkSameContent)
    throws IOException {
    try (ClientSession session = connect()){
      DefaultScpClient scp = new DefaultScpClient(session);
      List<PosixFilePermission> permissions = new ArrayList<>();
      permissions.add(PosixFilePermission.GROUP_READ);
      permissions.add(PosixFilePermission.OWNER_WRITE);
      scp.upload(bytes, remotePath, permissions, null);
    }
  }

  @Override
  public void setServerHostKeyAlgorithms(String[] algorithms) {

  }

  @Override
  public void setTCPNoDelay(boolean tcpNoDelay) {
    this.tcpNoDelay = tcpNoDelay;
  }

  @Override
  public ClientSession connect() throws IOException {
    this.client = SshClient.setUpDefaultClient();
    //client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
    //client.setHostConfigEntryResolver(HostConfigEntryResolver.EMPTY);
    //client.setKeyIdentityProvider(KeyIdentityProvider.EMPTY_KEYS_PROVIDER);
    //CoreModuleProperties.NIO2_READ_TIMEOUT.set(client, READ_TIMEOUT);
    //PropertyResolver propertyResolver = PropertyResolver.EMPTY; //TODO implement
    //SshConfigFileReader.configureKeyExchanges(client, propertyResolver, true, ClientBuilder.DH2KEX, true);
    //SshConfigFileReader.configureSignatures(client, propertyResolver, true, true);
    //SshClientConfigFileReader.setupClientHeartbeat(client, propertyResolver);
    CoreModuleProperties.WINDOW_SIZE.set(client, WINDOW_SIZE);
    CoreModuleProperties.TCP_NODELAY.set(client, tcpNoDelay);
    CoreModuleProperties.HEARTBEAT_INTERVAL.set(client, Duration.ofMillis(HEARTBEAT_INTERVAL));
    client.start();
    for (int i = 0; i <= maxNumRetries; i++) {
      try {
        ConnectFuture connectionFuture = client.connect(this.credentials.getUsername(), this.host, this.port);
        connectionFuture.verify(this.timeoutMillis);
        return authSession(connectionFuture.getSession());
      } catch (Exception ex) {
        String message = getExMessage(ex);
        if (maxNumRetries - i > 0) {
          println(stderr, "SSH Connection failed with IOException: \"" + message
                  + "\", retrying in " + retryWaitTime + " seconds." +
                  " There are " + (maxNumRetries - i) + " more retries left.");
        }
      }
      try {
        Thread.sleep(TimeUnit.SECONDS.toMillis(retryWaitTime));
      } catch (InterruptedException e) {
        throw new IOException(e);
      }
    }
    throw new IOException("Max number or reties reached.");
  }

  /**
   * Parse an exception to print the cause or the error message in the error output and return the message.
   * @param ex Exception to parse.
   * @return
   */
  private String getExMessage(Exception ex) {
    String message = "unknown error";
    Throwable cause = ex.getCause();
    if (cause != null) {
      message = cause.getMessage();
      println(stderr, message);
    } else if(ex.getMessage() != null){
      message = ex.getMessage();
      println(stderr, message);
    }
    return message;
  }

  /**
   * Prints a message in the output passed as parameter.
   * @param out Output to use.
   * @param message Message to write.
   */
  private void println(OutputStream out, String message) {
    if(out instanceof PrintStream) {
      ((PrintStream)out).println(message);
    } else {
      try {
        out.write((message + "\n").getBytes(StandardCharsets.UTF_8));
      } catch (IOException e) {
        //NOOP
      }
    }
  }

  @Override
  public void setServerHostKeyVerifier(ServerHostKeyVerifier verifier) {
  this.hostKeyVerifier = verifier;
  }

  @Override
  public void setTimeout(long timeout) {
    this.timeoutMillis = timeout;
  }

  @Override
  public void setCredentials(StandardUsernameCredentials credentials) {
    this.credentials = credentials;
  }

  @Override
  public void setRetryWaitTime(int time) {
    this.retryWaitTime = time;
  }

  @Override
  public void setRetries(int retries) {
    this.maxNumRetries = retries;
  }

  @Override
  public void setWorkingDirectory(String path) {
    this.workingDirectory = path;
  }

  @Override
  public void setStdErr(OutputStream stderr) {
    this.stderr = stderr;
  }

  @Override
  public void setStdOut(OutputStream stdout) {
    this.stdout = stdout;
  }

  /**
   * Authenticate the session passed as parameter.
   * @param clientSession session to use.
   * @return hte same session passed as parameter after authenticate.
   * @throws IOException
   */
  protected ClientSession authSession(ClientSession clientSession)
    throws IOException {
    ClientSession session = clientSession;
    addAuthentication(session);
    IOException err = null;
    try {
      AuthFuture auth = session.auth();
      auth.verify(this.timeoutMillis);
      session = null; // disable auto-close
    } catch (IOException e) {
      err = ExceptionUtils.accumulateException(err, e);
    } finally {
      err = closeSession(session, err);
    }

    if (err != null) {
      throw err;
    }

    return clientSession;
  }

  /**
   * Close a session after an exception, and return an accumulative exception.
   * @param session session to use.
   * @param err Excetion cause of the close action.
   * @return
   */
  private IOException closeSession(ClientSession session, IOException err) {
    if (session != null) {
      try {
        session.close();
      } catch (IOException e) {
        err = ExceptionUtils.accumulateException(err, e);
      }
    }
    return err;
  }
}