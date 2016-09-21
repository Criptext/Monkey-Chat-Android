package com.criptext.monkeychatandroid;

import android.graphics.Color;

import com.criptext.MonkeyKitSocketService;
import com.criptext.comunication.MOKUser;

import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by daniel on 8/29/16.
 */

public class GroupData implements com.criptext.monkeykitui.recycler.GroupChat{

    private String conversationId;
    private String membersIds;
    private HashMap<String, MOKUser> mokUserHashMap;
    private HashMap<String, Integer> userIndexHashMap;
    private boolean askingUsers = false;
    private List<Integer> colorsForUsersInGroup;
    private int MAX_PARTICIPANTS = 50;
    private WeakReference<MonkeyKitSocketService> serviceRef;

    public GroupData(String conversationId, String members, MonkeyKitSocketService service){
        this.conversationId = conversationId;
        this.membersIds = members;
        this.serviceRef = new WeakReference<>(service);
        mokUserHashMap = new HashMap<>();
        userIndexHashMap = new HashMap<>();
        initColorsForGroup();
        //TODO GET MEMBERS FROM DB
        for(String memberId: membersIds.split(",")){
            if(mokUserHashMap.get(memberId)==null){
                getMembers();
                askingUsers = true;
                break;
            }
        }
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setMembers(String conversationId, ArrayList<MOKUser> mokUsers){
        if(conversationId.equals(this.conversationId)) {
            askingUsers = false;
            mokUserHashMap.clear();
            userIndexHashMap.clear();
            for (MOKUser mokUser : mokUsers) {
                mokUserHashMap.put(mokUser.getMonkeyId(), mokUser);
                userIndexHashMap.put(mokUser.getMonkeyId(), userIndexHashMap.size());
            }
        }
    }

    private void getMembers(){
        final MonkeyKitSocketService service = serviceRef.get();
        if(!askingUsers && service!=null){
            service.getUsersInfo(membersIds);
        }
    }

    @NotNull
    @Override
    public String getMemberName(@NotNull String monkeyId) {
        MOKUser mokUser = mokUserHashMap.get(monkeyId);
        String finalName = "Unknown";
        if(mokUser!=null && mokUser.getInfo()!=null && mokUser.getInfo().has("name")){
            finalName = mokUser.getInfo().get("name").getAsString();
        }
        return finalName;
    }

    @Override
    public int getMemberColor(@NotNull String monkeyId) {

        int indiceColor = 0;
        if(userIndexHashMap.get(monkeyId)!=null) {
            indiceColor = userIndexHashMap.get(monkeyId);
            if (indiceColor > MAX_PARTICIPANTS)
                indiceColor = indiceColor - MAX_PARTICIPANTS;
        }
        return colorsForUsersInGroup.get(indiceColor);
    }

    private void initColorsForGroup(){
        colorsForUsersInGroup=new ArrayList<>();
        colorsForUsersInGroup.add(Color.rgb(111,6,123));
        colorsForUsersInGroup.add(Color.rgb(0,164,158));
        colorsForUsersInGroup.add(Color.rgb(179,0,124));
        colorsForUsersInGroup.add(Color.rgb(180,216,0));
        colorsForUsersInGroup.add(Color.rgb(226,0,104));
        colorsForUsersInGroup.add(Color.rgb(0,178,235));
        colorsForUsersInGroup.add(Color.rgb(236,135,14));
        colorsForUsersInGroup.add(Color.rgb(132,176,185));
        colorsForUsersInGroup.add(Color.rgb(58,106,116));
        colorsForUsersInGroup.add(Color.rgb(189, 167, 0));
        colorsForUsersInGroup.add(Color.rgb(130, 106, 169));
        colorsForUsersInGroup.add(Color.rgb(175,64,42));
        colorsForUsersInGroup.add(Color.rgb(115, 54, 16));
        colorsForUsersInGroup.add(Color.rgb(2,13,216));
        colorsForUsersInGroup.add(Color.rgb(126,101,101));
        colorsForUsersInGroup.add(Color.rgb(205,121,103));
        colorsForUsersInGroup.add(Color.rgb(253,120,167));
        colorsForUsersInGroup.add(Color.rgb(0,159,98));
        colorsForUsersInGroup.add(Color.rgb(51, 102, 51));
        colorsForUsersInGroup.add(Color.rgb(233,156,122));
        colorsForUsersInGroup.add(Color.rgb(111,6,123));
        colorsForUsersInGroup.add(Color.rgb(0,164,158));
        colorsForUsersInGroup.add(Color.rgb(179,0,124));
        colorsForUsersInGroup.add(Color.rgb(180,216,0));
        colorsForUsersInGroup.add(Color.rgb(226,0,104));
        colorsForUsersInGroup.add(Color.rgb(0,178,235));
        colorsForUsersInGroup.add(Color.rgb(236,135,14));
        colorsForUsersInGroup.add(Color.rgb(132,176,185));
        colorsForUsersInGroup.add(Color.rgb(58,106,116));
        colorsForUsersInGroup.add(Color.rgb(189, 167, 0));
        colorsForUsersInGroup.add(Color.rgb(130, 106, 169));
        colorsForUsersInGroup.add(Color.rgb(175,64,42));
        colorsForUsersInGroup.add(Color.rgb(115, 54, 16));
        colorsForUsersInGroup.add(Color.rgb(2,13,216));
        colorsForUsersInGroup.add(Color.rgb(126,101,101));
        colorsForUsersInGroup.add(Color.rgb(205,121,103));
        colorsForUsersInGroup.add(Color.rgb(253,120,167));
        colorsForUsersInGroup.add(Color.rgb(0,159,98));
        colorsForUsersInGroup.add(Color.rgb(51, 102, 51));
        colorsForUsersInGroup.add(Color.rgb(233,156,122));
        colorsForUsersInGroup.add(Color.rgb(111,6,123));
        colorsForUsersInGroup.add(Color.rgb(0,164,158));
        colorsForUsersInGroup.add(Color.rgb(179,0,124));
        colorsForUsersInGroup.add(Color.rgb(180,216,0));
        colorsForUsersInGroup.add(Color.rgb(226,0,104));
        colorsForUsersInGroup.add(Color.rgb(0,178,235));
        colorsForUsersInGroup.add(Color.rgb(236,135,14));
        colorsForUsersInGroup.add(Color.rgb(132,176,185));
        colorsForUsersInGroup.add(Color.rgb(58,106,116));
        colorsForUsersInGroup.add(Color.rgb(189, 167, 0));
        colorsForUsersInGroup.add(Color.rgb(0,0,0));
    }
}