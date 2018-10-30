package groupmembershipservice;

import groupmembershipservice.messages.ViewMessage;

import java.io.Serializable;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static groupmembershipservice.Constants.HEARTBEAT_TIME;

public final class View implements Serializable, Cloneable {
    private long version;

    private ConcurrentHashMap<String, Member> members;

    private String lastRemoval;

    View() {
        this.version = 0;
        this.members = new ConcurrentHashMap<>();
    }

    public synchronized long getVersion() {
        return version;
    }

    public synchronized Set<String> getMembers() {
        return members.keySet();
    }

    public synchronized String getLastRemoval() {
        return lastRemoval;
    }

    synchronized void updateViewWithMemberRemoval(String member) {
        updateViewWithMemberRemoval(member, version + 1);
    }

    synchronized void updateViewWithMemberRemoval(String member, long version) {
        this.version = version;
        this.members.remove(member);
        this.lastRemoval = member;
    }

    synchronized void updateViewWithMemberAddition(String member, long sequentialNumber) {
        updateViewWithMemberAddition(member, version + 1, sequentialNumber);
    }

    synchronized void updateViewWithMemberAddition(String member, long version, long sequentialNumber) {
        this.version = version;
        this.members.putIfAbsent(member, new Member(new Timestamp(System.currentTimeMillis()), sequentialNumber));
    }

    void updateMemberStatus(String sender) {
        if (this.members.get(sender) != null) {
            this.members.get(sender).timestamp.setTime(System.currentTimeMillis());
        }
    }

    public synchronized boolean checkMemberStatus() {
        boolean updatedView = false;
        final Instant limitTime = Instant.now().minus((long) (HEARTBEAT_TIME * 2.5), ChronoUnit.MILLIS);

        for (Map.Entry<String, Member> member : this.members.entrySet()) {
            if (member.getValue().timestamp.toInstant().isBefore(limitTime)) {
                this.updateViewWithMemberRemoval(member.getKey());
                updatedView = true;
            }
        }

        return updatedView;
    }

    public boolean isMessageValid(ViewMessage viewReceived) {
        // Avoid duplicates and non-ordered messages by ignored them
        final boolean valid = this.members.get(viewReceived.getSender()) == null ||
                this.members.get(viewReceived.getSender()).lastSequentialNumber < viewReceived.getSequentialNumber();

        return valid;
    }

    public boolean canJoined(ViewMessage viewReceived) {
        return this.members.get(viewReceived.getSender()) == null;
    }

    @Override
    public String toString() {
        return "View{" +
                "version=" + version +
                ", members=" + members.keySet() +
                '}';
    }

    @Override
    public View clone() throws CloneNotSupportedException {
        View clone = (View) super.clone();
        clone.members = new ConcurrentHashMap<>(this.members);

        return clone;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        final View other = (View) obj;

        if ((this.members == null) ? (other.members != null) : !this.members.keySet().equals(other.members.keySet())) {
            return false;
        }

        if (this.version != other.version) {
            return false;
        }

        return true;
    }

    class Member implements Serializable, Cloneable {
        Timestamp timestamp;
        long lastSequentialNumber;

        Member(Timestamp timestamp, long lastSequentialNumber) {
            this.timestamp = timestamp;
            this.lastSequentialNumber = lastSequentialNumber;
        }
    }
}
