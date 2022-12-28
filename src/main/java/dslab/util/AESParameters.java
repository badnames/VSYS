package dslab.util;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AESParameters {
    private final SecretKey key;
    private final IvParameterSpec initializationVector;

    public AESParameters(SecretKey key, IvParameterSpec initializationVector) {
        this.key = key;
        this.initializationVector = initializationVector;
    }

    public SecretKey getKey() {
        return key;
    }

    public IvParameterSpec getInitializationVector() {
        return initializationVector;
    }
}
