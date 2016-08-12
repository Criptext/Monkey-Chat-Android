package com.criptext.http;

import com.androidquery.callback.AjaxCallback;
import com.androidquery.callback.AjaxStatus;
import com.criptext.MonkeyKitSocketService;
import com.criptext.comunication.CBTypes;
import com.criptext.security.AESUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by gesuwall on 6/23/16.
 */
public class GroupManager extends AQueryHttp {

    public GroupManager(MonkeyKitSocketService service, AESUtil aesUtil) {
        super(service, aesUtil);
    }

    public void updateGroupData(String monkeyId, JSONObject info){

        try {
            String urlconnect = MonkeyKitSocketService.Companion.getHttpsURL()+"/group/update";

            JSONObject localJSONObject1 = new JSONObject();
            localJSONObject1.put("monkeyId",monkeyId);
            localJSONObject1.put("params",info);

            Map<String, Object> params = new HashMap<String, Object>();
            params.put("data", localJSONObject1.toString());

            aq.auth(handle).ajax(urlconnect, params, JSONObject.class, new AjaxCallback<JSONObject>(){
                @Override
                public void callback(String url, JSONObject response, AjaxStatus status) {

                    if(serviceRef.get()!=null)
                        return;

                    if(response!=null)
                        serviceRef.get().processMessageFromHandler(CBTypes.onUpdateGroupData, new Object[]{null});
                    else
                        serviceRef.get().processMessageFromHandler(CBTypes.onUpdateGroupData, new Object[]{
                                "Error code:"+status.getCode()+" -  Error msg:"+status.getMessage()});
                }
            });

        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    public void createGroup(String members, String group_name, String group_id){

        try {
            String urlConnect = MonkeyKitSocketService.Companion.getHttpsURL() +"/group/create";

            JSONObject localJSONObjectInfo = new JSONObject();
            localJSONObjectInfo.put("name", group_name);

            JSONObject localJSONObject1 = new JSONObject();
            localJSONObject1.put("members",members);
            localJSONObject1.put("info",localJSONObjectInfo);
            localJSONObject1.put("session_id", monkeyID);
            if(group_id!=null)
                localJSONObject1.put("group_id", group_id);

            Map<String, Object> params = new HashMap<String, Object>();
            params.put("data", localJSONObject1.toString());

            aq.auth(handle).ajax(urlConnect, params, JSONObject.class, new AjaxCallback<JSONObject>(){
                @Override
                public void callback(String url, JSONObject response, AjaxStatus status) {

                    if(serviceRef.get()!=null)
                        return;

                    if (response != null) {
                        try {
                            serviceRef.get().processMessageFromHandler(CBTypes.onCreateGroup, new Object[]{
                                    response.getJSONObject("data").getString("group_id"), null});
                        } catch (JSONException e) {
                            e.printStackTrace();
                            serviceRef.get().processMessageFromHandler(CBTypes.onCreateGroup, new Object[]{
                                    null, e});
                        }
                    }
                    else{
                        serviceRef.get().processMessageFromHandler(CBTypes.onCreateGroup, new Object[]{
                                null, "Error code:"+status.getCode()+" -  Error msg:"+status.getMessage()});
                    }
                }
            });

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void removeGroupMember(String group_id, String monkey_id){

        try {
            String urlConnect = MonkeyKitSocketService.Companion.getHttpsURL() +"/group/delete";

            JSONObject localJSONObject1 = new JSONObject();
            localJSONObject1.put("group_id", group_id);
            localJSONObject1.put("session_id", monkey_id);

            Map<String, Object> params = new HashMap<String, Object>();
            params.put("data", localJSONObject1.toString());

            aq.auth(handle).ajax(urlConnect, params, JSONObject.class, new AjaxCallback<JSONObject>(){
                @Override
                public void callback(String url, JSONObject response, AjaxStatus status) {

                    if(serviceRef.get()!=null)
                        return;

                    if (response != null) {
                        try {
                            serviceRef.get().processMessageFromHandler(CBTypes.onRemoveGroupMember, new Object[]{
                                    response.getJSONObject("data").getString("members"), null});
                        } catch (JSONException e) {
                            e.printStackTrace();
                            serviceRef.get().processMessageFromHandler(CBTypes.onRemoveGroupMember, new Object[]{
                                    null, e});
                        }
                    }
                    else{
                        serviceRef.get().processMessageFromHandler(CBTypes.onRemoveGroupMember, new Object[]{
                                null, "Error code:"+status.getCode()+" -  Error msg:"+status.getMessage()});
                    }
                }
            });

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void addGroupMember(String new_member, String group_id){
        try {

            String urlConnect = MonkeyKitSocketService.Companion.getHttpsURL() +"/group/addmember";

            JSONObject localJSONObject1 = new JSONObject();
            localJSONObject1.put("session_id", monkeyID);
            localJSONObject1.put("group_id", group_id);
            localJSONObject1.put("new_member",new_member);

            Map<String, Object> params = new HashMap<String, Object>();
            params.put("data", localJSONObject1.toString());

            System.out.println("MONKEY - Sending api - "+localJSONObject1);

            aq.auth(handle).ajax(urlConnect, params, JSONObject.class, new AjaxCallback<JSONObject>(){
                @Override
                public void callback(String url, JSONObject response, AjaxStatus status) {

                    if(serviceRef.get()!=null)
                        return;

                    if (response != null) {
                        try {
                            serviceRef.get().processMessageFromHandler(CBTypes.onAddGroupMember, new Object[]{
                                    response.getJSONObject("data").getString("members"), null});
                        } catch (JSONException e) {
                            e.printStackTrace();
                            serviceRef.get().processMessageFromHandler(CBTypes.onAddGroupMember, new Object[]{
                                    null, e});
                        }
                    }
                    else{
                        serviceRef.get().processMessageFromHandler(CBTypes.onAddGroupMember, new Object[]{
                                null, "Error code:"+status.getCode()+" -  Error msg:"+status.getMessage()});
                    }
                }
            });

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

}
