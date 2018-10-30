package groupmembershipservice.messages;

import java.util.HashSet;

public class PartitionMessage extends Message {

    private HashSet<String> subGroupToCreate;

    public PartitionMessage(String sender, HashSet<String> subGroupToCreate) {
        super(sender);
        this.subGroupToCreate = subGroupToCreate;
    }

    public HashSet<String> getSubGroupToCreate() {
        return subGroupToCreate;
    }
}
