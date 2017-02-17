package com.criptext.http;

import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;

import com.androidquery.callback.AjaxCallback;
import com.androidquery.callback.AjaxStatus;
import com.criptext.MonkeyKitSocketService;
import com.criptext.comunication.AsyncConnSocket;
import com.criptext.comunication.CBTypes;
import com.criptext.comunication.MOKConversation;
import com.criptext.comunication.MOKMessage;
import com.criptext.comunication.MOKUser;
import com.criptext.comunication.MessageTypes;
import com.criptext.lib.KeyStoreCriptext;
import com.criptext.security.AESUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by danieltigse on 7/20/16.
 */

public class UserManager extends AQueryHttp {

    public UserManager(MonkeyKitSocketService service, AESUtil aesUtil) {
        super(service, aesUtil);
    }

    private MOKMessage decryptMessage(MOKMessage remote) {
        String existingKey = KeyStoreCriptext.getString(serviceRef.get(), remote.getSid());
        String validKey = OpenConversationTask.Companion.attemptToDecrypt(remote, clientData,
                aesUtil, existingKey);
        if(validKey != null && !validKey.equals(existingKey))
            KeyStoreCriptext.putString(serviceRef.get(),
                    remote.getSid(), validKey);
        else if(validKey == null)
            remote = null;
        return remote;
    }

    public void updateUserData(final String monkeyId, JSONObject info){

        try {
            String urlconnect = MonkeyKitSocketService.Companion.getHttpsURL()+"/user/update";

            JSONObject localJSONObject1 = new JSONObject();
            localJSONObject1.put("monkeyId",monkeyId);
            localJSONObject1.put("params",info);

            Map<String, Object> params = new HashMap<String, Object>();
            params.put("data", localJSONObject1.toString());

            aq.auth(handle).ajax(urlconnect, params, JSONObject.class, new AjaxCallback<JSONObject>(){
                @Override
                public void callback(String url, JSONObject response, AjaxStatus status) {

                    MonkeyKitSocketService service = serviceRef.get();
                    if(service==null)
                        return;

                    if(response!=null)
                        service.processMessageFromHandler(CBTypes.onUpdateUserData, new Object[]{monkeyId, null});
                    else
                        service.processMessageFromHandler(CBTypes.onUpdateUserData, new Object[]{
                                monkeyId, new Exception("Error code:"+status.getCode()+" -  Error msg:"+status.getMessage())});
                }
            });

        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    public void getUserInfoById(final String monkeyid){

        String endpoint = "/user/info/"+monkeyid;

        aq.auth(handle).ajax(MonkeyKitSocketService.Companion.getHttpsURL()+endpoint, JSONObject.class, new AjaxCallback<JSONObject>() {
            @Override
            public void callback(String url, JSONObject response, AjaxStatus status) {

                MonkeyKitSocketService service = serviceRef.get();
                if(service==null)
                    return;

                if (response != null) {
                    try {
                        JsonParser jsonParser = new JsonParser();
                        JsonObject gsonObject = (JsonObject)jsonParser.parse(response.getJSONObject("data").toString());
                        service.processMessageFromHandler(CBTypes.onGetUserInfo, new Object[]{
                                new MOKUser(monkeyid, gsonObject), null});
                    } catch (Exception e) {
                        e.printStackTrace();
                        service.processMessageFromHandler(CBTypes.onGetUserInfo, new Object[]{
                                new MOKUser(monkeyid), e});
                    }
                }
                else{
                    service.processMessageFromHandler(CBTypes.onGetUserInfo, new Object[]{
                            new MOKUser(monkeyid), "Error code:"+status.getCode()+" -  Error msg:"+status.getMessage()});
                }
            }
        });
    }

    public void getUsersInfo(final String monkeyIds){

        String endpoint = "/users/info";

        try {
            JSONObject localJSONObject1 = new JSONObject();
            localJSONObject1.put("monkey_ids", monkeyIds);

            Map<String, Object> params = new HashMap<String, Object>();
            params.put("data", localJSONObject1.toString());

            aq.auth(handle).ajax(MonkeyKitSocketService.Companion.getHttpsURL() + endpoint, params, JSONObject.class, new AjaxCallback<JSONObject>() {
                @Override
                public void callback(String url, JSONObject response, AjaxStatus status) {

                    MonkeyKitSocketService service = serviceRef.get();
                    if (service == null)
                        return;

                    if (response != null) {
                        try {
                            ArrayList<MOKUser> mokUserArrayList = new ArrayList<MOKUser>();
                            for(int i=0; i<response.getJSONArray("data").length(); i++){
                                JSONObject jsonObject = response.getJSONArray("data").getJSONObject(i);
                                JsonParser jsonParser = new JsonParser();
                                JsonObject gsonObject = (JsonObject) jsonParser.parse(jsonObject.toString());
                                mokUserArrayList.add(new MOKUser(gsonObject.get("monkey_id").getAsString(), gsonObject));
                            }

                            service.processMessageFromHandler(CBTypes.onGetUsersInfo, new Object[]{
                                    mokUserArrayList, null});
                        } catch (Exception e) {
                            e.printStackTrace();
                            service.processMessageFromHandler(CBTypes.onGetUsersInfo, new Object[]{
                                    null, e});
                        }
                    } else {
                        service.processMessageFromHandler(CBTypes.onGetUsersInfo, new Object[]{
                                new Exception("Error code:" + status.getCode() + " -  Error msg:" + status.getMessage())});
                    }
                }
            });
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    public void getConversations(String monkeyid, int qty, long timestamp){

        String urlconnect = MonkeyKitSocketService.Companion.getHttpsURL()+"/user/conversations";

        try {
            JSONObject localJSONObject1 = new JSONObject();
            localJSONObject1.put("monkeyId", monkeyid);
            localJSONObject1.put("qty", qty);
            localJSONObject1.put("timestamp", timestamp);

            Map<String, Object> params = new HashMap<String, Object>();
            params.put("data", localJSONObject1.toString());

            Log.d("ServiceStartup", "getConversations " + qty);
            aq.auth(handle).ajax(urlconnect, params, JSONObject.class, new AjaxCallback<JSONObject>() {
                @Override
                public void callback(String url, final JSONObject response, AjaxStatus status) {

                    if (serviceRef.get() == null)
                        return;

                    Log.d("ServiceStartup", "getConversations resp");
                    if (response != null)
                        new AsyncTask<Object, String, Exception>(){

                            List<MOKConversation> conversationList = new ArrayList<MOKConversation>();

                            @Override
                            protected Exception doInBackground(Object... p) {

                                try {
                                    JsonParser parser = new JsonParser();
                                    JsonObject props = new JsonObject(), params = new JsonObject();
                                    JSONArray jsonArrayConversations = response.getJSONObject("data").getJSONArray("conversations");
                                    JsonArray array = (JsonArray) parser.parse(jsonArrayConversations.toString());
                                    for (int i = 0; i < array.size(); i++) {
                                        JsonObject currentConv = null;
                                        JsonObject currentMessage = null;
                                        MOKMessage remote = null;
                                        try {
                                            JsonElement jsonMessage = array.get(i);
                                            currentConv = jsonMessage.getAsJsonObject();
                                            currentMessage = currentConv.getAsJsonObject("last_message");
                                            //init params props
                                            if (currentMessage.has("params") && !currentMessage.get("params").isJsonNull() && !parser.parse(currentMessage.get("params").getAsString()).isJsonNull())
                                                if (parser.parse(currentMessage.get("params").getAsString()) instanceof JsonObject)
                                                    params = (JsonObject) parser.parse(currentMessage.get("params").getAsString());
                                            if (currentMessage.has("props") && !currentMessage.get("props").isJsonNull() && !parser.parse(currentMessage.get("props").getAsString()).isJsonNull())
                                                props = (JsonObject) parser.parse(currentMessage.get("props").getAsString());

                                            if (currentMessage.has("type") && (currentMessage.get("type").getAsString().compareTo(MessageTypes.MOKText) == 0
                                                    || currentMessage.get("type").getAsString().compareTo(MessageTypes.MOKFile) == 0)) {

                                                remote = AsyncConnSocket.createMOKMessageFromJSON(currentMessage, params, props, true);
                                                if (remote.getProps().has("encr") && remote.getProps().get("encr").getAsString().compareTo("1") == 0) {
                                                    remote = decryptMessage(remote);
                                                } else if (remote.getProps().has("encoding") && !remote.getType().equals(MessageTypes.MOKFile)) {
                                                    if (remote.getProps().get("encoding").getAsString().equals("base64"))
                                                        remote.setMsg(new String(Base64.decode(remote.getMsg().getBytes(), Base64.NO_WRAP)));
                                                }
                                            }

                                            JsonArray jsonArray = currentConv.has("members") ? currentConv.get("members").getAsJsonArray() : new JsonArray();
                                            ArrayList<String> arrayList = new ArrayList<String>();
                                            for(int j = 0; j<jsonArray.size(); j++){
                                                arrayList.add(jsonArray.get(j).getAsString());
                                            }
                                            conversationList.add(new MOKConversation(currentConv.get("id").getAsString(),
                                                    currentConv.get("info").getAsJsonObject(),
                                                    arrayList.toArray(new String[arrayList.size()]),
                                                    remote, currentConv.get("last_seen").getAsLong()*1000, currentConv.get("unread").getAsInt(),
                                                    currentConv.has("last_modified") ? (long)currentConv.get("last_modified").getAsDouble()*1000 : 0));

                                        } catch (IllegalArgumentException ex) {
                                            Log.e("MonkeyKit", "Error fetching conversation: " +
                                                    ex.getMessage());
                                        } catch (Exception ex) {
                                            ex.printStackTrace();
                                        }
                                    }
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                    return e;
                                }

                                return null;
                            }

                            @Override
                            protected void onPostExecute(Exception e) {
                                MonkeyKitSocketService service = serviceRef.get();
                                Log.d("ServiceStartup", "getConversations post exec " + (service != null));
                                if (service != null)
                                    service.processMessageFromHandler(CBTypes.onGetConversations, new Object[]{
                                        conversationList, e});
                            }

                        }.execute("");
                    else {
                        MonkeyKitSocketService service = serviceRef.get();
                        Log.d("ServiceStartup", "failiure " + (service != null));
                        if (service != null)
                            service.processMessageFromHandler(CBTypes.onGetConversations,
                                    new Object[]{new ArrayList<MOKConversation>(), new Exception(
                                    "Error code:" + status.getCode() + " -  Error msg:" + status.getMessage())});
                    }
                }
            });
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    public void getConversationMessages(String monkeyid, final String conversationId, int numberOfMessages
            , String lastTimeStamp, final AsyncConnSocket asyncConnSocket){

        String newlastTimeStamp = lastTimeStamp;
        if(lastTimeStamp==null || lastTimeStamp.length()==0 || lastTimeStamp.equals("0"))
            newlastTimeStamp = "";

        String urlconnect = MonkeyKitSocketService.Companion.getHttpsURL()+"/conversation/messages/"+monkeyid+"/"+conversationId+"/"+numberOfMessages+"/"+newlastTimeStamp;
        aq.auth(handle).ajax(urlconnect, JSONObject.class, new AjaxCallback<JSONObject>(){
            @Override
            public void callback(String url, final JSONObject response, AjaxStatus status) {

                final MonkeyKitSocketService service = serviceRef.get();
                if(service==null)
                    return;

                if(response!=null){

                    new AsyncTask<Object, String, Exception>() {

                        List<MOKMessage> messageList = new ArrayList<MOKMessage>();

                        @Override
                        protected Exception doInBackground(Object... p) {

                            try {
                                JsonParser parser = new JsonParser();
                                JsonObject props = new JsonObject(), params = new JsonObject();
                                JSONArray jsonArrayMessages = response.getJSONObject("data").getJSONArray("messages");
                                JsonArray array = (JsonArray)parser.parse(jsonArrayMessages.toString());
                                MOKMessage remote;
                                for (int i = 0; i < array.size(); i++) {
                                    JsonObject currentMessage = null;
                                    try {
                                        JsonElement jsonMessage = array.get(i);
                                        currentMessage = jsonMessage.getAsJsonObject();
                                        //init params props
                                        if (currentMessage.has("params") && !currentMessage.get("params").isJsonNull() && !parser.parse(currentMessage.get("params").getAsString()).isJsonNull())
                                            if (parser.parse(currentMessage.get("params").getAsString()) instanceof JsonObject)
                                                params = (JsonObject) parser.parse(currentMessage.get("params").getAsString());
                                        if (currentMessage.has("props") && !currentMessage.get("props").isJsonNull() && !parser.parse(currentMessage.get("props").getAsString()).isJsonNull())
                                            props = (JsonObject) parser.parse(currentMessage.get("props").getAsString());

                                        if (currentMessage.has("type") && (currentMessage.get("type").getAsString().compareTo(MessageTypes.MOKText) == 0
                                                || currentMessage.get("type").getAsString().compareTo(MessageTypes.MOKFile) == 0)) {

                                            remote = asyncConnSocket.createMOKMessageFromJSON(currentMessage, params, props, true);
                                            if (remote.getProps().has("encr") && remote.getProps().get("encr").getAsString().compareTo("1") == 0) {
                                                remote = decryptMessage(remote);

                                            } else if (remote.getProps().has("encoding") && !remote.getType().equals(MessageTypes.MOKFile)) {
                                                if(remote.getProps().get("encoding").getAsString().equals("base64"))
                                                    remote.setMsg(new String(Base64.decode(remote.getMsg().getBytes(), Base64.NO_WRAP)));
                                            }
                                            if (remote != null)
                                                messageList.add(remote);
                                        }
                                        else{
                                            remote = new MOKMessage(currentMessage.get("id").getAsString(),
                                                    currentMessage.get("sid").getAsString(),
                                                    currentMessage.get("rid").getAsString(),
                                                    currentMessage.get("msg").getAsString(),
                                                    currentMessage.get("datetime").getAsString(),
                                                    currentMessage.get("type").getAsString(),params,props);
                                            remote.setDatetimeorder(Long.parseLong(remote.getDatetime())*1000);
                                            messageList.add(remote);
                                        }
                                    }
                                    catch( Exception ex){
                                        ex.printStackTrace();
                                    }
                                }

                            } catch (JSONException e) {
                                e.printStackTrace();
                                return e;
                            }

                            return null;
                        }

                        @Override
                        protected void onPostExecute(Exception e) {
                            service.processMessageFromHandler(CBTypes.onGetConversationMessages, new Object[]{
                                    conversationId, messageList, null});
                        }
                    }.execute("");

                }
                else
                    service.processMessageFromHandler(CBTypes.onGetConversationMessages, new Object[]{new ArrayList<MOKMessage>(),
                            new Exception("Error code:"+status.getCode()+" -  Error msg:"+status.getMessage())});
            }
        });

    }

    public void deleteConversation(final String monkeyid, final String conversation_id){

        try{
            String urlconnect = MonkeyKitSocketService.Companion.getHttpsURL()+"/user/delete/conversation";

            JSONObject localJSONObject1 = new JSONObject();
            localJSONObject1.put("conversation_id", conversation_id);
            localJSONObject1.put("monkey_id", monkeyid);

            Map<String, Object> params = new HashMap<String, Object>();
            params.put("data", localJSONObject1.toString());

            aq.auth(handle).ajax(urlconnect, params, JSONObject.class, new AjaxCallback<JSONObject>(){
                @Override
                public void callback(String url, JSONObject response, AjaxStatus status) {

                    MonkeyKitSocketService service = serviceRef.get();
                    if(service==null)
                        return;

                    if(response!=null)
                        try {
                            service.processMessageFromHandler(CBTypes.onDeleteConversation, new Object[]{
                                    response.getJSONObject("data").getString("conversation"), null});
                        } catch (JSONException e) {
                            e.printStackTrace();
                            service.processMessageFromHandler(CBTypes.onDeleteConversation, new Object[]{
                                    conversation_id, e});
                        }
                    else{
                        service.processMessageFromHandler(CBTypes.onDeleteConversation, new Object[]{conversation_id,
                                new Exception("Error code:"+status.getCode()+" -  Error msg:"+status.getMessage())});
                    }
                }
            });
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

}
