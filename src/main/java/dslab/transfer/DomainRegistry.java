package dslab.transfer;

import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

public class DomainRegistry {
    private final ConcurrentHashMap<String, MailboxAddress> addressMap = new ConcurrentHashMap<>();

    // singleton
    private DomainRegistry() {
    }

    public static synchronized DomainRegistry getInstance() {
        return InstanceHolder.INSTANCE;
    }

    public synchronized void init() {
        var propertyFileName = "domains.properties";

        ResourceBundle bundle = ResourceBundle.getBundle(propertyFileName.substring(0, propertyFileName.length() - 11));

        for (var key : bundle.keySet()) {
            var domain = bundle.getString(key);

            var domainParts = domain.split(":");
            var mailBoxAddress = new MailboxAddress(domainParts[0], Integer.parseInt(domainParts[1]));
            addressMap.put(key, mailBoxAddress);
        }
    }

    public MailboxAddress getAddress(String domain) {
        return addressMap.get(domain);
    }

    public boolean hasAddress(String domain) {
        return addressMap.containsKey(domain);
    }

    private static class InstanceHolder {

        private static final DomainRegistry INSTANCE = new DomainRegistry();
    }
}
