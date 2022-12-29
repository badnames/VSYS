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
    public static Optional<String> encrypt(String input, AESParameters parameters)  {
        if (input == null) return Optional.empty();

        try {
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");

            cipher.init(Cipher.ENCRYPT_MODE, parameters.getKey(), parameters.getInitializationVector());

            byte[] cipherText = cipher.doFinal(input.getBytes(StandardCharsets.UTF_8));
            String output = Base64.getEncoder().encodeToString(cipherText);

            return Optional.of(output);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | BadPaddingException |
                 IllegalBlockSizeException | InvalidAlgorithmParameterException e) {
            return Optional.empty();
        }
    }

    public static Optional<String> decrypt(String input, AESParameters parameters)  {
        if (input == null) return Optional.empty();

        try {
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");

            cipher.init(Cipher.DECRYPT_MODE, parameters.getKey(), parameters.getInitializationVector());

            byte[] decodedInput = Base64.getDecoder().decode(input);

            byte[] plainText = cipher.doFinal(decodedInput);
            return Optional.of(new String(plainText, StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | BadPaddingException |
                 IllegalBlockSizeException | InvalidAlgorithmParameterException e) {
            return Optional.empty();
        }
    }

}
