package groupmembershipservice;

import groupmembershipservice.messages.*;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static groupmembershipservice.Constants.HEARTBEAT_TIME;
import static groupmembershipservice.Constants.MAX_TRIES;
import static groupmembershipservice.Constants.TIME_OUT_VIEW_MESSAGE;

@Log4j2
public class MulticastPeer extends TimerTask {

    private static final int BUFFER_SIZE = 65536; //64k

    private MulticastSocket socket;
    private byte[] receiveBuffer;
    private byte[] sendViewBuffer;
    private byte[] sendHeartbeatBuffer;
    private byte[] sendConsensusBuffer;
    private byte[] sendPartitionBuffer;
    private GroupsManager groupsManager;
    private String memberId;
    private int port;
    private AtomicLong currentSequential;
    private Timer timer;
    private List<GroupListener> listeners;
    private Thread thread;
    private Timer timerViewMessage;
    private int tries;

    /*
        Failures helper mechanisms.
    */
    private AtomicInteger pendingDuplicatedViewMessages = new AtomicInteger(0);
    private AtomicInteger pendingLostViewMessages = new AtomicInteger(0);
    private AtomicInteger pendingDuplicatedConsensusMessages = new AtomicInteger(0);
    private AtomicInteger pendingLostConsensusMessages = new AtomicInteger(0);
    private HashSet<String> subGroup;

    public MulticastPeer(int port, String memberId) throws IOException {
        this.groupsManager = new GroupsManager();
        this.receiveBuffer = new byte[BUFFER_SIZE];
        this.sendViewBuffer = new byte[BUFFER_SIZE];
        this.sendHeartbeatBuffer = new byte[BUFFER_SIZE];
        this.sendConsensusBuffer = new byte[BUFFER_SIZE];
        this.sendPartitionBuffer = new byte[BUFFER_SIZE];
        this.socket = new MulticastSocket(port);
        this.socket.setReuseAddress(true);
        //this.socket.setLoopbackMode(true);
        this.port = port;
        this.memberId = memberId;
        this.currentSequential = new AtomicLong(0);
        this.listeners = new ArrayList<>();
        this.timer = new Timer();
        this.timerViewMessage = new Timer();
        this.subGroup = new HashSet<>();
    }

    public String getMemberId() {
        return memberId;
    }

    public AtomicInteger getPendingDuplicatedViewMessages() {
        return pendingDuplicatedViewMessages;
    }

    public AtomicInteger getPendingLostViewMessages() {
        return pendingLostViewMessages;
    }

    public AtomicInteger getPendingDuplicatedConsensusMessages() {
        return pendingDuplicatedConsensusMessages;
    }

    public AtomicInteger getPendingLostConsensusMessages() {
        return pendingLostConsensusMessages;
    }

    /**
     * Adds a listener for any change over the member (i.e view, failure actions).
     * @param toAdd listener to add
     */
    public void addListener(GroupListener toAdd){
        this.listeners.add(toAdd);
    }

    /**
     * Joins a group. It starts sending heartbeats and listening to the group. It also send the joining message.
     * @param group group to join
     * @throws IOException
     */
    public void joinGroup(String group) throws IOException {
        log.info(group + ": " + memberId + " joining group.");
        final InetAddress groupAddress = InetAddress.getByName(group);
        this.socket.joinGroup(groupAddress);
        this.groupsManager.joinGroup(group, this.memberId);
        this.sendUnreliableView();
        this.thread = new Thread(this::listen);
        this.thread.start();
        this.timer.schedule(this, 0, HEARTBEAT_TIME);
    }

    /**
     * Leaves the joined group. It finishes the heartbeat, leaves the group and closes the socket.
     * @throws IOException
     */
    public void leaveGroup() throws IOException {
        if (!this.groupsManager.hasLeftGroup()) {
            this.logInfo(memberId +" leaving group. ");
            this.timer.cancel();
            this.timerViewMessage.cancel();
            final InetAddress groupAddress = InetAddress.getByName(this.groupsManager.getGroup());
            this.groupsManager.leaveGroup(this.memberId);
            this.sendUnreliableView();
            this.socket.leaveGroup(groupAddress);
            this.socket.close();
        }
    }

    /**
     * Returns the current view of the group.
     * @return
     */
    public View getView() {
        if (!this.groupsManager.hasLeftGroup()) {
            return this.groupsManager.getView();
        }

        return null;
    }

    /**
     * Listen to the group multicast socket
     */
    private void listen() {
        try {
            while (!this.groupsManager.hasLeftGroup()) {
                final DatagramPacket packet = new DatagramPacket(this.receiveBuffer, this.receiveBuffer.length);
                this.socket.receive(packet);

                final Message receivedMessage  = (Message) Utils.deserializeObject(packet.getData());

                if (receivedMessage instanceof PartitionMessage) {
                    this.subGroup = ((PartitionMessage) receivedMessage).getSubGroupToCreate();
                    continue;
                }

                if (!this.memberId.equals(receivedMessage.getSender()) && receivedMessage instanceof ViewMessage
                        && this.isJoinMessage((ViewMessage) receivedMessage)) {
                    this.sendPartition(this.subGroup);
                }

                // Network partition simulation
                if (!this.shouldProcessMessage(receivedMessage.getSender())) {
                    continue;
                }

                if (receivedMessage instanceof HeartbeatMessage) {
                    this.groupsManager.getView().updateMemberStatus(receivedMessage.getSender());
                    continue;
                }

                if (this.memberId.equals(receivedMessage.getSender())) {
                    continue;
                }

                final ViewMessage viewReceived = (ViewMessage) receivedMessage;
                this.logInfo("Delivered...");

                if (!this.groupsManager.hasLeftGroup()) {
                    log.info(viewReceived.toString());

                    if (viewReceived instanceof ConsensusMessage &&
                            this.groupsManager.getView().equals(viewReceived.getView())) {
                        this.stopViewTimer();
                        continue;
                    }

                    if (this.isJoinMessage(viewReceived)) {
                        if (this.groupsManager.getView().canJoined(viewReceived)) {
                            this.groupsManager.updateViewWithJoinRequest(viewReceived);
                            this.sendUnreliableView();
                        }
                        continue;
                    }

                    if (!this.groupsManager.getView().isMessageValid(viewReceived)) {
                        continue;
                    }

                    if (this.groupsManager.getView().getVersion() == viewReceived.getView().getVersion()) {
                        if(this.groupsManager.mergeViews(viewReceived)) {
                            this.logInfo("Merged views");
                            this.sendUnreliableView();
                        } else {
                            // If views are the same, send the consensus message that this node agree that this is the last version
                            this.sendUnreliableConsensus();
                        }
                    } else if (this.groupsManager.getView().getVersion() < viewReceived.getView().getVersion()) {
                        this.groupsManager.updateView(viewReceived);
                        this.logInfo("Updated view.");
                        this.sendUnreliableView();
                    } else {
                        // The sender has a old view. Hence, we should send a updated view.
                        this.logInfo("Received old view. Sending up to date view...");
                        this.sendUnreliableView();
                    }
                }

                this.logInfo("Listening for View...");
            }

            this.groupsManager.reset();
        } catch (IOException e) {
            if (this.socket != null && !this.socket.isClosed()) {
                this.socket.close();
            }
            log.error(e);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private boolean shouldProcessMessage(String sender) {
        if (this.subGroup.contains(this.memberId)) {
            return this.subGroup.contains(sender);
        }

        return !this.subGroup.contains(sender);
    }

    /**
     * Stops the time scheduler which sends the view periodically.
     */
    private void stopViewTimer() {
        try {
            this.timerViewMessage.cancel();
        } catch (IllegalStateException e) {
            // Ignore if timer was cancelled.
            log.error(e);
        }
    }

    /**
     * Scheduled task to detect failure nodes and send heartbeats.
     */
    @Override
    public void run() {
        try {
            if (this.thread.isAlive()) {
                CompletableFuture.supplyAsync(() -> this.groupsManager.getView().checkMemberStatus())
                        .thenAccept(b -> {
                            if (b) {
                                try {
                                    sendUnreliableView();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                this.sendHeartbeat();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Check if the message is for joining purpose.
     * @param message message received
     * @return whether or not the message is to join
     */
    private boolean isJoinMessage(ViewMessage message) {
        return message.getView().getVersion() == 1;
    }

    /**
     * Sends a message to other members
     * @param buffer buffer to the data to transmit
     * @throws IOException
     */
    private void sendMessage(byte[] buffer) throws IOException {
        final InetAddress groupAddress = InetAddress.getByName(this.groupsManager.getGroup());
        DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length, groupAddress, this.port);
        this.socket.send(datagramPacket);
    }

    /**
     * Sends heartbeat to other members.
     * @throws IOException
     */
    private void sendHeartbeat() throws IOException {
        final Message message = new HeartbeatMessage(this.memberId);
        this.sendHeartbeatBuffer = Utils.serializeObject(message);
        this.sendMessage(this.sendHeartbeatBuffer);
    }

    /**
     * Sends view through unreliable method which can failed if the appropriate failure scenario is injected
     * @throws IOException
     */
    private void sendUnreliableView() throws IOException {
        this.stopViewTimer();
        this.timerViewMessage = new Timer();
        this.tries = 0;
        this.timerViewMessage.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    // Do not resend for joining messages (version 1) because if there is not response,
                    // this is the only node in the group.
                    if ((groupsManager.getView().getVersion() == 1 && tries++ < MAX_TRIES) ||
                            (groupsManager.getView().getMembers().size() > 1 && groupsManager.getView().getVersion() > 1)) {
                        sendUnreliableView(true);
                    } else {
                        timerViewMessage.cancel();
                    }
                } catch (IOException e) {
                    timerViewMessage.cancel();
                }
            }
        }, TIME_OUT_VIEW_MESSAGE, TIME_OUT_VIEW_MESSAGE);

        this.sendUnreliableView(false);
    }

    /**
     * Sends view through unreliable method which can failed if the appropriate failure scenario is injected
     * @throws IOException
     */
    private void sendUnreliableView(boolean resendMessage) throws IOException {
        if (!resendMessage) {
            this.updateViewEvent();
        }

        if (this.pendingLostViewMessages.get() > 0) {
            this.pendingLostViewMessages.decrementAndGet();
            this.logInfo("Simulating lost view message...");
            this.updateViewLostMessageEvent();
            return;
        }

        this.sendView();

        if (this.pendingDuplicatedViewMessages.get() > 0) {
            this.pendingDuplicatedViewMessages.decrementAndGet();
            this.logInfo("Sending duplicated view message...");
            this.updateViewDuplicationEvent();
            this.sendView();
        }
    }

    /**
     * Sends view to other members.
     * @throws IOException
     */
    private void sendView() throws IOException {
        this.logInfo("Sending view... " + this.groupsManager.getView());
        final Message message = new ViewMessage(
                this.memberId,
                this.currentSequential.incrementAndGet(),
                this.groupsManager.getView());
        this.sendViewBuffer = Utils.serializeObject(message);
        this.sendMessage(this.sendViewBuffer);
    }

    /**
     * Sends the consensus through an unreliable method which can failed if the appropriate failure scenario is injected
     * @throws IOException
     */
    private void sendUnreliableConsensus() throws IOException {
        if (this.pendingLostConsensusMessages.get() > 0) {
            this.pendingLostConsensusMessages.decrementAndGet();
            this.logInfo("Simulating lost consensus message...");
            this.updateConsensusLostMessageEvent();
            return;
        }

        this.sendConsensus();

        if (this.pendingDuplicatedConsensusMessages.get() > 0) {
            this.pendingDuplicatedConsensusMessages.decrementAndGet();
            this.logInfo("Sending duplicated consensus message...");
            this.updateConsensusDuplicationEvent();
            this.sendConsensus();
        }
    }

    /**
     * Sends the consensus message to agree in current version of view.
     * @throws IOException
     */
    private void sendConsensus() throws IOException {
        this.logInfo("Sending consensus... " + this.groupsManager.getView());
        final Message message = new ConsensusMessage(
                this.memberId,
                this.currentSequential.incrementAndGet(),
                this.groupsManager.getView());
        this.sendConsensusBuffer = Utils.serializeObject(message);
        this.sendMessage(this.sendConsensusBuffer);
    }

    private void sendPartition(HashSet<String> membersToBlock) throws IOException {
        this.logInfo("Sending partition request... " + this.groupsManager.getView());
        final Message message = new PartitionMessage(
                this.memberId,
                membersToBlock);
        this.sendPartitionBuffer = Utils.serializeObject(message);
        this.sendMessage(this.sendPartitionBuffer);
    }

    /*
    Update Notification Events
     */
    /**
     * Throws the update view event to listeners.
     */
    private void updateViewEvent() {
        for (GroupListener listener : listeners) {
            try {
                listener.updatedView(this.groupsManager.getView().clone());
            } catch (CloneNotSupportedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Throws an event notification about duplications messages have occurred.
     */
    private void updateViewDuplicationEvent() {
        for (GroupListener listener : listeners) {
            listener.updateDuplicateViewMessages();
        }
    }

    /**
     * Throws an event notification about lost messages have occurred.
     */
    private void updateViewLostMessageEvent() {
        for (GroupListener listener : listeners) {
            listener.updateLostViewMessages();
        }
    }

    /**
     * Throws an event notification about duplications messages have occurred.
     */
    private void updateConsensusDuplicationEvent() {
        for (GroupListener listener : listeners) {
            listener.updateDuplicateConsensusMessages();
        }
    }

    /**
     * Throws an event notification about lost messages have occurred.
     */
    private void updateConsensusLostMessageEvent() {
        for (GroupListener listener : listeners) {
            listener.updateLostConsensusMessages();
        }
    }

    /**
     * Logs information with the group name.
     * @param message
     */
    private void logInfo(String message) {
        log.info(this.groupsManager.getGroup() + ": " + this.memberId + " - " + message);
    }

    /*
        Failure Scenarios Injection.
     */
    /**
     * Kill the process.
     */
    public void kill() {
        this.logInfo("Kill server");
        this.socket.close();
        this.timer.cancel();
    }

    /**
     * Increment the amount of messages to duplicate.
     */
    public void duplicateViewMessage() {
        this.logInfo("Set duplication for the next message.");
        this.pendingDuplicatedViewMessages.getAndIncrement();
        this.updateViewDuplicationEvent();
    }

    /**
     * Increment the amount of messages to lose.
     */
    public void loseViewMessage() {
        this.logInfo("Set losing for the next message.");
        this.pendingLostViewMessages.getAndIncrement();
        this.updateViewLostMessageEvent();
    }

    /**
     * Increment the amount of messages to duplicate.
     */
    public void duplicateConsensusMessage() {
        this.logInfo("Set duplication for the next message.");
        this.pendingDuplicatedConsensusMessages.getAndIncrement();
        this.updateConsensusDuplicationEvent();
    }

    /**
     * Increment the amount of messages to lose.
     */
    public void loseConsensusMessage() {
        this.logInfo("Set losing for the next message.");
        this.pendingLostConsensusMessages.getAndIncrement();
        this.updateConsensusLostMessageEvent();
    }

    public void blockMembers(String[] membersToBlock) throws IOException {
        final HashSet<String> set = new HashSet<>(Arrays.asList(membersToBlock));
        this.sendPartition(set);
    }
}
