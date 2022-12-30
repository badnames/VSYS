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
import java.util.Optional;

public class Base64AES {
    public static String encrypt(String input, AESParameters parameters) throws Base64CryptoException {
        if (input == null) throw new Base64CryptoException("Input is null!");

        try {
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");

            cipher.init(Cipher.ENCRYPT_MODE, parameters.getKey(), parameters.getInitializationVector());

            byte[] cipherText = cipher.doFinal(input.getBytes(StandardCharsets.UTF_8));
            String output = Base64.getEncoder().encodeToString(cipherText);

            return output;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | BadPaddingException |
                 IllegalBlockSizeException | InvalidAlgorithmParameterException e) {
            throw new Base64CryptoException(e);
        }
    }

    public static String decrypt(String input, AESParameters parameters) throws Base64CryptoException {
        if (input == null) throw new Base64CryptoException("Input is null!");

        try {
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");

            cipher.init(Cipher.DECRYPT_MODE, parameters.getKey(), parameters.getInitializationVector());

            byte[] decodedInput = Base64.getDecoder().decode(input);

            byte[] plainText = cipher.doFinal(decodedInput);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | BadPaddingException |
                 IllegalBlockSizeException | InvalidAlgorithmParameterException e) {
            throw new Base64CryptoException(e);
        }
    }

}
