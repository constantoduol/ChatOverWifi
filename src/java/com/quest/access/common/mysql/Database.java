
package com.quest.access.common.mysql;

/**
 *
 * @author constant oduol
 * @version 1.0(4/1/2012)
 */


import java.sql.*;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This file has implementations of mysql database connectivity methods
 * a database object represents a database in the mysql server connection
 * to the database needs to be set up by specifying the mysql user account
 * we want to connect with. This is done in the setDefaultConnection() method
 * @see Database#setDefaultConnection(java.lang.String, java.lang.String, java.lang.String) 
 * if the specified mysql account does not exist or if any of the connection details are null
 * a NoDefaultAccountException is thrown.Each database object created is assigned a unique id
 * starting from 1,2,3 and so on
 * 
 * 
 * 
 */

public class Database implements java.io.Serializable{
    /*
     * the password to the default root account
     */
    private static String defaultPass;
    /*
     * the primary root username
     * this username is used for creating the very first connection to the database
     */
    private static String defaultUserName;
    /*
     * the default url to the root account eg. localhost
     */
    private static String defaultUrl;
   
    /*
     * the name of the database
     */
    private String name;
    
    /*
     * a static connection object to ensure a persistent connection
     */
    private static Connection connection;  
    
    /*
     * to count the number of intialized databases
     */
    private static int dbCount;
    /*
     * a unique identifier of a database
     */
    private int dbId;
    
   
    
    /**
     *  this method constructs a database in the mysql server specified by the name
     * if the database exists it is not recreated. the following sql code is normally executed
     * <code>CREATE DATABASE IF NOT EXISTS NAME</code>
     * @param name - the name of the database we want to create
     */
    public Database(String name){
       Database.executeQuery("CREATE DATABASE IF NOT EXISTS "+name+"","");
       this.name=name;
       dbCount++;
       this.dbId=dbCount;
    }
    
    /**
     * this constructor privately creates database objects without trying to
     * recreate them in the mysql server, it is used in the getExistingDatabase()
     * method to get instances of existing databases
     */
    private Database(String name, int id){
        this.name=name;
        this.dbId=id;
    }
    
    /**
     * this method returns the name of the current database
     * 
     */
    
    public String getDatabaseName(){
        return this.name;
    }
    
    /**
     * this method returns the unique id of the database
     */
    public int getDatabaseId(){
        return this.dbId;
    }
    
    
    
    /**
     * this method checks whether the specified database exists, this is done by checking in
     * the information_schema database, the following sql code is executed 
     * <code>
     * USE information_schema;
     * SELECT SCHEMA_NAME FROM SCHEMATA WHERE SCHEMA_NAME=name
     * </code>
     * @param name- the name of the database we want to tell whether it exists
     * 
     */
    public static boolean ifDatabaseExists(String name){
           ResultSet set = Database.executeQuery("SELECT SCHEMA_NAME FROM SCHEMATA WHERE SCHEMA_NAME=? ", "information_schema",name);
           try {
                while(set.next()){
               // check whether the table exists in the tables table
                 if(set.getString("SCHEMA_NAME")!=null ){
                    return true;
                }
              }
                //close the result set
              set.close();
           return false;
         }
        catch(SQLException e){
            return false;
        }
        
    }
    
    /**
     * this method checks whether the specified database exists
     * @param db the database we want to tell if it exists
     * @see #ifDatabaseExists(java.lang.String) 
     */
    public static boolean ifDatabaseExists(Database db){
        return ifDatabaseExists(db.getDatabaseName());
    }
    
    /**
     * this method gets an instance of an existing database table
     * if the specified table does not exist a NonExistentTableException
     * is thrown and should be handled by the code calling this method
     * @param name the name of the table to get an instance of
     * @throws NonExistentTableException
     */
    public Table getTable(String name) throws NonExistentTableException{
        return Table.getExistingTable(name, this.name);
    }
   
    /**
     * this method returns an arraylist of all the names of the databases on the mysql server 
     * the following sql code is executed 
     * <code>
     * USE information_schema;
     * SELECT schema_name FROM schemata;
     * </code>
     */
    public static ArrayList getDatabaseNames(){
         ArrayList name=new ArrayList();
        ResultSet set = Database.executeQuery("SELECT schema_name FROM schemata", "information_schema");
         try{
          for(;set.next();){
              name.add(set.getString("schema_name")); 
              }
              return name;
         }
         catch(SQLException e){
            // Logger.toConsole(e,Database.class);
             return name;
         }
    }
    

    
    /**
     * this method drops the current database if the current mysql user has the 'DROP DATABASE' privilege
     * @see #dropDatabase(java.lang.String) 
     */
    public void dropDatabase(){
        dropDatabase(this.name);
     }
    
    
    /**
     * this method drops the database with the specified name
     * the database is only dropped if the current user has the DROP privilege
     * on the specified database
     * @param name the name of the database
     */
    public static void dropDatabase(String name){
       Database.executeQuery("DROP DATABASE "+name+"", "");
    }
    
    /**
     * this method sets the connection used to connect to the database and the actions that can 
     * be executed on the database such as UPDATE, SELECT, DELETE etc depend on the level of privilege
     * the specified user has, if any of the values are null an exception is thrown
     * @param name the username of the user who wants to connect
     * @param host the host name the user is allowed to connect from eg. localhost, 192.168.1.67
     * @param pass the password of the user
     */
    public static void setDefaultConnection(String name, String host, String pass) throws NoDefaultAccountException{
        if(host==null || name==null || pass==null){
            throw new NoDefaultAccountException();
            }
         else{
         defaultUserName=name;
         defaultUrl=host;
         defaultPass=pass;
        }
    }
    
    
    
    /**
     * returns a jdbc url from a normal url such as localhost ,127.0.0.1 etc.
     * @param normalUrl the url we want to convert to a jdbc url
     */
    private static String getJDBCUrl(String normalUrl){
        return "jdbc:mysql://"+normalUrl+":3306/";
    }
    
    /**
     * this method is used to execute a query on the database
     * depending on the executed statement a result set containing results from the database is returned
     * UPDATE, INSERT do not return any result set, SELECT returns a result set with the required data
     * It is important that once a result set object is used it is closed
     * @param sql - this is the sql statement that is executed on the database
     * @param dbName - the database we are executing a query on
     * @see #executeQuery(java.lang.String) 
     * @see #executeQuery(java.lang.String, java.lang.String, java.lang.String[]) 
     */
    public static ResultSet executeQuery(String sql, String dbName){
      Statement statement;    
         try{   
             createConnection(dbName,defaultUrl,defaultUserName,defaultPass);
                statement = connection.createStatement();
                  statement.execute(sql);
                   return statement.getResultSet();
              }
            catch(SQLException e){
          
                 throw new RuntimeException(e);
           } 
        }
    
    
    /**
     * this method executes a query on the current database
     * @param sql the query to be executed on the current database
     * @see #executeQuery(java.lang.String, java.lang.String) 
     * @see #executeQuery(java.lang.String, java.lang.String, java.lang.String[]) 
     */
    public ResultSet executeQuery(String sql){
        return executeQuery(sql,this.name);
        
    }
    
    /**
     * this method sets a value in a given column depending on a specific condition
     * the sql query that is made is<code>UPDATE tableName SET columnName=value WHERE condition</code>
     * if the condition matches more than one row then all the rows have the specified values updated to
     * the new value
     * @param serv the server we want to connect to 
     * @param tableName the name of the table we want to set a value
     * @param columnName the name of the column where a value is to be set
     * @param value
     * @param condition 
     */
   public static void setValue(Database db,String tableName, String columnName, String value, String condition){
       
        db.executeQuery("UPDATE "+tableName+" SET "+columnName+"='"+value+"' WHERE "+condition+"");
    }
    
    /**
     * this method creates a connection to the database if it does not exist
     * @param dbName the name of the database we want to create a connection to
     */
    private static void createConnection(String dbName, String host, String userName, String pass){
       try{
            Class.forName("com.mysql.jdbc.Driver").newInstance();
             connection = DriverManager.getConnection(getJDBCUrl(host)+dbName,userName,pass);
           }
         catch(Exception e){
           throw new RuntimeException(e);
       }
    }
    
    /**
     * this method executes prepared statements with strings as parameters
     * @param psql this is the sql statement to be executed as a prepared statement
     *             eg. INSERT INTO table1 (?) VALUES(?), CREATE TABLE ? (col1 INT)
     * @param dbName the name of the database to execute an sql statement
     * @param params these are the parameters that are replaced in the prepared statement
     *         eg a statement such as INSERT INTO table1 (?) VALUES(?) the first value in params
     *         replaces the first ? the second value replaces the second ? the number of values 
     *         under params determines how many times the method pstatement.setString() is called
     * @return the value returned is a ResultSet or null, executing a SELECT query returns a ResultSet
     *         while executing an UPDATE, INSERT or DELETE returns null
     * @see #executeQuery(java.lang.String) 
     * @see #executeQuery(java.lang.String, java.lang.String) 
     */
    
    public static ResultSet executeQuery(String psql, String dbName, String ... params){
       PreparedStatement pstatement;
        ResultSet set=null;
        try{
           createConnection(dbName,defaultUrl,defaultUserName,defaultPass);
             pstatement=connection.prepareStatement(psql);
                for(int x=0; x<params.length; x++){
                   pstatement.setString(x+1, params[x]);
                }
                //check to see if it is a select statement
                  if(psql.toUpperCase().startsWith("SELECT")){
                       set=pstatement.executeQuery();
                      }
                     else{
                       pstatement.executeUpdate();
                  }
               return set;
              
          }
        catch(Exception e){
            throw new RuntimeException(e);
        }
       
    }
    
    
    /**
     * this method returns an instance of an existing database if the specified
     * database does not exist an NonExistentDatabaseException is thrown
     * @param name the name of the database we want to get an instance of
     * @throws NonExistentDatabaseException
     */
    
    public static Database getExistingDatabase(String name) throws NonExistentDatabaseException{
        //check to see that the database exists
        if(ifDatabaseExists(name)){
             dbCount++;
             return new Database(name,dbCount);
         }
        else{
            throw new NonExistentDatabaseException();
        }
    }
    
    
    /**
     * this method checks to see if the specified value exists in the specified column 
     * @param value the value we want to ascertain its existence
     * @param tableName the table where the specified column is found
     * @param columnName the name of the column where the value is checked in
     * @param db the database to check for the value
     * @return true or false depending on whether the specified value exists or not
     */
    public static boolean ifValueExists(String value, String tableName, String columnName, Database db){
       return Column.ifValueExists(columnName, tableName,db.getDatabaseName(), value);
    }
    
    /**
     * this method checks to see if the specified value exists in the specified column 
     * @param value the value we want to ascertain its existence
     * @param tableName the table where the specified column is found
     * @param columnName the name of the column where the value is checked in
     * @return true or false depending on whether the specified value exists or not
     */
    public boolean ifValueExists(String value, String tableName, String columnName){
        return ifValueExists(value, tableName, columnName, this);
    }
    
    /**
     * this method gets a specific value from the database where a given condition is true
     * @param toFind this is the value we want to find from the database and is usually a database column
     * @param table this is the table where the specific value is found
     * @param key this is the value that is used as a key to retrieve what we are looking for
     * @param keyValue this is the value that matches what we want to find
     * @param serv this is the server where we want the value found
     * @return returns the value that has been found as per the specified condition
     *         or an empty string if no value is found
     */
    public static String getValue(String toFind, String table, String key, String keyValue, Database db){
      String val="";
        ResultSet rs = db.executeQuery("SELECT "+toFind+" FROM "+table+" WHERE "+key+"='"+keyValue+"'");
         try{
           while(rs.next()){
               val = rs.getString(""+toFind+"");
                return val;
           } 
            rs.close();
           return val;
         }
         catch(SQLException e){
             return val;
         }    
    }
    
    
    /**
     * this method returns an arraylist containing the names of the tables in the current database
     * @see #getTableNames(java.lang.String) 
     */
    
    public ArrayList getTableNames(){
        return getTableNames(this.name);
    }
    
    /**
     * this method returns the names of tables in the database specified by the name
     * @param name the name of the database we want to get the table names
     * @see #getTableNames() 
     */
    public static ArrayList getTableNames(String name){
      ArrayList names=new ArrayList();
        ResultSet set = Database.executeQuery("SELECT table_schema, table_name FROM TABLES WHERE table_schema=?", "information_schema",name); 
        try{
           for(;set.next();){
              names.add(set.getString("table_name")); 
             
           }
           return names;
         }
         catch(SQLException e){
             return names;
         }
    }
    
    /**
     * this method adds the specified table to the current database
     * @param tableName the name of the table to be added
     * @param firstColumnName the name of the first column in this table to be created
     * @param firstColumnDataType the data type of the first column eg. INT, VARCHAR
     */
    
    public Table addTable(String firstColumnName,String tableName, String firstColumnDataType){
        return new Table(firstColumnName, tableName, this.name, firstColumnDataType);
    }
    
    /**
     * two database objects  are equal if they have the same name
     */
    
    @Override
    public boolean equals(Object obj){
        if(obj instanceof Database ){
            Database db=(Database)obj;
             return db.getDatabaseName().equals(this.name);
         }
        return false;
    }
   
    /**
     * unique hash code for each object
     */

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 29 * hash + (this.name != null ? this.name.hashCode() : 0);
        return hash;
    }
    
    /**
     * returns a string representation of a database object
     * in this format Database[Name : dbId]
     */
    @Override
    public String toString(){
        return "Database["+this.name+" : "+this.dbId+"]";
    }

        
    /**
     * this method is used to insert data into the database
     * the query executed is  INSERT INTO table (values), it uses the ! mark as
     * an escape character for values that should not have 'value' and should be passed as they are
     * @param dbName the name of the database to insert into
     * @param table the name of the table the data is inserted into
     * @param values  the values to insert into the database
     */
    public static void doInsert(String dbName,String table, String[] values){
       StringBuilder builder=new StringBuilder("INSERT INTO ").append(table).append(" ").append("VALUES");
       ArrayList<String> ps=new ArrayList();
       builder.append("("); 
       for(int x=0; x<values.length-1; x++){
         if(values[x].startsWith("!")){
            builder.append(values[x].substring(1)).append(" ,");
            
         }
         else{
            builder.append(" ? ").append(" ,");  
            ps.add(values[x]);
          }
        }
         if(values[values.length-1].startsWith("!")){
            builder.append(values[values.length-1].substring(1)).append(" )");  
         }
         else{
            builder.append(" ? ").append(")");
            ps.add(values[values.length-1]);
         }
        String sql= builder.toString();
        String[] vals=new String[ps.size()];
        for(int x=0; x<ps.size(); x++){
          vals[x]=ps.get(x); 
        }
        executeQuery(sql, dbName,vals);
      
    }
    
    /**
     * @see Database#doInsert(java.lang.String, java.lang.String, java.lang.String[]) 
     */
    public void doInsert(String table, String[] values){
       doInsert(name, table, values);
    }
    
    
    
    /**
     * this method is used to carry out a database update on the specified database
     * the various components of the update statements are split into
     * UPDATE tableNames SET values WHERE conditions
     * @param dbName the name of the database to update
     * @param tableNames the table names that are to be updated
     * @param values the values that are to be updated in case the specified conditions are true
     * @param conditions the conditions to be met in order for the data to be updated
     */
    public static void doUpdate(String dbName,String [] tableNames,String[] values, String [] conditions){
          StringBuilder builder=new StringBuilder("UPDATE");
          for(int x=0; x<tableNames.length-1; x++){
            builder.append(" ").append(tableNames[x]).append(" ,");   
          }
          builder.append(" ").append(tableNames[tableNames.length-1]).append(" ");
          builder.append("SET "); 
          if(values.length>0){
            for(int x=0; x<values.length-1; x++){
              builder.append(" ").append(values[x]).append(" AND");   
            }
             builder.append(" ").append(values[values.length-1]); 
           }
          
          if(conditions.length>0){
             builder.append(" WHERE");
             for(int x=0; x<conditions.length-1; x++){
              builder.append(" ").append(conditions[x]).append(" AND");   
             }
            builder.append(" ").append(conditions[conditions.length-1]); 
           }
           String sql= builder.toString();
           executeQuery(sql, dbName);
    }
    
    /**
     * @see Database#doUpdate(java.lang.String, java.lang.String[], java.lang.String[], java.lang.String[]) 
     */
    public void doUpdate(String [] tableNames,String[] values, String [] conditions){
        doUpdate(name, tableNames, values, conditions);
    }
    
    
    
    /**
     * this method is used to carry out a select which returns a json object with
     * the data
     * @param sql the sql statement to run
     * @param params the parameters for the prepared statement
     * @return json object with the data
     */
    public JSONObject queryDatabase(String sql,String [] params){   
        try {
            ResultSet set=null;
            if(params==null){
              set = Database.executeQuery(sql,this.name);  
            }
            else{
              set = Database.executeQuery(sql,this.name,params);    
            }
            String [] labels=new String[set.getMetaData().getColumnCount()+1];
            JSONObject json=new JSONObject();
            for(int x=1; x<labels.length; x++){
               labels[x] = set.getMetaData().getColumnLabel(x); 
                try {
                    json.put(labels[x],new JSONArray());
                } catch (JSONException ex) {

                }
              }
               while(set.next()){
                 for(int x=1; x<labels.length; x++){
                 String value = set.getString(x);
                 ((JSONArray)json.get(labels[x])).put(value);
               }  
           }
          
           return json;
        } catch (Exception ex) {
           System.out.println(ex);
            return  null;
        }
    }
     
      public JSONObject queryDatabase(String sql){   
          return queryDatabase(sql,null);
      }
}