package groupmembershipservice;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import groupmembershipservice.messages.ViewMessage;

import java.util.concurrent.atomic.AtomicBoolean;

public class GroupsManager {

    private View view;
    private String group;
    private AtomicBoolean hasLeftGroup;

    GroupsManager() {
        this.view = new View();
        this.hasLeftGroup = new AtomicBoolean(true);
    }

    public void joinGroup(String group, String memberId) {
        this.group = group;
        this.hasLeftGroup = new AtomicBoolean(false);
        this.view.updateViewWithMemberAddition(memberId, 1);
    }

    public void leaveGroup(String memberId) {
        this.group = null;
        this.hasLeftGroup.getAndSet(true);
        this.view.updateViewWithMemberRemoval(memberId);
    }

    public boolean hasLeftGroup() {
        return this.hasLeftGroup.get();
    }

    public String getGroup() {
        return group;
    }

    public void reset() {
        this.hasLeftGroup.getAndSet(false);
        this.group = "";
    }

    public View getView() {
        return view;
    }

    public void updateView(ViewMessage message) {

        final ImmutableSet<String> difference =
                Sets.symmetricDifference(message.getView().getMembers(), this.view.getMembers()).immutableCopy();

        if (!difference.isEmpty()) {
            if (this.view.getMembers().size() < message.getView().getMembers().size()) {
                for (String member : difference) {
                    this.view.updateViewWithMemberAddition(member, message.getView().getVersion(), message.getSequentialNumber());
                }
            } else {
                for (String member : difference) {
                    this.view.updateViewWithMemberRemoval(member, message.getView().getVersion());
                }
            }
        }
    }

    public void updateViewWithJoinRequest(ViewMessage message) {
        this.view.updateViewWithMemberAddition(message.getSender(), message.getSequentialNumber());
    }

    boolean mergeViews(ViewMessage message) {
        if (message.getView().getMembers().size() == this.view.getMembers().size()) {
            boolean wasConcurrentRemoval = false;
            String distinctMember = null;

            for (String member : message.getView().getMembers()) {
                if (!this.view.getMembers().contains(member)) {
                    if (member.equals(this.view.getLastRemoval())) {
                        wasConcurrentRemoval = true;
                    } else {
                        // found distinct member
                        distinctMember = member;
                    }
                }
            }

            if (distinctMember == null) {
                // views are the same
                return false;
            }

            if (wasConcurrentRemoval) {
                this.view.updateViewWithMemberRemoval(distinctMember);
            } else {
                this.view.updateViewWithMemberAddition(distinctMember, message.getSequentialNumber());
            }

            return true;
        }

        return false;
    }
}
