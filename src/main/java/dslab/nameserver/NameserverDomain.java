package dslab.nameserver;

public class NameserverDomain {

    private String domain;
    private int ip;
    private int port;

    NameserverDomain(String domain, int ip, int port) {
        this.domain = domain;
        this.ip = ip;
        this.port = port;
    }

    public int getIp() {
        return ip;
    }

    public void setIp(int ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }
}
