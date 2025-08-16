package com.chatapp.model;

public class Message {
    private final String sender;
    private final String receiver;
    private final boolean isRoom;
    private final String text;

    public Message(String sender, String receiver, boolean isRoom, String text) {
        this.sender = sender;
        this.receiver = receiver;
        this.isRoom = isRoom;
        this.text = text;
    }

    public String getSender() { return sender; }
    public String getReceiver() { return receiver; }
    public boolean isRoom() { return isRoom; }
    public String getText() { return text; }

    @Override
    public String toString() {
        return "Message{" + "sender='" + sender + '\'' + ", receiver='" + receiver + '\'' + ", isRoom=" + isRoom + ", text='" + text + '\'' + '}';
    }
}
