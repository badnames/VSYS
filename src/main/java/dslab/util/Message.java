package dslab.util;

import java.util.Objects;

public class Message {
    private String to;
    private String from;
    private String subject;
    private String data;
    private String hash;

    public Message(String to, String from, String subject, String data, String hash) {
        this.to = to;
        this.from = from;
        this.subject = subject;
        this.data = data;
        this.hash = hash;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message = (Message) o;
        return Objects.equals(to, message.to) && Objects.equals(from, message.from) && Objects.equals(subject, message.subject) && Objects.equals(data, message.data) && Objects.equals(hash, message.hash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(to, from, subject, data, hash);
    }
}
