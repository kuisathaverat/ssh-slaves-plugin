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
package hudson.plugins.sshslaves.verifiers;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.plugins.sshslaves.Messages;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.slaves.SlaveComputer;
import hudson.util.FormValidation;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.apache.sshd.client.keyverifier.RejectAllServerKeyVerifier;
import org.apache.sshd.client.keyverifier.RequiredServerKeyVerifier;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Checks a key provided by a remote hosts matches a key specified as being required by the
 * user that configured this strategy. This would be equivalent of someone manually setting a
 * value in their known hosts file before attempting an SSH connection on a Unix/Linux machine.
 * @author Michael Clarke
 * @since 1.13
 */
public class ManuallyProvidedKeyVerificationStrategy extends SshHostKeyVerificationStrategy {

    private static final Logger LOGGER = Logger.getLogger(ManuallyProvidedKeyVerificationStrategy.class.getName());

    private final HostKey key;

    private final String rawKey;

    @DataBoundConstructor
    public ManuallyProvidedKeyVerificationStrategy(String key) {
        super();
        try {
            this.rawKey = key;
            this.key = parseKey(key);
        } catch (KeyParseException e) {
            throw new IllegalArgumentException("Invalid key: " + e.getMessage(), e);
        }
    }

    public String getKey() {
        return key.getAlgorithm() + " " + Base64.getEncoder().encodeToString(key.getKey());
    }

    public HostKey getParsedKey() {
        return key;
    }

    @Override
    public boolean verify(SlaveComputer computer, HostKey hostKey, TaskListener listener) throws Exception {
        if (key.equals(hostKey)) {
            listener.getLogger()
                    .println(Messages.ManualKeyProvidedHostKeyVerifier_KeyTrusted(SSHLauncher.getTimestamp()));
            return true;
        } else {
            listener.getLogger()
                    .println(Messages.ManualKeyProvidedHostKeyVerifier_KeyNotTrusted(SSHLauncher.getTimestamp()));
            return false;
        }
    }

    @Override
    public String[] getPreferredKeyAlgorithms(SlaveComputer computer) throws IOException {
        String[] unsortedAlgorithms = super.getPreferredKeyAlgorithms(computer);
        List<String> sortedAlgorithms = new ArrayList<>(
                unsortedAlgorithms != null ? Arrays.asList(unsortedAlgorithms) : Collections.emptyList());

        sortedAlgorithms.remove(key.getAlgorithm());
        sortedAlgorithms.add(0, key.getAlgorithm());

        return sortedAlgorithms.toArray(new String[0]);
    }

    private static HostKey parseKey(String key) throws KeyParseException {
        if (!key.contains(" ")) {
            throw new IllegalArgumentException(Messages.ManualKeyProvidedHostKeyVerifier_TwoPartKey());
        }
        StringTokenizer tokenizer = new StringTokenizer(key, " ");
        String algorithm = tokenizer.nextToken();
        byte[] keyValue = Base64.getDecoder().decode(tokenizer.nextToken());
        if (null == keyValue) {
            throw new KeyParseException(Messages.ManualKeyProvidedHostKeyVerifier_Base64EncodedKeyValueRequired());
        }

        return TrileadVersionSupportManager.getTrileadSupport().parseKey(algorithm, keyValue);
    }

    @Override
    public ServerKeyVerifier getServerKeyVerifier() {
        AuthorizedKeyEntry entry = AuthorizedKeyEntry.parseAuthorizedKeyEntry(this.rawKey);
        if (entry != null) {
            try {
                final PublicKey expected = entry.resolvePublicKey(null, PublicKeyEntryResolver.IGNORING);
                LOGGER.log(Level.FINE, () -> "PublicKey expected " + expected);
                return new RequiredServerKeyVerifier(expected);
            } catch (GeneralSecurityException | IOException e) {
                // it's not really fine, but this could spam logs, and the previous logging was nothing.
                LOGGER.log(
                        Level.FINE,
                        "Error on the configured server key format, all keys are rejected. Please "
                                + "verify the ssh server key.",
                        e);
            }
        } else {
            LOGGER.log(
                    Level.FINE,
                    "No server key configured, all keys are rejected. This seems to be a missed "
                            + "configuration, please verify the ssh server key.");
        }
        // either key is null or with a bad format, rejecting all.
        return RejectAllServerKeyVerifier.INSTANCE;
    }

    @Extension
    public static class ManuallyProvidedKeyVerificationStrategyDescriptor
            extends SshHostKeyVerificationStrategyDescriptor {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.ManualKeyProvidedHostKeyVerifier_DisplayName();
        }

        @RequirePOST
        public FormValidation doCheckKey(@QueryParameter String key) {
            Jenkins.get().checkPermission(Computer.CONFIGURE);
            try {
                ManuallyProvidedKeyVerificationStrategy.parseKey(key);
                return FormValidation.ok();
            } catch (KeyParseException | IllegalArgumentException ex) {
                return FormValidation.error(ex.getMessage());
            }
        }
    }
}
