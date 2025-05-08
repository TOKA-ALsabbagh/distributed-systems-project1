package common;
import java.util.*;

public class TokenManager {
    private static Map<String, User> tokens = new HashMap<>();

    public static String generateToken(User user) {
        String token = UUID.randomUUID().toString();
        tokens.put(token, user);
        return token;
    }

    public static User validateToken(String token) {
        return tokens.get(token);
    }

    public static void removeToken(String token) {
        tokens.remove(token);
    }
}
