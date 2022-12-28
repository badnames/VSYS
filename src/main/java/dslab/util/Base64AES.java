package dslab.util;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class Base64AES {
    public static String encrypt(String input, AESParameters parameters) throws NoSuchPaddingException,
            NoSuchAlgorithmException,
            InvalidAlgorithmParameterException,
            InvalidKeyException,
            IllegalBlockSizeException,
            BadPaddingException {
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, parameters.getKey(), parameters.getInitializationVector());

        byte[] decodedInput = Base64.getDecoder().decode(input);

        byte[] plainText = cipher.doFinal(decodedInput);
        return new String(plainText, StandardCharsets.UTF_8);
    }

    public static String decrypt(String input, AESParameters parameters) throws NoSuchPaddingException,
            NoSuchAlgorithmException,
            InvalidAlgorithmParameterException,
            InvalidKeyException,
            IllegalBlockSizeException,
            BadPaddingException {
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, parameters.getKey(), parameters.getInitializationVector());

        byte[] decodedInput = Base64.getDecoder().decode(input);

        byte[] cipherText = cipher.doFinal(decodedInput);
        return Base64.getEncoder().encodeToString(cipherText);
    }

}
