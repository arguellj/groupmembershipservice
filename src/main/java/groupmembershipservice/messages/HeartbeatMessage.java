package groupmembershipservice.messages;

import java.io.Serializable;

public final class HeartbeatMessage extends Message implements Serializable {
    public HeartbeatMessage(String sender) {
        super(sender);
    }
}
