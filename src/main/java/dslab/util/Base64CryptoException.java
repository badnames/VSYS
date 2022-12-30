package dslab.util;

public class Base64CryptoException extends Exception {
    public Base64CryptoException() {
        super();
    }

    public Base64CryptoException(String msg) {
        super(msg);
    }

    public Base64CryptoException(Exception e) {
        super(e);
    }

}
