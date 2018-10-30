package groupmembershipservice.messages;

import groupmembershipservice.View;

import java.io.Serializable;

public class ViewMessage extends Message implements Serializable {

    private long sequentialNumber;

    private View view;

    public ViewMessage(String sender, long sequentialNumber, View view) {
        super(sender);
        this.sequentialNumber = sequentialNumber;
        this.view = view;
    }

    public long getSequentialNumber() {
        return sequentialNumber;
    }

    public void setSequentialNumber(long sequentialNumber) {
        this.sequentialNumber = sequentialNumber;
    }

    public View getView() {
        return view;
    }

    public void setView(View view) {
        this.view = view;
    }

    @Override
    public String toString() {
        return "Message{" +
                "sender='" + sender + '\'' +
                ", sequentialNumber=" + sequentialNumber +
                ", view=" + view +
                '}';
    }
}
