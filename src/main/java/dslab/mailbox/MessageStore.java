package dslab.mailbox;

import dslab.util.Message;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MessageStore {

    // This class is a singleton
    private MessageStore() {
    }

    /*
        This solution takes advantage of the Java memory model's guarantees
        about class initialization to ensure thread safety. Each class can
        only be loaded once, and it will only be loaded when it is needed.
        That means that the first time getInstance is called,
        InstanceHolder will be loaded and instance will be created, and
        since this is controlled by ClassLoaders, no additional
        synchronization is necessary.

        (Explaination by user mfb on Stackoverflow
         URL: https://stackoverflow.com/questions/11165852/java-singleton-and-synchronization)
     */
    private static class InstanceHolder {
        private static final MessageStore INSTANCE = new MessageStore();
    }

    public static synchronized MessageStore getInstance() {
        return InstanceHolder.INSTANCE;
    }

    // Keys: usernames, Values: message queues
    private ConcurrentHashMap<String, ConcurrentHashMap<Integer, Message>> userMessageMap;
    private ConcurrentHashMap<String, String> userPasswordMap;

    private final Object lastIdLock = new Object();
    private int lastId = 0;

    public void init(HashMap<String, String> userPasswordMap) {
        this.userPasswordMap = new ConcurrentHashMap<>(userPasswordMap);
        this.userMessageMap = new ConcurrentHashMap<>();

        this.userPasswordMap.forEach((username, password) -> this.userMessageMap.put(
                username,
                new ConcurrentHashMap<>())
        );
    }

    public boolean hasUser(String username) {
        return this.userPasswordMap.containsKey(username);
    }

    public boolean isPasswordCorrect(String username, String password) {
        return this.userPasswordMap.get(username).equals(password);
    }

    public void putMessage(String username, Message message) {
        if (!hasUser(username)) {
            throw new IllegalArgumentException("User " + username + " does not exist!");
        }

        synchronized (lastIdLock) {
            this.userMessageMap.get(username).put(lastId, message);
            lastId++;
        }
    }

    public Message getMessage(String username, int id) {
        if (!hasUser(username)) {
            throw new IllegalArgumentException("user " + username + " does not exist");
        }

        if (!this.userMessageMap.get(username).containsKey(id)) {
            throw new IllegalArgumentException("message does not exist");
        }

        return this.userMessageMap.get(username).get(id);
    }

    public Map<Integer, Message> getAllMessagesReadOnly(String username) {
        var list =  this.userMessageMap.get(username);
        return Collections.unmodifiableMap(list);
    }

    public void deleteMessage(String username, int id) {
        if (!hasUser(username)) {
            throw new IllegalArgumentException("User " + username + " does not exist!");
        }

        if (!this.userMessageMap.get(username).containsKey(id)) {
            throw new IllegalArgumentException("message does not exist");
        }

        this.userMessageMap.get(username).remove(id);
    }
}
