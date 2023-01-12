package dslab.nameserver;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class NameserverStore {
    private final ConcurrentHashMap<String, String> mailboxes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, INameserverRemote> subZones = new ConcurrentHashMap<>();

    // Singleton
    private NameserverStore() {
    }

    public static synchronized NameserverStore getInstance() {
        return NameserverStore.InstanceHolder.INSTANCE;
    }

    public void addSubZone(String domain, INameserverRemote remote) {
        subZones.put(domain, remote);
    }

    public void deleteSubZone(String domain) {
        subZones.remove(domain);
    }

    public INameserverRemote getSubZone(String domain) {
        return subZones.get(domain);
    }

    public Set<String> getKnownSubZones() {
        return subZones.keySet();
    }

    public void addMailbox(String domain, String remote) {
        mailboxes.put(domain, remote);
    }

    public void deleteMailbox(String domain) {
        mailboxes.remove(domain);
    }

    public String getMailbox(String domain) {
        return mailboxes.get(domain);
    }

    public Set<String> getKnownMailboxes() {
        return mailboxes.keySet();
    }

    private static class InstanceHolder {
        private static final NameserverStore INSTANCE = new NameserverStore();
    }
}
