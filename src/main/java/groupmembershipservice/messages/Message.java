package groupmembershipservice.messages;

import java.io.Serializable;

public class Message implements Serializable {
    String sender;

    Message(String sender) {
        this.sender = sender;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }
}
