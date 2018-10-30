package groupmembershipservice.messages;

import java.util.HashSet;

public class PartitionConsensusMessage extends PartitionMessage {
    public PartitionConsensusMessage(String sender, HashSet<String> membersToBlock) {
        super(sender, membersToBlock);
    }
}
