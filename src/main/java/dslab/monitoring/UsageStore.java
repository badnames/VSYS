package dslab.monitoring;

import java.util.concurrent.ConcurrentHashMap;

public class UsageStore {

    private ConcurrentHashMap<String, Integer> addressAccessMap;
    private ConcurrentHashMap<String, Integer> serverAccessMap;

    // Singleton
    private UsageStore() {
    }

    public static synchronized UsageStore getInstance() {
        return InstanceHolder.INSTANCE;
    }

    public void init() {
        serverAccessMap = new ConcurrentHashMap<>();
        addressAccessMap = new ConcurrentHashMap<>();
    }

    // Synchronized to prevent race conditions when two
    // threads try to increment a server access separately.
    public synchronized void addServerAccess(String address) {
        var accessCount = 0;

        if (serverAccessMap.containsKey(address)) {
            accessCount = serverAccessMap.get(address);
        }

        serverAccessMap.put(address, accessCount + 1);
    }

    public String serverAccessesToString() {
        StringBuilder accessString = new StringBuilder();
        for (var key : serverAccessMap.keySet()) {
            accessString.append(key).append(" ").append(serverAccessMap.get(key));
        }

        return accessString.toString();
    }

    public synchronized void addAddressAccess(String address) {
        var accessCount = 0;

        if (addressAccessMap.containsKey(address)) {
            accessCount = addressAccessMap.get(address);
        }

        addressAccessMap.put(address, accessCount + 1);
    }

    public String addressAccessesToString() {
        StringBuilder accessString = new StringBuilder();
        for (var key : addressAccessMap.keySet()) {
            accessString.append(key).append(" ").append(addressAccessMap.get(key)).append("\n");
        }

        return accessString.toString();
    }

    private static class InstanceHolder {
        private static final UsageStore INSTANCE = new UsageStore();
    }
}
