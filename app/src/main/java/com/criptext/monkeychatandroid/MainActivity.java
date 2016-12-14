package com.criptext.monkeychatandroid;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.activeandroid.Model;
import com.criptext.ClientData;
import com.criptext.comunication.MOKConversation;
import com.criptext.comunication.MOKDelete;
import com.criptext.comunication.MOKMessage;
import com.criptext.comunication.MOKNotification;
import com.criptext.comunication.MOKUser;
import com.criptext.comunication.MessageTypes;
import com.criptext.comunication.PushMessage;
import com.criptext.gcm.MonkeyRegistrationService;
import com.criptext.http.HttpSync;
import com.criptext.lib.MKDelegateActivity;
import com.criptext.monkeychatandroid.dialogs.SyncStatus;
import com.criptext.monkeychatandroid.gcm.SampleRegistrationService;
import com.criptext.monkeychatandroid.models.AsyncDBHandler;
import com.criptext.monkeychatandroid.models.ConversationItem;
import com.criptext.monkeychatandroid.models.DatabaseHandler;
import com.criptext.monkeychatandroid.models.FindConversationTask;
import com.criptext.monkeychatandroid.models.FindMessageTask;
import com.criptext.monkeychatandroid.models.GetConversationPageTask;
import com.criptext.monkeychatandroid.models.GetMessagePageTask;
import com.criptext.monkeychatandroid.models.MessageItem;
import com.criptext.monkeychatandroid.models.SaveModelTask;
import com.criptext.monkeychatandroid.models.StoreNewConversationTask;
import com.criptext.monkeychatandroid.models.UpdateConversationsTask;
import com.criptext.monkeykitui.MonkeyChatFragment;
import com.criptext.monkeykitui.MonkeyConversationsFragment;
import com.criptext.monkeykitui.MonkeyInfoFragment;
import com.criptext.monkeykitui.cav.EmojiHandler;
import com.criptext.monkeykitui.conversation.ConversationsActivity;
import com.criptext.monkeykitui.conversation.ConversationsList;
import com.criptext.monkeykitui.conversation.MonkeyConversation;
import com.criptext.monkeykitui.conversation.holder.ConversationTransaction;
import com.criptext.monkeykitui.info.InfoActivity;
import com.criptext.monkeykitui.input.listeners.InputListener;
import com.criptext.monkeykitui.recycler.ChatActivity;
import com.criptext.monkeykitui.recycler.GroupChat;
import com.criptext.monkeykitui.recycler.MonkeyInfo;
import com.criptext.monkeykitui.recycler.MonkeyItem;
import com.criptext.monkeykitui.recycler.MonkeyItemTransaction;
import com.criptext.monkeykitui.recycler.audio.PlaybackNotification;
import com.criptext.monkeykitui.recycler.audio.PlaybackService;
import com.criptext.monkeykitui.toolbar.ToolbarDelegate;
import com.criptext.monkeykitui.util.MonkeyFragmentManager;
import com.criptext.monkeykitui.util.Utils;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Handler;

public class MainActivity extends MKDelegateActivity implements ChatActivity, ConversationsActivity, InfoActivity, ToolbarDelegate{

    private static String DATA_FRAGMENT = "MainActivity.chatDataFragment";
    //Since this is the Chat activity, we need a RecyclerView and an adapter. Additionally we
    //will store the messages in our own list so that they can be accessed easily.
    MonkeyFragmentManager monkeyFragmentManager;
    MonkeyChatFragment monkeyChatFragment;
    ConversationItem activeConversationItem = null;
    MonkeyInfoFragment monkeyInfoFragment;

    HashMap<String, List<MonkeyItem>> messagesMap = new HashMap<>();
    ConversationsList conversations = null;
    ChatDataFragment dataFragment;

    static int MESS_PERPAGE = 30;

    Handler handler;
    /**
     * This class is basically a media player for our voice notes. we pass this to MonkeyAdapter
     * so that it can handle all the media playback for us. However, we must initialize it in "onStart".
     * and release it in "onStop" method.
     */
    PlaybackService.VoiceNotePlayerBinder voiceNotePlayer;

    private SharedPreferences prefs;
    /**
     * Monkey ID of the current user. This is stored in Shared Preferences, so we use this
     * property to cache it so that we don't have to read from disk every time we need it.
     */
    private String myMonkeyID;
    /**
     * Name of the current user. This is stored in Shared Preferences, so we use this
     * property to cache it so that we don't have to read from disk every time we need it.
     */
    private String myName;
    /**
     * Monkey ID of the user that we are going to talk with.
     */
    private String myFriendID;
    /**
     * This class is used to handle group methods.
     */
    private GroupData groupData;

    private AsyncDBHandler asyncDBHandler;

    private File downloadDir;

    private SyncStatus syncStatus;

    private ServiceConnection playbackConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            voiceNotePlayer = (PlaybackService.VoiceNotePlayerBinder) service;
            if(monkeyChatFragment != null)
                monkeyChatFragment.setVoiceNotePlayer(voiceNotePlayer);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getRetainedData();
        //First, initialize the constants from SharedPreferences.
        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        myMonkeyID = prefs.getString(MonkeyChat.MONKEY_ID, null);
        myName = prefs.getString(MonkeyChat.FULLNAME, null);
        //Log.d("MonkeyId", myMonkeyID);
        downloadDir = MonkeyChat.getDownloadDir(this);

        //Check play services. if available try to register with GCM so that we get Push notifications
        if(MonkeyRegistrationService.Companion.checkPlayServices(this))
                registerWithGCM();

        //Setup MonkeyKit UI fragments
        monkeyFragmentManager = new MonkeyFragmentManager(this);
        monkeyFragmentManager.setConversationsTitle(getResources().getString(R.string.app_name));
        //this function sets the content layout for the fragments and puts a conversations fragment
        monkeyFragmentManager.setContentLayout(savedInstanceState);

        asyncDBHandler = new AsyncDBHandler();

        //wait for a timeout to show a "connecting" message
        syncStatus = new SyncStatus(monkeyFragmentManager);
        syncStatus.delayConnectingMessage();

    }

    public void registerWithGCM(){
        Intent intent = new Intent(this, SampleRegistrationService.class);
        intent.putExtra(ClientData.Companion.getAPP_ID_KEY(), SensitiveData.APP_ID);
        intent.putExtra(ClientData.Companion.getAPP_KEY_KEY(), SensitiveData.APP_KEY);
        intent.putExtra(ClientData.Companion.getMONKEY_ID_KEY(), myMonkeyID);
        startService(intent);
        Log.d("MainActivity", "Registering with GCM");
    }

    @Override
    protected void onPause() {
        super.onPause();
        //sensorHandler.onPause();
    }

    @Override
    protected void onStart(){
        super.onStart();
        //bind to the service that plays voice notes.
        startPlaybackService();
    }

    @Override
    protected void onStop() {
        super.onStop();
        getApplicationContext().unbindService(playbackConnection);
        //sensorHandler.onStop();
    }

    private void startPlaybackService() {
        Intent intent = new Intent(getApplicationContext(), PlaybackService.class);
        if(!PlaybackService.Companion.isRunning())
            getApplicationContext().startService(intent);
        getApplicationContext().bindService(intent, playbackConnection, Context.BIND_AUTO_CREATE);
    }

    private void getRetainedData(){
        final ChatDataFragment retainedFragment =(ChatDataFragment) getSupportFragmentManager().findFragmentByTag(DATA_FRAGMENT);
        if(retainedFragment != null) {
            messagesMap = retainedFragment.chatMap!=null?retainedFragment.chatMap:new HashMap<String, List<MonkeyItem>>();
            conversations = retainedFragment.conversations;
            groupData = retainedFragment.groupData;
            activeConversationItem = retainedFragment.activeConversationItem;
            if (monkeyChatFragment != null) {
                monkeyChatFragment.setInputListener(initInputListener());
                monkeyChatFragment.setVoiceNotePlayer(voiceNotePlayer);
            }
            dataFragment = retainedFragment;
        } else {
            dataFragment = new ChatDataFragment();
            messagesMap = new HashMap<>();
            conversations = new ConversationsList();
            getSupportFragmentManager().beginTransaction().add(dataFragment, DATA_FRAGMENT).commit();
        }

    }

    private void retainDataInFragment(){
        dataFragment.chatMap = this.messagesMap;
        dataFragment.conversations = this.conversations;
        dataFragment.groupData = this.groupData;
        dataFragment.activeConversationItem = activeConversationItem;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //sensorHandler.onDestroy();
        retainDataInFragment();
        if(monkeyChatFragment != null)
            monkeyChatFragment.setInputListener(null);
        asyncDBHandler.cancelAll();
    }

    @Override
    protected void onRestart(){
        super.onRestart();
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                break;
        }
        return false;
    }

    /***
     * MY OWN METHODS
     */

    /**
     * Sets an InputListener to the InputView. This object listens for new messages that the user
     * wants to send, regardless of the type. They can be text, audio or photo messages. The listener
     * checks the type to figure out how to send it with MonkeyKit.
     */
    public InputListener initInputListener(){
        return new InputListener() {
            @Override
            public void onStopTyping() {
                JSONObject params = new JSONObject();
                try {
                    params.put("type", 20);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                if(activeConversationItem != null){
                    sendTemporalNotification(activeConversationItem.getConvId(), params);
                }
            }

            @Override
            public void onTyping(@NotNull String text) {
                JSONObject params = new JSONObject();
                try {
                    params.put("type", 21);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                if(activeConversationItem != null){
                    sendTemporalNotification(activeConversationItem.getConvId(), params);
                }
            }

            @Override
            public void onNewItemFileError(int type) {
                Toast.makeText(MainActivity.this, "Error writing file of type " +
                        MonkeyItem.MonkeyItemType.values()[type], Toast.LENGTH_LONG).show();
            }

            @Override
            public void onNewItem(@NotNull MonkeyItem item) {

                String textTalk = null;
                JsonObject params = new JsonObject();
                MOKMessage mokMessage;

                if(activeConversationItem != null && activeConversationItem.isGroup()){
                    textTalk = activeConversationItem.getName();
                }

                //Store the message in the DB and send it via MonkeyKit
                switch (MonkeyItem.MonkeyItemType.values()[item.getMessageType()]) {
                    case audio:
                        params = new JsonObject();
                        params.addProperty("length",""+item.getAudioDuration()/1000);

                        mokMessage = persistFileMessageAndSend(item.getFilePath(), myMonkeyID, myFriendID,
                            MessageTypes.FileTypes.Audio, params,
                            new PushMessage(EmojiHandler.encodeJavaForPush(myName) +
                            (textTalk==null ? " sent you an audio" : "sent an audio to " + textTalk) ), true);
                        break;
                    case photo:
                        mokMessage = persistFileMessageAndSend(item.getFilePath(), myMonkeyID, myFriendID,
                            MessageTypes.FileTypes.Photo, new JsonObject(),
                            new PushMessage(EmojiHandler.encodeJavaForPush(myName) +
                            (textTalk==null ? " sent you a photo" : "sent a photo to " + textTalk) ), true);
                        break;
                    default:
                        mokMessage = persistMessageAndSend(item.getMessageText(), myMonkeyID,
                            myFriendID, params, new PushMessage(EmojiHandler.encodeJavaForPush(myName) +
                            (textTalk==null ? " sent you a message" : " sent a message to " + textTalk) ), true);
                        break;
                }

                //Now that the message was sent, create a MessageItem using the MOKMessage that MonkeyKit
                //created. This MessageItem will be added to MonkeyAdapter so that it can be shown in
                //the screen.
                //USE THE DATETIMEORDER FROM MOKMESSAGE, NOT THE ONE FROM MONKEYITEM
                MessageItem newItem = new MessageItem(myMonkeyID, myFriendID, mokMessage.getMessage_id(),
                        item.getMessageText(), item.getMessageTimestamp(), mokMessage.getDatetimeorder(), item.isIncomingMessage(),
                        MonkeyItem.MonkeyItemType.values()[item.getMessageType()]);
                newItem.setParams(params.toString());
                newItem.setProps(mokMessage.getProps().toString());

                switch (MonkeyItem.MonkeyItemType.values()[item.getMessageType()]) {
                    case audio:
                        newItem.setAudioDuration(item.getAudioDuration()/1000);
                        newItem.setMessageContent(item.getFilePath());
                        newItem.setFileSize(mokMessage.getFileSize());
                        break;
                    case photo:
                        newItem.setMessageContent(item.getFilePath());
                        newItem.setFileSize(mokMessage.getFileSize());
                        break;
                }

                if(monkeyChatFragment != null)
                    monkeyChatFragment.smoothlyAddNewItem(newItem); // Add to recyclerView
                updateConversationByMessage(newItem, false);
            }
        };
    }

    public ConversationTransaction createTransactionFromMessage(final MessageItem message,
                                                                final boolean read) {
        return new ConversationTransaction() {
            @Override
            public void updateConversation(MonkeyConversation monkeyConversation) {
                long dateTime = message.getMessageTimestampOrder();
                ConversationItem conversation = (ConversationItem) monkeyConversation;
                conversation.setDatetime(dateTime > -1 ? dateTime : conversation.getDatetime());
                if (read) {
                    conversation.lastRead = message.getMessageTimestampOrder();
                    conversation.setTotalNewMessage(0);
                } else
                    conversation.setTotalNewMessage(conversation.getTotalNewMessages() + 1);
                Log.d("MainActivity", "transaction total new messages" + conversation.getTotalNewMessages());
                String secondaryText = DatabaseHandler.getSecondaryTextByMessageType(message, monkeyConversation.isGroup());
                conversation.setSecondaryText(secondaryText);

                int newStatus;
                if (!message.isIncomingMessage()) {
                    switch (message.getDeliveryStatus()) {
                        case sending:
                            newStatus = MonkeyConversation.ConversationStatus.sendingMessage.ordinal();
                            break;
                        case delivered:
                            newStatus = MonkeyConversation.ConversationStatus.deliveredMessage.ordinal();
                            break;
                        default:
                            throw new UnsupportedOperationException("tried to conversation with outgoing message with type error");
                    }
                } else {
                    newStatus = MonkeyConversation.ConversationStatus.receivedMessage.ordinal();
                }
                conversation.setStatus(newStatus);
            }
        };

    }


    /**
     * Updates a conversation in the database and then optionally adds it to the conversation list.
     * If conversation is not found in the database, fetch the conversation from server. This
     * method should be used when we want to update a conversation that is not in our
     * 'conversations' list.
     * @param conversationId id of the conversation to update
     * @param transaction transaction that will update the conversation.
     * @param addToList if true the conversation will be added to the UI when it is found in the DB.
     *                  Conversations found in server are always added to UI.
     */
    private void updateMissingConversation(final String conversationId,
                                           ConversationTransaction transaction, final boolean addToList) {
        String[] ids = { conversationId };
        asyncDBHandler.updateMissingConversationsTask(new UpdateConversationsTask.OnQueryReturnedListener() {
            @Override
            public void onQueryReturned(List<ConversationItem> results) {
                if(results.isEmpty()) { //conversation not in DB, request server
                    getConversationInfo(conversationId);
                } else if (addToList){ //conversation found in DB, add it to list
                    ConversationItem newItem = results.get(0);
                    conversations.addNewConversation(newItem);
                }
            }
        }, ids, transaction);
    }

    private  void updateConversationByMessage(MessageItem message, boolean read) {
        ConversationTransaction transaction = createTransactionFromMessage(message, read);
        ConversationItem conversation = (ConversationItem) conversations.findConversationById(message.conversationId);
        if (conversation != null) {
            conversations.updateConversation(conversation, transaction);
            DatabaseHandler.updateConversation(conversation);
        } else //find conversation elsewhere
            updateMissingConversation(message.getConversationId(), transaction, true);
    }

    /**
     * Add messages retrieved from DB to the messages list
     * @param oldMessages list of messages
     * @param hasReachedEnd boolean if messages has reached end
     */
    public void addOldMessages(ArrayList<MonkeyItem> oldMessages, boolean hasReachedEnd){
        if(oldMessages != null && oldMessages.size()>0 && monkeyChatFragment != null) {
            if(monkeyChatFragment.getConversationId().equals( ((MessageItem)oldMessages.get(0)).getConversationId())){
                monkeyChatFragment.addOldMessages(oldMessages, hasReachedEnd);
            }
        }else if(monkeyChatFragment != null) {
            monkeyChatFragment.addOldMessages(new ArrayList<MonkeyItem>(), hasReachedEnd);
        }
    }

    /**
     * Ask old messages from server
     * @param conversationId id of the conversation messages required
     */
    public void addOldMessagesFromServer(String conversationId){
        if(monkeyChatFragment!=null && monkeyChatFragment.getConversationId().equals(conversationId)) {
            String firstTimestamp = "0";
            if(monkeyChatFragment.getFirstMessage()!=null)
                firstTimestamp = ""+monkeyChatFragment.getFirstMessage().getMessageTimestamp();
            else if(messagesMap.get(conversationId)!=null && messagesMap.get(conversationId).size()>0)
                firstTimestamp = ""+new ArrayList<MonkeyItem>(messagesMap.get(conversationId)).get(0).getMessageTimestamp();
            getConversationMessages(conversationId, MESS_PERPAGE, firstTimestamp);
        }
    }

    /**
     * Change the status bar depending on the state
     * @param status status of the connection
     */
    public void setStatusBarState(Utils.ConnectionStatus status){

        if(monkeyFragmentManager==null)
            return;
        monkeyFragmentManager.showStatusNotification(status);

    }

    /**
     * Update a status message. This is normally used after you send a message.
     * @param oldId message old id
     * @param id message id
     * @param newStatus new status to change
     */

    private void updateMessage(String id, String oldId, MonkeyItem.DeliveryStatus newStatus) {
        MessageItem messageItem = DatabaseHandler.getMessageById(id);
        if(messageItem == null){
            messageItem = DatabaseHandler.getMessageById(oldId);
        }

        if (messageItem != null) {
            messageItem.setStatus(newStatus.ordinal());
            if(oldId != null){
                messageItem.setOldMessageId(oldId);
                messageItem.setMessageId(id);
                DatabaseHandler.updateMessageStatus(id, oldId, newStatus);

            }else{
                DatabaseHandler.updateMessageStatus(id, null, newStatus);
            }
            if (monkeyChatFragment != null) {
                MessageItem message = (MessageItem) monkeyChatFragment.findMonkeyItemById(oldId != null ? oldId : id);
                if (message != null) {
                    message.setStatus(newStatus.ordinal());
                    if(oldId != null){
                        message.setOldMessageId(oldId);
                        message.setMessageId(id);
                    }
                    monkeyChatFragment.updateMessageDeliveryStatus(message);
                }
            }
        }

    }

    /**
     * Creates a new MonkeyChatFragment and adds it to the activity.
     * @param chat conversation to display
     * @param hasReachedEnd true of the initial messages are the only existing messages of the chat
     */
    public void startChatWithMessages(ConversationItem chat, boolean hasReachedEnd){
        MonkeyChatFragment fragment = chat.isGroup() ?
                MonkeyChatFragment.Companion.newGroupInstance(chat.getConvId(), chat.getName(),
                        chat.getAvatarFilePath(), hasReachedEnd, chat.lastRead, chat.getGroupMembers()) :
                MonkeyChatFragment.Companion.newInstance(chat.getConvId(), chat.getName(),
                        chat.getAvatarFilePath(), hasReachedEnd, chat.lastRead);

         monkeyFragmentManager.setChatFragment(fragment, initInputListener(), voiceNotePlayer);
    }
    /**
     * Updates a sent message and updates de UI so that the user can see that it has been
     * successfully delivered
     * @param oldId The old Id of the message, set locally.
     * @param newId The new id of the message, set by the server.
     * @param read true if the message was delivered and read
     */
    private void markMessageAsDelivered(String oldId, String newId, boolean read){
        updateMessage(newId, oldId, MonkeyItem.DeliveryStatus.delivered);
    }

    /**
     * adds a message to the adapter so that it can be displayed in the RecyclerView.
     * @param message a received message
     */
    private MessageItem processNewMessage(MOKMessage message) {
        String conversationID = message.getConversationID(myMonkeyID);
        MessageItem newItem = DatabaseHandler.createMessage(message, downloadDir.getAbsolutePath(), myMonkeyID);
        if(monkeyChatFragment != null && monkeyChatFragment.getConversationId().equals(conversationID)) {
            monkeyChatFragment.smoothlyAddNewItem(newItem);
        }
        else if(messagesMap!=null && messagesMap.get(conversationID)!=null){
            messagesMap.get(conversationID).add(newItem);
        }
        else{
            ArrayList<MonkeyItem> monkeyItemArrayList = new ArrayList<>();
            monkeyItemArrayList.add(newItem);
            messagesMap.put(conversationID, monkeyItemArrayList);
        }
        return newItem;
    }

    /**
     * adds old messages to the adapter so that it can be displayed in the RecyclerView.
     * @param messages
     */
    private void processOldMessages(String conversationId, ArrayList<MOKMessage> messages){

        ArrayList<MessageItem> messageItems = new ArrayList<>();
        for(MOKMessage message: messages){
            messageItems.add(DatabaseHandler.createMessage(message, downloadDir.getAbsolutePath(), myMonkeyID));
        }
        Collections.sort(messageItems);
        DatabaseHandler.saveMessages(messageItems);
        if(monkeyChatFragment != null && monkeyChatFragment.getConversationId().equals(conversationId)) {
            monkeyChatFragment.addOldMessages(new ArrayList<MonkeyItem>(messageItems), messages.size() == 0);
        }
    }

    @Override
    public void onActivityResult (int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK) {
            return;
        }

        //Since our chat fragment uses different activities to take and edit photos, we must forward
        // the onActivityResult event to it so that it can react to the results of take photo, choose
        //photo and edit photo.
        if(monkeyChatFragment != null)
            monkeyChatFragment.onActivityResult(requestCode, resultCode, data);
    }

    /******
     * These are the methods that MonkeyKit calls to inform us about new events.
     */

    @Override
    public void storeSendingMessage(final MOKMessage message) {
        //TODO update conversation
        DatabaseHandler.storeNewMessage(DatabaseHandler.createMessage(message,
                downloadDir.getAbsolutePath(), myMonkeyID));
    }

    /******
     * These are the methods that MonkeyKit calls to inform us about new events.
     */

    @Override
    public void onSocketConnected() {
        super.onSocketConnected();
        setStatusBarState(Utils.ConnectionStatus.connected);
    }

    @Override
    public void onSocketDisconnected() {
        setStatusBarState(Utils.ConnectionStatus.connecting);
    }

    @Override
    public void onFileDownloadFinished(String fileMessageId, long fileMessageTimestamp,
                                       String conversationId, final boolean success) {
        //TODO use better search algorithm
        super.onFileDownloadFinished(fileMessageId, fileMessageTimestamp, conversationId, success);
//        updateMessage(fileMessageId, null,
//                success ? MonkeyItem.DeliveryStatus.delivered : MonkeyItem.DeliveryStatus.error);
        if (monkeyChatFragment != null && getActiveConversation().equals(conversationId)) {
            monkeyChatFragment.updateMessage(fileMessageId, fileMessageTimestamp, new MonkeyItemTransaction() {
                @Override
                public MonkeyItem invoke(MonkeyItem monkeyItem) {
                    MessageItem item = (MessageItem) monkeyItem;
                    item.setStatus(success ? MonkeyItem.DeliveryStatus.delivered.ordinal() :
                            MonkeyItem.DeliveryStatus.error.ordinal());
                    return item;
                }
            });
        }
    }

    @Override
    public void onAcknowledgeRecieved(@NotNull final String senderId, @NotNull final String recipientId,
                                      final @NotNull String newId, final @NotNull String oldId, final boolean read,
                                      final int messageType) {
        //Always call super so that MKDelegate knows that it should not attempt to retry this message anymore
        super.onAcknowledgeRecieved(senderId, recipientId, newId, oldId, read, messageType);

        asyncDBHandler.getMessageById(new FindMessageTask.OnQueryReturnedListener() {
            @Override
            public void onQueryReturned(MessageItem result) {
                if(result != null){
                    if(!read)
                        markMessageAsDelivered(oldId, newId, read);
                    else if(getActiveConversation() != null && senderId.equals(getActiveConversation()))
                        monkeyChatFragment.setLastRead(result.getMessageTimestampOrder());
                    updateConversationByMessage(result, read);

                } else if((messageType == Integer.parseInt(MessageTypes.MOKText)
                        || messageType == Integer.parseInt(MessageTypes.MOKFile))){
                    //If we use the same monkeyId for several devices (multisession) we receive an
                    // acknowledge for each message sent. So to validate if we have the message
                    // sent, we can send a sync message.
                    sendSync();
                }

                List<MonkeyItem> conversationMessageList = messagesMap.get(senderId);
                if(conversationMessageList != null) {
                    Iterator<MonkeyItem> iter = conversationMessageList.iterator();
                    while (iter.hasNext()) {
                        MessageItem updateMessage = (MessageItem) iter.next();
                        if (updateMessage.getMessageId().equals(oldId) || updateMessage.getMessageId().equals(newId)) {
                            updateMessage.setStatus(MonkeyItem.DeliveryStatus.delivered.ordinal());
                            break;
                        }
                    }
                }
            }
        }, oldId, newId);
    }

    @Override
    public void onCreateGroup(String groupMembers, String groupName, String groupID, Exception e) {
        Log.d("TEST", "group create");
        if(e==null){
            ConversationItem conversationItem = new ConversationItem(groupID,
                    groupName, System.currentTimeMillis(), "Write to this group",
                    0, true, groupMembers, "", MonkeyConversation.ConversationStatus.empty.ordinal());
            conversations.addNewConversation(conversationItem); //TODO update silently??
        }
    }

    @Override
    public void onAddGroupMember(@Nullable String groupID, @Nullable String members, @Nullable Exception e) {
        Log.d("TEST", "group add");
    }

    @Override
    public void onRemoveGroupMember(@Nullable String groupID, @Nullable String members, @Nullable Exception e) {
        Log.d("TEST", "remove member");
    }

    @Override
    public void onUpdateUserData(@NotNull String monkeyId, @Nullable Exception e) {

    }

    @Override
    public void onUpdateGroupData(@NotNull String groupId, @Nullable Exception e) {

    }

    @Override
    public void onMessageReceived(@NonNull MOKMessage message) {
        MessageItem newItem = processNewMessage(message);
        Log.d("MainActivity", "active conv " + (activeConversationItem != null));
        updateConversationByMessage(newItem, activeConversationItem != null &&
                activeConversationItem.getConvId().equals(newItem.getConversationId()));
    }

    /**
     * Update conversations in memory and the update the MonkeyConversationsFragment.
     */
    private void syncConversationsFragment() {
        int totalConversations = conversations.size();
        asyncDBHandler.getConversationPage(new GetConversationPageTask.OnQueryReturnedListener() {
            @Override
            public void onQueryReturned(List<ConversationItem> conversationPage) {
                conversations.addOldConversations(conversationPage, false);
            }
        }, totalConversations, 0);
    }

    private void syncChatFragment(boolean deletedMessages, int totalNewMessages) {
        final String activeConversationId = getActiveConversation();
        if(monkeyChatFragment != null) {
            //update fragment
            if(deletedMessages) {
                asyncDBHandler.getMessagePage(new GetMessagePageTask.OnQueryReturnedListener() {
                    @Override
                    public void onQueryReturned(List<MessageItem> messagePage) {
                        if(activeConversationId.equals(getActiveConversation()) && monkeyChatFragment != null) {
                            monkeyChatFragment.smoothlyAddNewItems(messagePage);
                        }
                    }
                }, activeConversationId, totalNewMessages, 0);
            } else {
                List<MonkeyItem> currentMessages = monkeyChatFragment.takeAllMessages();
                asyncDBHandler.getMessagePage(new GetMessagePageTask.OnQueryReturnedListener() {
                    @Override
                    public void onQueryReturned(List<MessageItem> messagePage) {
                        if(activeConversationId.equals(getActiveConversation()) && monkeyChatFragment != null) {
                            monkeyChatFragment.insertMessages(messagePage);
                        }
                    }
                }, activeConversationId, currentMessages.size(), 0);
            }
        }
    }

    /**
     * Updates the conversation that user may have closed. This updates the lastOpen, secondaryText
     * and totalNewMessages, so that when the user goes back to the conversation list, he/she sees
     * up-to-date data.
     * @param conversationId ID of the conversation to update
     * @param lastOpen timestamp with the last time conversation was open. should be the datetime of last message
     * @param lastMessageText the secondary text to put in the conversation
     */
    public void updateClosedConversation(String conversationId, final long lastOpen, final String lastMessageText) {
        if(activeConversationItem != null && activeConversationItem.getConvId().equals((conversationId))) {
            ConversationTransaction t = new ConversationTransaction() {
                @Override
                public void updateConversation(@NotNull MonkeyConversation conversation) {
                    ConversationItem conversationItem = (ConversationItem) conversation;
                    conversationItem.lastOpen = lastOpen;
                    conversationItem.setTotalNewMessage(0);
                    conversationItem.setSecondaryText(lastMessageText);
                }
            };
            //Apply the transaction on the UI
            conversations.updateConversation(activeConversationItem, t);
            //Apply same transaction on the DB
            DatabaseHandler.updateConversation(activeConversationItem);
        } else {
            //throw new IllegalStateException("Tried to update the lastOpen of a non-active conversation");
            IllegalStateException exception = new IllegalStateException("Tried to update the lastOpen of a non-active conversation");
            exception.printStackTrace();
        }
    }

    private void syncNotifications(List<MOKNotification> notifications) {
        Iterator<MOKNotification> notificationIterator = notifications.iterator();
        while (notificationIterator.hasNext()) {
            MOKNotification not = notificationIterator.next();
            if (not.getProps().has("monkey_action")) {
                int type = not.getProps().get("monkey_action").getAsInt();
                try {
                    switch (type) {
                        case com.criptext.comunication.MessageTypes.MOKGroupNewMember:
                            onGroupNewMember(not.getReceiverId(), not.getProps().get("new_member").getAsString());
                            break;
                        case com.criptext.comunication.MessageTypes.MOKGroupRemoveMember:
                            onGroupRemovedMember(not.getReceiverId(), not.getSenderId());
                            break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onSyncComplete(@NonNull HttpSync.SyncData syncData) {
        Log.d("MainActivity", "Sync complete");
        syncStatus.cancelMessage();

        final String activeConversationId = getActiveConversation();
        boolean activeConversationNeedsUpdate = false;
        final HashMap<String, List<MOKMessage>> newMessagesMap = syncData.getNewMessages();
        HashMap<String, List<MOKDelete>> deletesMap = syncData.getDeletes();

        Iterator<String> iterator = syncData.getConversationsToUpdate().iterator();
        while (iterator.hasNext()) {
            String convId = iterator.next();

            if((activeConversationId != null) && activeConversationId.equals(convId)) {
                activeConversationNeedsUpdate = true;
            }
            //clear cached messages of updated conversations
            messagesMap.remove(convId);
        }

        final List<MOKMessage> activeConversationMessages = newMessagesMap.get(activeConversationId);
        //if active conversation has been updated, update the chat fragment
        if(activeConversationNeedsUpdate && activeConversationMessages != null)
            syncChatFragment(!deletesMap.containsKey(activeConversationId),
                    activeConversationMessages.size());

        syncConversationsFragment();

        syncNotifications(syncData.getNotifications());

    }

    @Override
    public void onGroupAdded(final String groupid, final String members, final JsonObject info) {
        asyncDBHandler.getConversationById(new FindConversationTask.OnQueryReturnedListener() {
            @Override
            public void onQueryReturned(ConversationItem result) {
                if(result == null) {
                    result = new ConversationItem(groupid, info.has("name") ?
                        info.get("name").getAsString() : "Uknown Group", System.currentTimeMillis(),
                        "Write to this group", 0, true, members, info.has("avatar") ?
                        info.get("avatar").getAsString() : "", MonkeyConversation.ConversationStatus.empty.ordinal());
                    DatabaseHandler.saveConversations(new ConversationItem[]{result});
                    conversations.addNewConversation(result);
                }
            }
        }, groupid);
    }

    @Override
    public void onGroupNewMember(String groupid, final String new_member) {


    }

    @Override
    public void onGroupRemovedMember(String groupid, final String removed_member) {

        ConversationTransaction transaction = new ConversationTransaction() {
            @Override
            public void updateConversation(@NotNull MonkeyConversation conversation) {
                ConversationItem convToUpdate = (ConversationItem) conversation;
                convToUpdate.removeMember(removed_member);
            }
        };

        ConversationItem group = (ConversationItem) conversations.findConversationById(groupid);
        if(group != null) {

            if(groupData!=null && groupData.getConversationId().equals(groupid)){
                groupData.removeMember(removed_member);
                groupData.setInfoList(myMonkeyID, myName);
            }
            conversations.updateConversation(group, transaction);
            DatabaseHandler.updateConversation(group);

        } else
            updateMissingConversation(groupid, transaction, false);
    }

    @Override
    public void onDeleteConversation(@NotNull String conversationId, @Nullable Exception e) {
        //not supported
    }

    @Override
    public void onGetGroupInfo(@NotNull MOKConversation mokConversation, @Nullable Exception e) {
        if(e==null){
            String convName = "Unknown group";
            String admins = "";
            JsonObject userInfo = mokConversation.getInfo();
            if(userInfo!=null && userInfo.has("name"))
                convName = userInfo.get("name").getAsString();
            ConversationItem conversationItem = new ConversationItem(mokConversation.getConversationId(),
                    convName, System.currentTimeMillis(), "Write to this group",
                    1, true, mokConversation.getMembers()!=null? TextUtils.join("," ,mokConversation.getMembers()):"",
                    mokConversation.getAvatarURL(), MonkeyConversation.ConversationStatus.empty.ordinal());
            if(userInfo!=null && userInfo.has("admin")) {
                admins = userInfo.get("admin").getAsString();
                conversationItem.setAdmins(admins);
            }

            asyncDBHandler.storeNewConversation(new StoreNewConversationTask.OnQueryReturnedListener() {
                @Override
                public void onQueryReturned(ConversationItem result) {
                conversations.addNewConversation(result); //TODO update silently?
                }
            }, conversationItem);

        }
    }

    @Override
    public void onGetUserInfo(@NotNull MOKUser mokUser, @Nullable Exception e) {

        if(e==null){
            String convName = "Unknown";
            JsonObject userInfo = mokUser.getInfo();
            if(userInfo!=null && userInfo.has("name"))
                convName = userInfo.get("name").getAsString();
            ConversationItem conversationItem = new ConversationItem(mokUser.getMonkeyId(),
                    convName, System.currentTimeMillis(), "Write to this contact",
                    1, false, "", mokUser.getAvatarURL(), MonkeyConversation.ConversationStatus.empty.ordinal());
            asyncDBHandler.storeNewConversation(new StoreNewConversationTask.OnQueryReturnedListener() {
                @Override
                public void onQueryReturned(ConversationItem result) {
                conversations.addNewConversation(result); //TODO update silently??
                }
            }, conversationItem);
        }

    }

    @Override
    public void onGetUsersInfo(@NotNull ArrayList<MOKUser> mokUsers, @Nullable Exception e) {
        if(e==null && groupData!=null && monkeyChatFragment!=null) {
            groupData.setMembers(monkeyChatFragment.getConversationId(), mokUsers);
            groupData.setAdmins(DatabaseHandler.getConversationById(monkeyChatFragment.getConversationId()).getAdmins());
            groupData.setInfoList(myMonkeyID, myName);
            monkeyChatFragment.reloadAllMessages();
            if(monkeyInfoFragment != null){
                monkeyInfoFragment.setInfo(groupData.getInfoList());
            }
        }
    }

    @Override
    public void onGetConversations(@NotNull ArrayList<MOKConversation> fetchedConversations, @Nullable Exception e) {
        //ALWAYS CALL SUPER FOR THIS CALLBACK!!
        super.onGetConversations(fetchedConversations, e);
        if(e!=null) {
            e.printStackTrace();
            return;
        }

        if(fetchedConversations.isEmpty())
          conversations.setHasReachedEnd(true);
        else
            Log.d("MainActvity", "getconversations. first is " + fetchedConversations.get(0).getConversationId());

        ArrayList<ConversationItem> monkeyConversations = new ArrayList<>();
        for(MOKConversation mokConversation : fetchedConversations){
            String convName = "Unknown";
            String admins = null;
            String secondaryText = "Write to this conversation";
            if(mokConversation.isGroup())
                secondaryText = "Write to this group";
            JsonObject convInfo = mokConversation.getInfo();
            if(convInfo!=null && convInfo.has("name"))
                convName = convInfo.get("name").getAsString();
            MessageItem lastItem = null;
            if(mokConversation.getLastMessage() != null)
            lastItem = DatabaseHandler.createMessage(mokConversation.getLastMessage(),
                    downloadDir.getAbsolutePath(), myMonkeyID);
            ConversationItem conversationItem = new ConversationItem(mokConversation.getConversationId(),
                    convName, mokConversation.getLastModified(),
                    DatabaseHandler.getSecondaryTextByMessageType(lastItem, mokConversation.isGroup()),
                    mokConversation.getUnread(),
                    mokConversation.isGroup(), mokConversation.getMembers()!=null? TextUtils.join("," ,mokConversation.getMembers()):"",
                    mokConversation.getAvatarURL(),
                    MonkeyConversation.ConversationStatus.receivedMessage.ordinal());
            if(convInfo!=null && convInfo.has("admin")) {
                admins = convInfo.get("admin").getAsString();
                conversationItem.setAdmins(admins);
            }
            if(mokConversation.getUnread()>0) {
                conversationItem.status = MonkeyConversation.ConversationStatus.receivedMessage.ordinal();
            }
            else if(mokConversation.getLastMessage()!=null){
                if(mokConversation.getLastMessage().isMyOwnMessage(myMonkeyID)){
                    conversationItem.status = MonkeyConversation.ConversationStatus.deliveredMessage.ordinal();
                }
                else{
                    conversationItem.status = MonkeyConversation.ConversationStatus.receivedMessage.ordinal();
                }
            }
            monkeyConversations.add(conversationItem);
        }
        ConversationItem[] conversationItems = new ConversationItem[monkeyConversations.size()];
        for(int i = 0; i < monkeyConversations.size(); i++){
            conversationItems[i] = new ConversationItem(monkeyConversations.get(i));
        }

        if(conversationItems.length > 0)
            asyncDBHandler.storeConversationPage(new SaveModelTask.OnQueryReturnedListener() {
                @Override
                public void onQueryReturned(Model[] storedModels) {
                    ConversationItem[] storedConversations = (ConversationItem[]) storedModels;
                    conversations.addOldConversations(Arrays.asList(storedConversations),
                            storedModels.length == 0);
                }
            }, conversationItems);
    }

    @Override
    public void onGetConversationMessages(@NotNull String conversationId, @NotNull ArrayList<MOKMessage> messages, @Nullable Exception e) {

        if(e!=null) {
            e.printStackTrace();
            return;
        }
        processOldMessages(conversationId, messages);
    }

    @Override
    public void onFileFailsUpload(MOKMessage message) {
        super.onFileFailsUpload(message);
        updateMessage(message.getMessage_id(), null, MonkeyItem.DeliveryStatus.error);
    }


    @Override
    public void onConversationOpenResponse(String senderId, Boolean isOnline, String lastSeen,
                                           String lastOpenMe, String members_online) {
        if(monkeyFragmentManager!=null && monkeyChatFragment!=null) {
            if(!monkeyChatFragment.getConversationId().equals(senderId)){
                return;
            }

            String subtitle = isOnline? "Online":"";

            long lastSeenValue = -1L;
            boolean isGroupConversation = senderId.contains("G:");
            if(isGroupConversation){
                groupData.setMembersOnline(members_online);
                int membersOnline = members_online != null ? members_online.split(",").length : 0;
                if(membersOnline > 0) {
                    subtitle = membersOnline + " " + (membersOnline > 1 ? "members online" : "member online");
                }
                groupData.setInfoList(myMonkeyID, myName);
                if(monkeyInfoFragment != null){
                    monkeyInfoFragment.setInfo(groupData.getInfoList());
                }
            }
            else if(!isOnline){
                if(lastSeen.isEmpty())
                    lastSeenValue = 0L;
                else
                    lastSeenValue = Long.valueOf(lastSeen) * 1000;
                subtitle = "Last seen: "+Utils.Companion.getFormattedDate(lastSeenValue, this);
            }
            if(!subtitle.isEmpty()) {
                monkeyFragmentManager.setSubtitle(subtitle);
            }

            lastSeenValue = (activeConversationItem.lastRead > lastSeenValue ? activeConversationItem.lastRead : lastSeenValue);
            updateConversationLastRead(senderId, lastSeenValue);
        }
    }

    /**
     * Updates the lastRead value of a conversation. If the  conversation is active, the chatFragment
     * is updated to reflect the new value. if conversation does not exist
     * @param conversationId
     * @param newLastReadValue
     */
    public void updateConversationLastRead(String conversationId, final long newLastReadValue) {
        if(conversationId.startsWith("G:"))
            return; //don't update group conversations

        ConversationTransaction transaction = new ConversationTransaction() {
                @Override
                public void updateConversation(@NotNull MonkeyConversation conversation) {
                    ConversationItem conversationItem = (ConversationItem) conversation;
                    conversationItem.lastRead = newLastReadValue;
                }
            };

        ConversationItem openedConversation = (ConversationItem) conversations.findConversationById(conversationId);
        if(openedConversation != null) {
            if (newLastReadValue > openedConversation.lastRead) {
                //Only update conversation's last read if the new last read value is greater than the
                // current one.

                if(monkeyChatFragment!=null && monkeyChatFragment.getConversationId().equals(conversationId))
                    monkeyChatFragment.setLastRead(newLastReadValue);

                conversations.updateConversation(openedConversation, new ConversationTransaction() {
                    @Override
                    public void updateConversation(@NotNull MonkeyConversation conversation) {
                        ConversationItem conversationItem = (ConversationItem) conversation;
                        conversationItem.lastRead = newLastReadValue;
                    }
                });
                DatabaseHandler.updateConversation(openedConversation);
            }
        }
         else updateMissingConversation(conversationId, transaction, false);
    }

    @Override
    public void onDeleteReceived(String messageId, String senderId, String recipientId) {

    }

    @Override
    public void onContactOpenMyConversation(String monkeyId) {
        //Update the conversation status
        final long newLastReadValue = System.currentTimeMillis();
        updateConversationLastRead(monkeyId, newLastReadValue);

    }

    @Override
    public void onNotificationReceived(String messageId, String senderId, String recipientId, JsonObject params, String datetime) {
        int type = params.get("type").getAsInt();
        if(recipientId.contains("G:")){
            if(monkeyChatFragment != null && monkeyChatFragment.getConversationId().equals(recipientId)){
                if(type == 21) {
                    groupData.addMemberTyping(senderId);
                    monkeyFragmentManager.setSubtitle(groupData.getMembersNameTyping());
                }else if (type ==20){
                    groupData.removeMemberTyping(senderId);
                    monkeyFragmentManager.setSubtitle(groupData.getMembersNameTyping());
                }
            }
        }else{
            if(monkeyChatFragment != null && monkeyChatFragment.getConversationId().equals(senderId)){
                if(type == 21) {
                    monkeyFragmentManager.setSubtitle("Typing...");
                }else if (type ==20){
                    monkeyFragmentManager.setSubtitle("Online");
                }
            }
        }
    }

    @NotNull
    @Override
    public Class<?> getServiceClassName() {
        //Provide the class of the service that we subclassed so that MKActivityDelegate can automatically
        //handle the binding and unbinding for us.
        return MyServiceClass.class;
    }

    @Override
    public  void onConnectionRefused(){
        setStatusBarState(Utils.ConnectionStatus.connected);
        Toast.makeText(this, "Login failed. Please check your Monkey Kit credentials", Toast.LENGTH_LONG).show();
    }

    /** CHAT ACTIVITY METHODS **/

    @Override
    public boolean isOnline() {
        //Use connectivity service to check if there's an active internet connection.
        ConnectivityManager connectivityManager = (ConnectivityManager) getApplicationContext().
                getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager
                .getActiveNetworkInfo();
        return activeNetworkInfo != null;
    }

    @Override
    public void onFileUploadRequested(@NotNull MonkeyItem item) {
        if(item.getDeliveryStatus() == MonkeyItem.DeliveryStatus.error) {
            updateMessage(item.getMessageId(), null, MonkeyItem.DeliveryStatus.sending);
            if(monkeyChatFragment != null)
                monkeyChatFragment.rebindMonkeyItem(item);
        }
        boolean msgIsResending = resendFile(item.getMessageId());
        if(!msgIsResending){
            MessageItem message = (MessageItem) item;
            MOKMessage resendMessage = new MOKMessage(message.getMessageId(), myMonkeyID, myFriendID,
                    message.getMessageText(), "" + message.getMessageTimestamp(), "" + message.getMessageType(),
                    message.getJsonParams(), message.getJsonProps());
            resendFile(resendMessage, new PushMessage("You have a new message from the sample app"), true);
        }
    }

    @Override
    public void onFileDownloadRequested(@NotNull MonkeyItem item) {

        if(item.getDeliveryStatus() == MonkeyItem.DeliveryStatus.error) {
            //If the message failed to download previously, mark it as sending and rebind.
            //Rebinding will update the UI to a loading view and call this method again to start
            //the download
            MessageItem message = (MessageItem) item;
            message.setStatus(MonkeyItem.DeliveryStatus.sending.ordinal());
            if(monkeyChatFragment != null) {
                monkeyChatFragment.rebindMonkeyItem(message);
            }
        } else { //Not error status, download the file.
            final MessageItem messageItem = (MessageItem) item;
            downloadFile(messageItem.getMessageId(), messageItem.getFilePath(),
                    messageItem.getJsonProps(), messageItem.getSenderId(),
                    messageItem.getMessageTimestampOrder(), getActiveConversation());
        }

    }

    @Override
    public void onLoadMoreMessages(final String conversationId, int currentMessageCount) {
        asyncDBHandler.getMessagePage(new GetMessagePageTask.OnQueryReturnedListener() {
            @Override
            public void onQueryReturned(List<MessageItem> messageItems) {
                if(messageItems.size() > 0){
                    if(monkeyChatFragment != null) {
                        monkeyChatFragment.addOldMessages(new ArrayList<MonkeyItem>(messageItems), false);
                    }
                }
                else {
                    addOldMessagesFromServer(conversationId);
                }
            }
        }, conversationId, MESS_PERPAGE, currentMessageCount);
    }

    @Override
    public void onDestroyWithPendingMessages(@NotNull ArrayList<MOKMessage> errorMessages) {
        DatabaseHandler.markMessagesAsError(errorMessages);
    }

    @Override
    public void setChatFragment(@Nullable MonkeyChatFragment chatFragment) {
        Log.d("MainActivity", "set chat fragment");
        monkeyChatFragment = chatFragment;
    }

    @Override
    public void deleteChatFragment(@Nullable MonkeyChatFragment chatFragment) {
        if(monkeyChatFragment != null && monkeyChatFragment == chatFragment ){
            monkeyChatFragment = null;
        }
    }

    @NotNull
    @Override
    public List<MonkeyItem> getInitialMessages(String conversationId) {
        myFriendID = conversationId;
        return messagesMap.get(conversationId);
    }

    @Override
    public GroupChat getGroupChat(@NotNull String conversationId, @NonNull String membersIds) {
        if(conversationId.contains("G:") && (groupData == null || !groupData.getConversationId().equals(conversationId))) {
            groupData = new GroupData(conversationId, membersIds, this);
        }
        else if(groupData!=null && !groupData.getConversationId().equals(conversationId)){
            groupData = null;
        }
        return groupData;
    }

    @Override
    public void retainMessages(@NotNull String conversationId, @NotNull List<? extends MonkeyItem> messages) {
        if(messagesMap!=null)
            messagesMap.put(conversationId, (List<MonkeyItem>) messages);
        if(!messages.isEmpty()) {
            MonkeyItem lastItem = messages.get(messages.size() - 1);
            long lastOpenValue = lastItem.getMessageTimestampOrder();
            //We don;t actually know if the conversation is a group but it's not important here.
            updateClosedConversation(conversationId, lastOpenValue,
                    DatabaseHandler.getSecondaryTextByMessageType(lastItem, false));
        }
        activeConversationItem = null;
    }

    @Override
    public void onStartChatFragment(@NonNull String conversationId) {
        setActiveConversation(conversationId);
    }

    @Override
    public void onStopChatFragment(@NonNull String conversationId) {
        setActiveConversation(null);
        if(voiceNotePlayer != null)
            voiceNotePlayer.setupNotificationControl(new PlaybackNotification(R.mipmap.ic_launcher,
                    " Playing voice note"));
    }

    /** CONVERSATION ACTIVITY METHODS **/
    @Override
    public ConversationsList onRequestConversations() {
        //TODO rewrite async
        if(!conversations.isEmpty())
            return conversations;
        else {
            int firstBatchSize = 20;
            List<ConversationItem> firstConversations = DatabaseHandler.getConversations(firstBatchSize, 0);
            //INSER CONVERSATIONS SHOULD BE CALLED ONLY HERE!!!
            conversations.insertConversations(firstConversations, firstConversations.size() < firstBatchSize);
            return conversations;
        }
    }

    @Override
    public void onConversationClicked(final @NotNull MonkeyConversation conversation) {

        activeConversationItem = (ConversationItem) conversation;
        List<MonkeyItem> messages = messagesMap.get(conversation.getConvId());
        if(messages!=null && !messages.isEmpty()){
            //Get initial messages from memory
            startChatWithMessages((ConversationItem) conversation, false);
        }
        else{
            //Get initial messages from DB
            asyncDBHandler.getMessagePage(new GetMessagePageTask.OnQueryReturnedListener() {
                @Override
                public void onQueryReturned(List<MessageItem> messageItems) {
                    messagesMap.put(conversation.getConvId(), new ArrayList<MonkeyItem>(messageItems));
                    startChatWithMessages((ConversationItem) conversation, false);
                }
            }, conversation.getConvId(), MESS_PERPAGE, 0);
        }
    }

    @Override
    public void setConversationsFragment(@Nullable MonkeyConversationsFragment monkeyConversationsFragment) {
        if(conversations != null){
            conversations.setListUI(monkeyConversationsFragment);
        }

    }

    @Override
    public void onLoadMoreConversations(int loadedConversations) {
        final int conversationsToLoad = 50;
        final int conversationsToRequest = 20;
        asyncDBHandler.getConversationPage(new GetConversationPageTask.OnQueryReturnedListener() {
            @Override
            public void onQueryReturned(List<ConversationItem> conversationPage) {
                if(conversationPage.isEmpty()) {
                    MonkeyConversation lastItem;
                    if((lastItem = conversations.getLastConversation()) != null) {
                        getConversationsFromServer(conversationsToRequest, lastItem.getDatetime() / 1000);
                    } else {
                        getConversationsFromServer(conversationsToRequest, 0);
                    }
                } else {
                    conversations.addOldConversations(conversationPage, false);
                }
            }
        }, conversationsToLoad, loadedConversations);
    }

    @Override
    public void onConversationDeleted(@NotNull MonkeyConversation conversation) {

        if (conversation.isGroup()) {
            removeGroupMember(conversation.getConvId(), myMonkeyID);
        } else {
            deleteConversation(conversation.getConvId());
        }
        DatabaseHandler.deleteConversation((ConversationItem) conversation);

    }

    @Override
    public void onMessageRemoved(@NotNull MonkeyItem item, boolean unsent) {
        if(unsent){
            unsendMessage(item.getSenderId(), item.getConversationId(), item.getMessageId());
        }else{
            DatabaseHandler.deleteMessage((MessageItem)item);
        }
    }

    @Override
    public void onClickToolbar(@NotNull String monkeyID, @NotNull String name, @NotNull String lastSeen, @NotNull String avatarURL){
        if(monkeyInfoFragment == null){
            MonkeyInfoFragment infoFragment = MonkeyInfoFragment.Companion.newInfoInstance(
                    monkeyChatFragment.getConversationId(),
                    monkeyChatFragment.getConversationId().contains("G:"));
            monkeyFragmentManager.setInfoFragment(infoFragment);
            if(getCurrentFocus() != null){
                getCurrentFocus().clearFocus();
            }
        }
    }

    @Override
    public void setInfoFragment(@Nullable MonkeyInfoFragment infoFragment) {
        monkeyInfoFragment = infoFragment;
    }

    @Override
    public void requestUsers() {

    }

    @Nullable
    @Override
    public ArrayList<MonkeyInfo> getInfo(String conversationId) {
        if(monkeyChatFragment.getConversationId().contains("G:")){
            return groupData.getInfoList();
        }
        Iterator it = conversations.iterator();
        ArrayList<MonkeyInfo> infoList = new ArrayList<>();

        while(it.hasNext()){
            MonkeyConversation monkeyConversation = (MonkeyConversation) it.next();

            if(monkeyConversation.getStatus() == MonkeyConversation.ConversationStatus.moreConversations.ordinal()){
                continue;
            }

            ConversationItem conversation = (ConversationItem)monkeyConversation;
            if(conversation.getConvId().contains("G:") && conversation.getGroupMembers().contains(myMonkeyID) &&
                    conversation.getGroupMembers().contains(conversationId)){
                infoList.add(conversation);
            }
        }

        Collections.sort(infoList, new Comparator<MonkeyInfo>() {
            @Override
            public int compare(MonkeyInfo lhs, MonkeyInfo rhs) {
                return lhs.getTitle().toLowerCase().compareTo(rhs.getTitle().toLowerCase());
            }
        });

        return infoList;

    }

    @Override
    public void onInfoItemClick(@NotNull MonkeyInfo infoItem) {
        if(infoItem.getInfoId().contains("G:")){
            activeConversationItem = (ConversationItem) infoItem;
            final ConversationItem conversation = (ConversationItem) infoItem;
            List<MonkeyItem> messages = messagesMap.get(conversation.getConvId());
            if(messages!=null && !messages.isEmpty()){
                startChatFromInfo(conversation, false);
            }else{
                //Get initial messages from DB
                asyncDBHandler.getMessagePage(new GetMessagePageTask.OnQueryReturnedListener() {
                    @Override
                    public void onQueryReturned(List<MessageItem> messageItems) {
                        messagesMap.put(conversation.getConvId(), new ArrayList<MonkeyItem>(messageItems));
                        startChatFromInfo(conversation, false);
                    }
                }, conversation.getConvId(), MESS_PERPAGE, 0);
            }
        }else{
            ArrayList<MonkeyConversation> conversationsList = new ArrayList<>(conversations);
            ConversationItem conversationUser = null;
            for(MonkeyConversation conv : conversationsList) {
                if(conv.getConvId().equals(infoItem.getInfoId())) {
                    conversationUser = (ConversationItem)conv;
                    break;
                }
            }
            if(conversationUser == null){
                //return;
                ConversationItem conversationItem = new ConversationItem(infoItem.getInfoId(),
                        infoItem.getTitle(), System.currentTimeMillis(), "Write to this Conversation",
                        0, false, "", infoItem.getAvatarUrl(), MonkeyConversation.ConversationStatus.empty.ordinal());
                conversationsList.add(conversationItem);
                startChatFromInfo(conversationItem, true);
                DatabaseHandler.saveConversations(new ConversationItem[]{conversationItem});
                return;
            }
            final ConversationItem conversationUserCopy = conversationUser;
            List<MonkeyItem> messages = messagesMap.get(conversationUser.getConvId());
            if(messages!=null && !messages.isEmpty()){
                startChatFromInfo(conversationUser, false);
            }else{
                //Get initial messages from DB
                asyncDBHandler.getMessagePage(new GetMessagePageTask.OnQueryReturnedListener() {
                    @Override
                    public void onQueryReturned(List<MessageItem> messageItems) {
                        messagesMap.put(conversationUserCopy.getConvId(), new ArrayList<MonkeyItem>(messageItems));
                        startChatFromInfo(conversationUserCopy, false);
                    }
                }, conversationUser.getConvId(), MESS_PERPAGE, 0);
            }


        }
    }

    public void startChatFromInfo(ConversationItem chat, boolean hasReachedEnd){
        MonkeyChatFragment fragment = chat.isGroup() ?
                MonkeyChatFragment.Companion.newGroupInstance(chat.getConvId(), chat.getName(),
                        chat.getAvatarFilePath(), hasReachedEnd, chat.lastRead, chat.getGroupMembers()) :
                MonkeyChatFragment.Companion.newInstance(chat.getConvId(), chat.getName(),
                        chat.getAvatarFilePath(), hasReachedEnd, chat.lastRead);

        activeConversationItem = chat;
        monkeyFragmentManager.setChatFragmentFromInfo(fragment, initInputListener(), voiceNotePlayer);
        monkeyInfoFragment = null;
    }

    @Override
    public void onExitGroup(@NotNull String conversationId) {

        removeGroupMember(conversationId, myMonkeyID);
        ConversationItem group = (ConversationItem) conversations.findConversationById(conversationId);
        int pos = conversations.getConversationPositionByTimestamp(group);
        conversations.removeConversationAt(pos);
        DatabaseHandler.deleteConversation(group);

        monkeyFragmentManager.popStack(2);
    }

    @Override
    public void deleteAllMessages(@NotNull String conversationId) {
        DatabaseHandler.deleteAll(conversationId);
        if(monkeyChatFragment != null) monkeyChatFragment.clearMessages();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(monkeyChatFragment != null)
            monkeyChatFragment.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void removeMember(@NotNull String monkeyId) {

    }
}