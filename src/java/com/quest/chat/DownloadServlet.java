/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.quest.chat;

import com.quest.access.common.mysql.Database;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author connie
 */
@WebServlet(name = "DownloadServlet", urlPatterns = {"/download"})
@MultipartConfig
public class DownloadServlet extends HttpServlet {

    /** 
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
 protected void processRequest(HttpServletRequest request, HttpServletResponse response)  throws ServletException, IOException {
     response.setContentType("text/html;charset=UTF-8");     
     String downloadFlag=request.getParameter("download_flag");
     String[] currentUser = ensureIntegrity(response, request);
      if(downloadFlag!=null && downloadFlag.equals("true")){
         downloadFile(request, response, currentUser);
         return;
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
 
 private String[] ensureIntegrity(HttpServletResponse response,HttpServletRequest request){
       try{
        String [] currentUser=getCurrentUser(request, response, true);
       //now check whether this is what we have on the server
        Session ses=(Session) MainServlet.sessions.get(currentUser[0]);
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
    
    
   
    
        
    private void doLogOut(String[] currentUser, HttpServletResponse response){
        try {
            MainServlet.sessions.remove(currentUser[0]);
            Database.setValue(MainServlet.database, "USERS", "IS_ONLINE", "0", "USER_NAME='"+currentUser[0]+"'");
            JSONObject object=new JSONObject();
            object.put("request_msg", "redirect");
            object.put("url","index.html");
            response.getWriter().print(object);
        } catch (JSONException | IOException ex) {
            Logger.getLogger(MainServlet.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    
  private void downloadFile(HttpServletRequest request, HttpServletResponse response, String [] currentUser){
     try {
      ServletContext context=getServletConfig().getServletContext();
      String accessToken = request.getParameter("access_token");
      String fileName=request.getParameter("file_name");
      String fakeName=request.getParameter("fake_file_name");
      String mimeType=context.getMimeType(fakeName);
      String userName = MainServlet.accessTokens.get(accessToken);
      /*
      if(userName==null || !userName.equals(currentUser[0])){ 
            System.out.println("no authority to download");
            response.getWriter().print("You are not authorized to download this file"); //only authorized people download
            return;
      }
       * 
       */
      
       String uploadDir=getServletConfig().getInitParameter("upload-dir"); 
       String fileExt=fileName.substring(fileName.lastIndexOf("."));
      
       File file = new File(uploadDir + File.separator+ fakeName+fileExt);
       response.setContentLength((int)file.length());
       if(!file.exists()){
          System.out.println("file does not exist");
          response.getWriter().print("file was deleted or moved");
          return;  
       }
       ServletOutputStream out = null;
       FileInputStream input = null;
      // DataInputStream in=null;
       try{
           response.setContentType((mimeType!=null) ? mimeType : "application/octet-stream");
           response.setHeader("Content-Disposition", "attachment;filename="+fileName+"");
          out =response.getOutputStream();
          input=new FileInputStream(file);
          WritableByteChannel outputChannel;
        try (ReadableByteChannel inputChannel = Channels.newChannel(input)) {
            outputChannel = Channels.newChannel(out);
            fastChannelCopy(inputChannel, outputChannel);
        }
        outputChannel.close();
       }
      catch(Exception e){
          response.getWriter().print("an error occurred while downloading the file");
          return;    
      }
   
       
       
    } catch (Exception ex) {
        Logger.getLogger(MainServlet.class.getName()).log(Level.SEVERE, null, ex);
    }
   }
  
  



public static void fastChannelCopy(final ReadableByteChannel src, final WritableByteChannel dest) throws IOException {
    final ByteBuffer buffer = ByteBuffer.allocateDirect(16 * 1024);
    while (src.read(buffer) != -1) {
      // prepare the buffer to be drained
      buffer.flip();
      // write to the channel, may block
      dest.write(buffer);
      // If partial transfer, shift remainder down
      // If buffer is empty, same as doing clear()
      buffer.compact();
    }
    // EOF will leave buffer in fill state
    buffer.flip();
    // make sure the buffer is fully drained.
    while (buffer.hasRemaining()) {
      dest.write(buffer);
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
}
