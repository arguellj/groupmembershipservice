package groupmembershipservice;

public interface GroupListener {
    void updatedView(View view);

    void updateDuplicateViewMessages();

    void updateLostViewMessages();

    void updateDuplicateConsensusMessages();

    void updateLostConsensusMessages();
}
