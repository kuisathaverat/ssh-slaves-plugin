package hudson.plugins.sshslaves.verifiers;

import com.trilead.ssh2.signature.KeyAlgorithm;
import com.trilead.ssh2.signature.KeyAlgorithmManager;
import hudson.plugins.sshslaves.Messages;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import com.trilead.ssh2.signature.SSHSignature;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Michael Clarke
 */
// TODO move this to KeyAlgorithmManager
@Restricted(NoExternalUse.class)
class JenkinsTrilead9VersionSupport {

    public String[] getSupportedAlgorithms() {
        List<String> algorithms = new ArrayList<>();
        for (SSHSignature algorithm : KeyAlgorithmManager.getSupportedAlgorithms()) {
            algorithms.add(algorithm.getKeyFormat());
        }
        return algorithms.toArray(new String[0]);
    }

    public HostKey parseKey(String algorithm, byte[] keyValue) throws KeyParseException {
        for (SSHSignature keyAlgorithm : KeyAlgorithmManager.getSupportedAlgorithms()) {
            try {
                if (keyAlgorithm.getKeyFormat().equals(algorithm)) {
                    keyAlgorithm.decodePublicKey(keyValue);
                    return new HostKey(algorithm, keyValue);
                }
            } catch (IOException ex) {
                throw new KeyParseException(Messages.ManualKeyProvidedHostKeyVerifier_KeyValueDoesNotParse(algorithm), ex);
            }
        }
        throw new KeyParseException("Unexpected key algorithm: " + algorithm);
    }
}
