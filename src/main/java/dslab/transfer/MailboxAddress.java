package dslab.transfer;

public class MailboxAddress {
    private String domain;
    private int port;

    public MailboxAddress(String domain, int port) {
        this.domain = domain;
        this.port = port;
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
