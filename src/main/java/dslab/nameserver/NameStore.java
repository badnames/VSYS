package dslab.nameserver;

public class NameStore {

    private String domain;
    private int ip;
    private int port;

    NameStore(String domain, int ip, int port){
        this.domain =domain;
        this.ip = ip;
        this.port =port;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public void setIp(int ip) {
        this.ip = ip;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public String getDomain() {
        return domain;
    }
}
