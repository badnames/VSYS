package dslab.nameserver;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;

public class NameserverRemote implements INameserverRemote {

    @Override
    //Registers a mailbox server with the given address for the given domain.
    public void registerNameserver(String domain, INameserverRemote nameserver) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
        String[] domains = domain.split(".");
        if (domains.length == 1) {
            if (NameserverStore.getInstance().getSubZone(domain) == null) {
                throw new AlreadyRegisteredException(domain);
            }

            NameserverStore.getInstance().addSubZone(domain, nameserver);
            return;
        }

        //see if the last zone from the domain exist in one of the children
        String nextDomain = domains[domains.length - 1];
        INameserverRemote remote = NameserverStore.getInstance().getSubZone(nextDomain);
        if (remote == null)
            throw new InvalidDomainException(nextDomain + " does not exist!");

        // The next server does not need the last part of the domain.
        // "vienna.earth.planet" -> "vienna.earth"
        String subDomain = String.valueOf(
                Arrays.stream(domains)
                        .limit(domains.length - 1)
                        .reduce((d1, d2) -> d1 + "." + d2)
        );

        // Propagate the register request to the next nameserver in the chain
        remote.registerNameserver(subDomain, nameserver);
    }

    @Override
    //Returns a reference to the remote object of the ns for the given zone. For example, if called with the argument
    //     * 'earth' on the remote object of zone 'planet', the call returns the reference to the nameserver of the zone
    //     * 'earth.planet'.
    public void registerMailboxServer(String domain, String address) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
        String[] domains = domain.split(".");
        if (domains.length == 1) {
            if (NameserverStore.getInstance().getSubZone(domain) == null)
                throw new AlreadyRegisteredException("domain");

            NameserverStore.getInstance().addMailbox(domain, address);
            return;
        }

        //see if the last zone from the domain exist in one of the children
        String nextDomain = domains[domains.length - 1];
        INameserverRemote remote = NameserverStore.getInstance().getSubZone(nextDomain);
        if (remote == null)
            throw new InvalidDomainException(nextDomain + " does not exist!");

        // The next server does not need the last part of the domain.
        // "vienna.earth.planet" -> "vienna.earth"
        String subDomain = String.valueOf(
                Arrays.stream(domains)
                        .limit(domains.length - 1)
                        .reduce((d1, d2) -> d1 + "." + d2)
        );

        // Propagate the register request to the next nameserver in the chain
        remote.registerMailboxServer(subDomain, address);
    }

    @Override
    public INameserverRemote getNameserver(String zone) throws RemoteException {
        return NameserverStore.getInstance().getSubZone(zone);
    }

    @Override
    public String lookup(String domain) throws RemoteException {
        return NameserverStore.getInstance().getMailbox(domain);
    }
}