package groupmembershipservice.messages;

import groupmembershipservice.View;

public final class ConsensusMessage extends ViewMessage {
    public ConsensusMessage(String sender, long sequentialNumber, View view) {
        super(sender, sequentialNumber, view);
    }
}
