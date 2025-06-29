/*
 * The MIT License
 *
 * Copyright (c) 2016,
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

import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.init.Terminator;
import org.apache.sshd.client.ClientBuilder;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.DelegatingServerKeyVerifier;
import org.apache.sshd.common.channel.RequestHandler;
import org.apache.sshd.common.global.KeepAliveHandler;
import org.apache.sshd.common.session.ConnectionService;
import org.apache.sshd.core.CoreModuleProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static io.jenkins.plugins.sshbuildagents.ssh.mina.ConnectionImpl.HEARTBEAT_INTERVAL;
import static io.jenkins.plugins.sshbuildagents.ssh.mina.ConnectionImpl.HEARTBEAT_MAX_RETRY;
import static io.jenkins.plugins.sshbuildagents.ssh.mina.ConnectionImpl.IDLE_SESSION_TIMEOUT;
import static io.jenkins.plugins.sshbuildagents.ssh.mina.ConnectionImpl.WINDOW_SIZE;

public class SshClientProvider {

    private static final SshClient sshClient;
    static {
        ClientBuilder clientBuilder = ClientBuilder.builder();
        clientBuilder.serverKeyVerifier(new DelegatingServerKeyVerifier());
        sshClient = clientBuilder.build();
        List<RequestHandler<ConnectionService>> globalRequestHandlers = sshClient.getGlobalRequestHandlers();
        // npe check and recreate if un modifiable collection
        globalRequestHandlers = (globalRequestHandlers == null) ? new ArrayList<>() : new ArrayList<>(globalRequestHandlers);
        if(globalRequestHandlers.stream().noneMatch(csrh -> csrh instanceof KeepAliveHandler))
        {
            globalRequestHandlers.add(new KeepAliveHandler());
            sshClient.setGlobalRequestHandlers(globalRequestHandlers);
        }
        CoreModuleProperties.WINDOW_SIZE.set(sshClient, WINDOW_SIZE);
        CoreModuleProperties.TCP_NODELAY.set(sshClient, true);
        CoreModuleProperties.IDLE_TIMEOUT.set(sshClient, Duration.ofMinutes(IDLE_SESSION_TIMEOUT));
        sshClient.start();
    }

    public static SshClient getSshClient() {
        if(sshClient == null) {
            throw new  IllegalStateException("SshClient not initialized");
        }
        return sshClient;
    }

//    @Initializer(before = InitMilestone.PLUGINS_STARTED)
//    public static SshClient intSshClient() {
//        if(sshClient != null && !sshClient.isClosed()) {
//            return sshClient;
//        }
//
//
//        return sshClient;
//    }

    @Terminator
    public static void stopSshClient() {
        if(sshClient != null && sshClient.isStarted()) {
            sshClient.stop();
        }
    }

}
