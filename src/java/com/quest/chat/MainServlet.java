/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.quest.chat;

import com.quest.access.common.mysql.DataType;
import com.quest.access.common.mysql.Database;
import com.quest.access.common.mysql.NonExistentDatabaseException;
import com.quest.access.common.mysql.Table;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author connie
 */
@WebServlet(name = "MainServlet", urlPatterns = {"/main"})
@MultipartConfig
public class MainServlet extends HttpServlet {
    
    public static Database database; // an instance of the database
    public static ConcurrentHashMap sessions=new ConcurrentHashMap();
    private static ConcurrentHashMap<String, ConcurrentHashMap<String,ArrayList>> messages=new ConcurrentHashMap();
    public static ConcurrentHashMap<String,String> accessTokens=new ConcurrentHashMap();
    private static ConcurrentHashMap<String,String[]> preTokens=new ConcurrentHashMap();
    public static ConcurrentHashMap<String,String> fileNames=new ConcurrentHashMap();
    private final static Logger LOGGER =Logger.getLogger(DownloadServlet.class.getCanonicalName());
    private final long DELETE_FILE_DELAY=3600000;
    /** 
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
         try{
            String json = request.getParameter("json");
            if(json==null){
               String [] currentUser=getCurrentUser(request, response, false);
               uploadFile(request, response, currentUser);
               return;
            }
            
            JSONObject obj=new JSONObject(json);
            JSONObject headers = obj.optJSONObject("request_header");
            String msg=headers.optString("request_msg");
            JSONObject req_obj=(JSONObject)obj.optJSONObject("request_object");
            if("login".equals(msg)){ 
                 doLogin(req_obj, response,msg);  
                 return;
             }  
            else if("change_pass".equals(msg)){
                changePass(req_obj, response, msg);
                return;
            }
            
            else if("create_account".equals(msg)){
              createUser(req_obj, response, msg);
                return;
            }
          
            String[] currentUser = ensureIntegrity(response, request);
            if("add_friend".equals(msg)){
              addFriend(req_obj, response, msg, currentUser);
            }
            else if("pre_token".equals(msg)){
               String toName=req_obj.optString("to_name");
               String fromName=req_obj.optString("from_name");
               String tokenID=req_obj.optString("token_id");
               String friendID=req_obj.optString("friend_id");
               String message=req_obj.optString("message");
               preTokens.put(fromName, new String[]{toName,tokenID,message,friendID});
               JSONObject object=new JSONObject();
               object.put("success","true");
               object.put("request_msg",msg);
               response.getWriter().print(object);
               
            }
            else if("real_time".equals(msg)){
               getRealTimeData(response, msg, currentUser);
            }
            else if("send_msg".equals(msg)){
               sendMessage(req_obj, response, msg, currentUser);
            }
            else if("logout".equals(msg)){
              doLogOut(currentUser, response);
            }
            else if("delete_friend".equals(msg)){
               String friendID=req_obj.optString("friend_id");
               Database.executeQuery("DELETE FROM FRIEND_DATA WHERE FRIEND_ID=?",database.getDatabaseName(),new String[]{friendID});
               JSONObject object=new JSONObject();
               JSONObject details=new JSONObject();
               details.put("success", "true");
               object.put("request_msg", msg);
               object.put("response",details);
               response.getWriter().print(object);
            }
            else  if("friend_list".equals(msg)){
              friendList(response, msg, currentUser);
            }
            else  if("share_msg".equals(msg)){
              shareMessage(req_obj, response, msg, currentUser);
            }
             else  if("share_file".equals(msg)){
              shareFile(req_obj, response, msg, currentUser);
            }
            else  if("fetch_conv".equals(msg)){
              fetchConversation(req_obj, response, msg, currentUser);
            }
          
         }
         catch(Exception e){
           e.printStackTrace();  
         }
      
    }
    
    
    private void fetchConversation(JSONObject obj,HttpServletResponse response,String msg, String[] currentUser){
        try {
            String friendID = obj.optString("friend_id");
            String userID=(String) ((Session)sessions.get(currentUser[0])).getAttribute("user_id"); 
            String sql;
            JSONObject data;
            if(friendID.equals("all")){
               sql="SELECT * FROM CHAT_DATA WHERE FROM_ID=? OR TO_ID=? LIMIT 10" ; //FETCH ALL CONVERSATIONS
               data = database.queryDatabase(sql, new String[]{userID,userID});
            }
            else{
              sql="SELECT * FROM CHAT_DATA WHERE (FROM_ID=? AND TO_ID=?) OR (TO_ID=? AND FROM_ID=?) LIMIT 10 "; // FETCH CONVERSATION WITH SPECIFIC FRIEND
              data = database.queryDatabase(sql, new String[]{userID,friendID,userID,friendID});
            }
            
            JSONObject details=new JSONObject();
            JSONObject object =new JSONObject();
            details.put("success", "true");
            details.put("data", data);
            object.put("request_msg", msg);
            object.put("response",details);
            response.getWriter().print(object);
            
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    
    
    private void getRealTimeData(HttpServletResponse response,String msg, String [] currentUser){
        try {
           
            ConcurrentHashMap message = messages.get(currentUser[0]);
            JSONObject object=new JSONObject();
            JSONObject details=new JSONObject();
            if(message==null || message.isEmpty()){
              details.put("success", "false");
              object.put("request_msg", msg);
              object.put("response",details);
              response.getWriter().print(object);
              return;
            }
            // message composition fromName and arraylist of messages
            Enumeration keys = message.keys();
            Enumeration elements = message.elements();
            JSONObject data=new JSONObject();
            while(keys.hasMoreElements()){
               JSONArray arr=new JSONArray((ArrayList)elements.nextElement());
               data.put((String)keys.nextElement(), arr);
            }
          
            details.put("data", data);
            object.put("request_msg", msg);
            object.put("response",details);
            response.getWriter().print(object);
            message.clear();
        } catch (Exception ex) {
            Logger.getLogger(MainServlet.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    
    private void friendList(HttpServletResponse response,String msg, String[] currentUser){
        try {
            String userID=(String) ((Session)sessions.get(currentUser[0])).getAttribute("user_id");
            String sql1="SELECT FRIEND_ID FROM FRIEND_DATA WHERE USER_ID=?";
            JSONObject data1 = database.queryDatabase(sql1,new String[]{userID}); //RETURNS FRIEND IDS
            StringBuilder lastSql = new StringBuilder();
            JSONArray arr=data1.optJSONArray("FRIEND_ID");
            JSONObject object=new JSONObject();
            JSONObject details=new JSONObject();
            int arrLen=arr.length();
            if(arrLen==0){
                //no friends
              details.put("success","false");
              object.put("request_msg", msg);
              object.put("response",details);
              response.getWriter().print(object);
              return;
            }
            String[] params=new String[arrLen];
            lastSql.append("USER_ID = ?");
            params[0]=arr.getString(0);
            for(int x=1; x<arrLen; x++){
                lastSql.append(" OR USER_ID = ?");
                params[x]=arr.getString(x);
            }
            String sql="SELECT USER_NAME,USER_ID,IS_ONLINE FROM USERS WHERE "+lastSql;
            JSONObject data = database.queryDatabase(sql,params);
            details.put("data",data);
            object.put("request_msg", msg);
            object.put("response",details);
            response.getWriter().print(object);
        } catch (Exception ex) {
            Logger.getLogger(MainServlet.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private void shareMessage(JSONObject obj, HttpServletResponse response,String msg, String[] currentUser){
       try {
          String fromName=currentUser[0];
          JSONArray toNames=obj.optJSONArray("to_names");
          String txt=obj.optString("message");
          JSONArray friendIDs=obj.optJSONArray("to_ids");
          Session ses=(Session) sessions.get(fromName);
          String userID=(String) ses.getAttribute("user_id");
          JSONObject object=new JSONObject();
          JSONObject details=new JSONObject();
          JSONArray notSent=new JSONArray();
          for(int x=0;x<toNames.length(); x++){ 
               if(!isFriend(friendIDs.getString(x), userID)){
                 notSent.put(toNames.getString(x));
               }
               else{
                 storeMessage(toNames.getString(x), fromName, txt);
               }
          }
            details.put("success", "true");
            details.put("not_sent",notSent);
            object.put("request_msg", msg);
            object.put("response",details);
            response.getWriter().print(object);
           } catch (Exception ex) {
                ex.printStackTrace();
          }
    }
    
    private void sendMessage(JSONObject obj, HttpServletResponse response,String msg, String[] currentUser){
        try {
            String fromName=currentUser[0];
            String toName=obj.optString("to_name");
            String txt=obj.optString("message");
            String friendID=obj.optString("to_id");
            Session ses=(Session) sessions.get(fromName);
            String userID=(String) ses.getAttribute("user_id");
            JSONObject object=new JSONObject();
            JSONObject details=new JSONObject();
            if(!isFriend(friendID, userID)){
              details.put("success", "false");
              details.put("reason", "you can only send messages to people who have you in their friend lists and you have them in your friend list");
              object.put("request_msg", msg);
              object.put("response",details);
              response.getWriter().print(object);  
              return;
            }
           
            if(ses==null){
            
            }
            else{
              storeMessage(toName, fromName, txt);
            }
              database.doInsert("CHAT_DATA",new String[]{"0",userID,friendID,txt,"!NOW()"});
              details.put("success", "true");
              object.put("request_msg", msg);
              object.put("response",details);
              response.getWriter().print(object);
        } catch (JSONException | IOException ex) {
            Logger.getLogger(MainServlet.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
        
    private void storeMessage(String toName, String fromName,String txt){
     if(messages.get(toName)==null){
          messages.put(toName,new ConcurrentHashMap()); 
        }
       if( messages.get(toName).get(fromName)==null) {
           messages.get(toName).put(fromName,new ArrayList());   
         }
       messages.get(toName).get(fromName).add(txt);   
    }
    
    private void doLogOut(String[] currentUser, HttpServletResponse response){
        try {
            sessions.remove(currentUser[0]);
            Database.setValue(database, "USERS", "IS_ONLINE", "0", "USER_NAME='"+currentUser[0]+"'");
            JSONObject object=new JSONObject();
            object.put("request_msg", "redirect");
            object.put("url","index.html");
            response.getWriter().print(object);
        } catch (Exception ex) {
            Logger.getLogger(MainServlet.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    
 
    private void addFriend(JSONObject obj, HttpServletResponse response,String msg,String[] currentUser){
        try {
          JSONObject object=new JSONObject();
          JSONObject details=new JSONObject();
          String friendName=obj.optString("name");
          String userID=(String) ((Session)sessions.get(currentUser[0])).getAttribute("user_id");
          String friendID = Database.getValue("USER_ID","USERS","USER_NAME", friendName, database);
          if(friendID==null || friendID.equals("")){
               details.put("success", "false");
               details.put("reason", "friend does not exist");
               object.put("request_msg", msg);
               object.put("response",details);
               response.getWriter().print(object); 
               return;
           }
          String sql="SELECT FRIEND_ID "
                  + " FROM FRIEND_DATA WHERE USER_ID=? AND FRIEND_ID=?";
          JSONObject data = database.queryDatabase(sql, new String[]{userID,friendID});
          if(data.optJSONArray("FRIEND_ID").length()>0){
               details.put("success", "false");
               details.put("reason", "friend already exists");
               object.put("request_msg", msg);
               object.put("response",details);
               response.getWriter().print(object); 
               return;
           }
          
          if(!friendName.equals("") && !friendName.equals(currentUser[0])){
            database.doInsert("FRIEND_DATA", new String[]{userID,friendID,"!NOW()"});
            details.put("success", "true");
            object.put("request_msg", msg);
            object.put("response",details);
            response.getWriter().print(object);
            String txt=currentUser[0]+" has added you as a friend, to respond add him to you friend list";
            storeMessage(friendName,currentUser[0], txt);
          }
          else{
           details.put("success", "false");
           object.put("request_msg", msg);
           object.put("response",details);
           response.getWriter().print(object);
          }
        }
        catch(Exception e){
          e.printStackTrace();  
        }
    }
    
    private void createUser(JSONObject obj, HttpServletResponse response,String msg){
        try {
            String name=obj.optString("username");
            String pass=obj.optString("password");
            JSONObject object=new JSONObject();
            JSONObject details=new JSONObject();
            if(Database.ifValueExists(name,"USERS","USER_NAME",database)){ 
               details.put("success", "false");
               details.put("reason", "user already exists");
               object.put("request_msg", msg);
               object.put("response",details);
               response.getWriter().print(object); 
               return;
            } 
            UniqueRandom rand=new UniqueRandom(8);
            String userId=rand.nextRandom();
            byte[] bytes=Security.makePasswordDigest(name,pass.toCharArray());
            String passw=Security.toBase64(bytes);
            database.doInsert("USERS", new String[]{userId,name,passw,"0","!NOW()","0"});
            details.put("success", "true");
            object.put("request_msg", msg);
            object.put("response",details);
            response.getWriter().print(object);
        } catch (Exception ex) {
            Logger.getLogger(MainServlet.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    
       private void doLogin(JSONObject obj, HttpServletResponse response,String msg){
        //when a user logs in remember his role, username and random id
        //send this to the client
         String name=obj.optString("username");
         String pass=obj.optString("password");
         JSONObject object=new JSONObject();
         boolean auth=authenticateUser(name, pass);
         try {
            if(auth){
                String changePass=Database.getValue("CHANGE_PASS","USERS","USER_NAME",name, database);
                if(changePass.equals("1")){
                  object.put("request_msg","redirect");
                  object.put("url","changePass.html");
                  response.getWriter().print(object); 
                  return;
                }
                Session ses=new Session();
                String userID=Database.getValue("USER_ID","USERS","USER_NAME",name, database);
                ses.setAttribute("user_id", userID);
                sessions.put(name,ses);
                Database.setValue(database, "USERS", "IS_ONLINE", "1", "USER_NAME='"+name+"'");
                response.addCookie(new Cookie("user", name));
                response.addCookie(new Cookie("rand", ses.getSessionId()));
                 object.put("request_msg","redirect");
                 object.put("url","main.html");  
              
               response.getWriter().print(object);
          }
          else{ 
              object.put("request_msg", msg);
              object.put("response",auth );
              response.getWriter().print(object);
            }
                
         } catch (Exception ex) {
                Logger.getLogger(MainServlet.class.getName()).log(Level.SEVERE, null, ex);
         }
        
    }
       
       
    private String[] ensureIntegrity(HttpServletResponse response,HttpServletRequest request){
       try{
        String [] currentUser=getCurrentUser(request, response, true);
       //now check whether this is what we have on the server
        Session ses=(Session) sessions.get(currentUser[0]);
        if(ses==null){
         doLogOut(currentUser, response);
         return null;
       }
       if(ses.getSessionId().equals(currentUser[1])){
           // this is the right user
           return currentUser;
       }
       else{
                //make the user login again
          doLogOut(currentUser, response);
          return null;
       }
    }
      catch(Exception e){
        e.printStackTrace();
        return null;
        
      }
    }
    
    
    private String [] getCurrentUser(HttpServletRequest request,HttpServletResponse response, boolean logout){
         String [] currentUser=new String[3];
         Cookie[] cookies = request.getCookies();
         if(cookies==null || cookies.length==0){
           if(logout){
              doLogOut(currentUser, response);
           }
           return null;
        }
      
       String username=null;
       String rand = null;
       for(int x=0; x<cookies.length; x++){
          String name=cookies[x].getName();
          String value=cookies[x].getValue();
          if("user".equals(name)){
            username=value; 
            currentUser[0]=username;
          }
          else if(name.equals("rand")){
             rand=value; 
             currentUser[1]=rand;
          }
       }
      return currentUser;
    }
    
    
     private boolean authenticateUser(String userName, String pass){
        try{
             String pass_user=Security.toBase64(Security.makePasswordDigest(userName, pass.toCharArray()));
             String pass_stored=Database.getValue("PASS_WORD","USERS","USER_NAME",userName,database);
             if(pass_user.equals(pass_stored)){
                 return true;
              }
             return false;
        
           } catch(Exception e){
              e.printStackTrace();
              return false; 
        }
    }
     
     private void changePass(JSONObject obj, HttpServletResponse response,String msg){
        try {
            String name=obj.optString("username");
            String oldPass=obj.optString("old_password");
            String newPass=obj.optString("new_password");
            JSONObject object=new JSONObject();
            String old_pass=Security.toBase64(Security.makePasswordDigest(name, oldPass.toCharArray()));
            String pass_stored=Database.getValue("PASS_WORD","USERS","USER_NAME",name,database);
            if(old_pass.equals(pass_stored)){
                 byte[] bytes=Security.makePasswordDigest(name,newPass.toCharArray());
                 String passw=Security.toBase64(bytes);
                 Database.executeQuery("UPDATE USERS SET PASS_WORD=? ,CHANGE_PASS=? WHERE USER_NAME=?",database.getDatabaseName(),passw,"0",name);
                 object.put("response",true);
                 object.put("request_msg","redirect");
                 object.put("url","index.html");
                 response.getWriter().print(object);
            }
            else{
                object.put("response",false);  
                response.getWriter().print(object);
             }
        } catch (Exception ex) {
            Logger.getLogger(MainServlet.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
     
     
    @Override
     public void init(){
   
         try {
            String dbName=getServletConfig().getInitParameter("database-name");
            String uName=getServletConfig().getInitParameter("database-username");
            String pass=getServletConfig().getInitParameter("database-password");
            String host=getServletConfig().getInitParameter("database-host");
            Launcher.setDatabaseConnection(uName, host, pass);
            Database db=new Database(dbName);
            database=Database.getExistingDatabase(dbName);
            if(!Table.ifTableExists("USERS", db.getDatabaseName())){
                    Table users = db.addTable("USER_ID", "USERS", "VARCHAR(25)");
                    users.addColumns(new String[]{
                      "USER_NAME", "VARCHAR(256)",
                      "PASS_WORD","VARCHAR(512)",
                      "CHANGE_PASS",DataType.BOOL,
                      "CREATED",DataType.DATETIME,
                      "IS_ONLINE", DataType.BOOL
             }); 
              byte[] bytes=Security.makePasswordDigest("root","pass".toCharArray());
              String passw=Security.toBase64(bytes);
              database.doInsert("USERS", new String[]{"12345678","root",passw,"1","!NOW()","0"});
            
            }
            
           if(!Table.ifTableExists("CHAT_DATA", db.getDatabaseName())){      
               database.executeQuery("CREATE TABLE IF NOT "
                + "EXISTS CHAT_DATA ("
                + "RECORD_ID INT AUTO_INCREMENT PRIMARY KEY, " // test int(10) auto_increment primary key
                + "FROM_ID VARCHAR(30),"
                + "TO_ID VARCHAR(50),"
                + "MSG TEXT,"
                + "CREATED DATETIME)"
               );
            }
           
              if(!Table.ifTableExists("FRIEND_DATA", db.getDatabaseName())){      
               database.executeQuery("CREATE TABLE IF NOT "
                + "EXISTS FRIEND_DATA ("
                + "USER_ID VARCHAR(30), "
                + "FRIEND_ID VARCHAR(30),"
                + "CREATED DATETIME)"
               );
            }
         }
         catch(NonExistentDatabaseException | NoSuchAlgorithmException | NoSuchProviderException e){
            
         }
     }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /** 
     * Handles the HTTP <code>GET</code> method.
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /** 
     * Handles the HTTP <code>POST</code> method.
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /** 
     * Returns a short description of the servlet.
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

    private boolean isFriend(String friendID, String userID){
       JSONObject friend = database.queryDatabase("SELECT FRIEND_ID FROM FRIEND_DATA"
                    + " WHERE FRIEND_ID=? AND USER_ID=? OR (FRIEND_ID=? AND USER_ID=?)", new String[]{friendID,userID,userID,friendID});
            
       if(friend.optJSONArray("FRIEND_ID").length()!=2){  
              return false;
        } 
       return true;
    }

 protected void uploadFile(HttpServletRequest request, HttpServletResponse response,String[] currentUser)  throws ServletException, IOException {
     String fromName=currentUser[0];
    // Create path components to save the file
     Session ses=(Session) sessions.get(fromName);
     String userID=(String) ses.getAttribute("user_id");
     String toName=preTokens.get(fromName)[0];
     String message=preTokens.get(fromName)[2];
     String friendID=preTokens.get(fromName)[3];
     JSONObject object=new JSONObject();
     JSONObject details=new JSONObject();
     final PrintWriter writer = response.getWriter();
     if(!isFriend(friendID, userID)){
            try {
                details.put("success", "false");
                details.put("reason","you can only send files to friends who have you in their friend lists");
                object.put("response",details);
                writer.print(object);
                return;
            } catch (JSONException ex) {
                ex.printStackTrace();
            }
     }
            
     final String uploadDir=getServletConfig().getInitParameter("upload-dir");
     File file=new File(uploadDir);
     if(!file.exists()){
       file.mkdir();  
     }
 
    final String path =uploadDir;
    final Part filePart = request.getPart("file");
    final String fileName = getFileName(filePart);
    String fakeFileName=new UniqueRandom(15).nextMixedRandom();
    OutputStream out = null;
    InputStream filecontent = null;
    String fileExt=fileName.substring(fileName.lastIndexOf("."));
    final String savedFileName=fakeFileName+fileExt;
    fileNames.put(fakeFileName, fileName);
    try {
        out = new FileOutputStream(new File(path + File.separator
                + savedFileName));
        filecontent = filePart.getInputStream();
        int read = 0;
        final byte[] bytes = new byte[1024];

        while ((read = filecontent.read(bytes)) != -1) {
            out.write(bytes, 0, read);
        }
        
        // write url , from who, if image display it
        // url 
        UniqueRandom rand=new UniqueRandom(15);
        String token=rand.nextMixedRandom();
        details.put("success", "true");
        object.put("response",details);
        accessTokens.put(token,toName);
        preTokens.remove(fromName);
        storeMessage(toName, fromName,"@$%1289file"+fromName+"$%@127"+token+"$%@127"+message+"$%@127"+fileName+"$%@127"+fakeFileName);
        storeMessage(fromName,"server","@$%1289file"+fromName+"$%@127"+token+"$%@127"+message+"$%@127"+fileName+"$%@127"+fakeFileName);
        writer.print(object);
        java.util.Timer timer=new Timer();
        java.util.TimerTask task=new TimerTask() {
                @Override
                public void run() {
                    File file=new File(uploadDir+File.separator+savedFileName);
                    file.delete();
                }
            };
        timer.schedule(task,DELETE_FILE_DELAY); //delete after thirty minutes
        
    } catch (IOException | JSONException fne) {
            try {
                details.put("success", "false");
                details.put("reason","An error occurred when uploading the file ");
                object.put("response",details);
                writer.println(object);
                LOGGER.log(Level.SEVERE, "Problems during file upload. Error: {0}", 
                        new Object[]{fne.getMessage()});
            } catch (JSONException ex) {
                Logger.getLogger(MainServlet.class.getName()).log(Level.SEVERE, null, ex);
            }
    } finally {
        if (out != null) {
            out.close();
        }
        if (filecontent != null) {
            filecontent.close();
        }
        if (writer != null) {
            writer.close();
        }
    }
}
 
 private void shareFile(JSONObject obj, HttpServletResponse response,String msg,String [] currentUser){
   try {
          String fromName=currentUser[0];
          JSONArray toNames=obj.optJSONArray("to_names");
          JSONArray friendIDs=obj.optJSONArray("to_ids");
          Session ses=(Session) sessions.get(fromName);
          String fileName=obj.optString("file_name");
          String tokenID=obj.optString("token_id");
          String fakeName=obj.optString("fake_file_name");
          String userID=(String) ses.getAttribute("user_id");
          JSONObject object=new JSONObject();
          JSONObject details=new JSONObject();
          JSONArray notSent=new JSONArray();
          for(int x=0;x<toNames.length(); x++){ 
               if(!isFriend(friendIDs.getString(x), userID)){
                 notSent.put(toNames.getString(x));
               }
               else{
                 accessTokens.put(tokenID,toNames.getString(x));
                 storeMessage(toNames.getString(x), fromName,"@$%1289file"+fromName+"$%@127"+tokenID+"$%@127"+"  "+"$%@127"+fileName+"$%@127"+fakeName);
               }
          }
            details.put("success", "true");
            details.put("not_sent",notSent);
            object.put("request_msg", msg);
            object.put("response",details);
            response.getWriter().print(object);
           } catch (Exception ex) {
                ex.printStackTrace();
          }
 }
 
   private String getFileName(final Part part) {
    final String partHeader = part.getHeader("content-disposition");
    LOGGER.log(Level.INFO, "Part Header = {0}", partHeader);
    for (String content : part.getHeader("content-disposition").split(";")) {
        if (content.trim().startsWith("filename")) {
            return content.substring(
                    content.indexOf('=') + 1).trim().replace("\"", "");
        }
    }
    return null;
    }
   
 


}
