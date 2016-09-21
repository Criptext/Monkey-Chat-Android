package com.criptext.monkeychatandroid.models;

import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;
import com.criptext.monkeykitui.conversation.MonkeyConversation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by daniel on 8/24/16.
 */

@Table(name = "ConversationItem")
public class ConversationItem extends Model implements MonkeyConversation{

    @Column(name = "idConv", index = true)
    private String idConv;
    @Column(name = "name")
    private String name;
    @Column(name = "datetime")
    private long datetime;
    @Column(name = "secondaryText")
    private String secondaryText;
    @Column(name = "totalNewMessage")
    private int totalNewMessage;
    @Column(name = "isGroup")
    private boolean isGroup;
    @Column(name = "groupMembers")
    public String groupMembers;
    @Column(name = "avatarFilePath")
    public String avatarFilePath;
    @Column(name = "status")
    public int status;

    public ConversationItem(){
        super();
    }

    public ConversationItem(String idConv, String name, long datetime, String secondaryText, int totalNewMessage,
                            boolean isGroup, String groupMembers, String avatarFilePath, int status) {
        super();
        this.idConv = idConv;
        this.name = name;
        this.datetime = datetime;
        this.secondaryText = secondaryText;
        this.totalNewMessage = totalNewMessage;
        this.isGroup = isGroup;
        this.groupMembers = groupMembers;
        this.avatarFilePath = avatarFilePath;
        this.status = status;
    }

    public ConversationItem(MonkeyConversation conversation){
        this.idConv = conversation.getConvId();
        this.name = conversation.getName();
        this.datetime = conversation.getDatetime();
        this.secondaryText = conversation.getSecondaryText();
        this.totalNewMessage = conversation.getTotalNewMessages();
        this.isGroup = conversation.isGroup();
        this.groupMembers = conversation.getGroupMembers();
        this.avatarFilePath = conversation.getAvatarFilePath();
        this.status = conversation.getStatus();
    }

    public void setId(String idConv) {
        this.idConv = idConv;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDatetime(long datetime) {
        this.datetime = datetime;
    }

    public void setSecondaryText(String secondaryText) {
        this.secondaryText = secondaryText;
    }

    public void setTotalNewMessage(int totalNewMessage) {
        this.totalNewMessage = totalNewMessage;
    }

    public void setGroup(boolean group) {
        isGroup = group;
    }

    public void setGroupMembers(String groupMembers) {
        this.groupMembers = groupMembers;
    }

    public void setAvatarFilePath(String avatarFilePath) {
        this.avatarFilePath = avatarFilePath;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    @NotNull
    @Override
    public String getConvId() {
        return idConv;
    }

    @NotNull
    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getDatetime() {
        return datetime;
    }

    @NotNull
    @Override
    public String getSecondaryText() {
        return secondaryText;
    }

    @Override
    public int getTotalNewMessages() {
        return totalNewMessage;
    }

    @Override
    public boolean isGroup() {
        return isGroup;
    }

    @NotNull
    @Override
    public String getGroupMembers() {
        return groupMembers;
    }

    @Nullable
    @Override
    public String getAvatarFilePath() {
        return avatarFilePath;
    }

    @Override
    public int getStatus() {
        return status;
    }
}