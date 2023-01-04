package dslab.nameserver;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class NameserverStore {
    // Singleton
    private NameserverStore() {}

    private static class InstanceHolder {
        private static final NameserverStore INSTANCE = new NameserverStore();
    }

    public static synchronized NameserverStore getInstance() {
        return NameserverStore.InstanceHolder.INSTANCE;
    }

    private ConcurrentHashMap<String, String> mailboxes = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, INameserverRemote> subZones = new ConcurrentHashMap<>();

    public void addSubZone(String domain, INameserverRemote remote) {
        subZones.put(domain, remote);
    }

    public void deleteSubZone(String domain){
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

    public void deleteMailbox(String domain){
        mailboxes.remove(domain);
    }

    public String getMailbox(String domain) {
        return mailboxes.get(domain);
    }
}
