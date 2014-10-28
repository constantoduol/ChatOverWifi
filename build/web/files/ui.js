var dom={
    /**
     *
     * returns an element with the specified id
     */
   el : function(id){
       var element=document.getElementById(id);
       element.attr=function(name,value){
          this.setAttribute(name,value); 
       }
       return element
   },
   
   /**
    *creates a new element with the specified tag name
    */
   newEl : function (tag){
     var element=document.createElement(tag);
      element.attr=function(name,value){
          this.setAttribute(name,value); 
       }
      return element;
   },
   
   /**
    *
    * checks whether the document has completely loaded
    */
   ready : function (){
     if(document.readyState=="complete"){
        return true; 
      }
     else{
       return false;
     }
   },
   /**
    * 
    * returns an array of elements with the specified tag name
    *
    */
   tags : function(name){
      return document.getElementsByTagName(name);   
   },
   
   /**
    *waits till the document is ready and then executes the function func
    */
   waitTillReady : function(func){
      var time=setInterval(function(){
          if(dom.ready()){
            clearInterval(time); 
            func();
          }  
      },5); 
      
   }
   

}

var funcs={} // namespace for functions



var ui={
  /*
  *  ui appends the input
  *
  * value
  * onclick
  * class
  * other attributes
  * 
  */
  element: function(attr){
     var run=function(id){
         var com=dom.newEl(attr.tag);
	 com.attr("id",id);
         delete attr.tag;
         for(var param in attr){
           if(typeof attr[param]=="function"){
             var func=attr[param];
             var str=func.toString();
             var funcBody=str.substring(str.indexOf("("));
             var funcName="function_"+Math.floor(Math.random()*100000000)+" ";
             var newFunc="funcs."+funcName+"= function "+funcName+funcBody;
             eval(newFunc);
             com.attr(param,"funcs."+funcName+"()"); 
           }
	  else if(param=="klass"){
	      com.attr("class",attr[param]);
	   }
          else if(param=="content"){
	      com.innerHTML=attr[param];
	   }
          else{
            com.attr(param,attr[param]);
          }
        }
        if(attr.parent){
          //append this element to the specified parent
            if( typeof attr.parent=="string"){
		 dom.el(attr.parent).appendChild(com);
	      }
	    else {
	         attr.parent.appendChild(com);
	     }
          delete attr.parent;
  	 }
       else{
        dom.tags("body")[0].appendChild(com);
       }	
      return com;	  
     }
	 
       function wait(func,id){
	  var time=setInterval(function(){
          if(dom.ready()){
            clearInterval(time); 
            func(id);
          }  
        },5);
     }
     if(!attr.id){
            // bind this id if none is specified
       attr.id="element_"+Math.floor(Math.random()*1000000);
     } 
     wait(run,attr.id); // run this function only after the document is ready
     return attr.id;
 },
 
 /**
  * this defines various layouts 
  *
  */
 layout : {
     /*
      *@param el the root element to attach the layout
      *@param layoutManager the object containing details on the layout
      *@param func the function that knows how to interpret the layout
      *
      */
     init : function (el,layoutManager,func){
       var run=function(){
        if(func!=null){
          func(el,layoutManager);
        }
        else{
          var mainDiv=dom.newEl("div"); 
          for(var param in layoutManager){
            if(param=="menuBar"){
               var menuDiv=dom.newEl("div"); 
               for(var menuAttr in layoutManager[param]){
                 menuDiv.style[menuAttr]=layoutManager[param][menuAttr];
               }
              mainDiv.appendChild(menuDiv);
            }
            else if(param=="divs"){
               for(var singleDiv in layoutManager[param]){
                  var bodyDiv=dom.newEl("div");
                  for(var divParam in layoutManager[param][singleDiv]){
                     bodyDiv.style[divParam]=layoutManager[param][singleDiv][divParam];  
                  }
                  mainDiv.appendChild(bodyDiv);
               } 
            }
            else{
              mainDiv.style[param]=layoutManager[param];
            }
          }
        }
        var toAppend;
        if(el==""){
          toAppend=dom.tags("body")[0]; 
        }
        else if(typeof el=="string"){
           toAppend=dom.el(el);
        }
        toAppend.appendChild(mainDiv);
      }
     dom.waitTillReady(run);
   }
 },
 
 
 // this stores layout managers
 layoutManager : {
     basicLayout : {
        marginBottom : window.screen.availHeight*0.05+"px",
        marginLeft : window.screen.availWidth*0.05+"px",
        marginRight : window.screen.availWidth*0.05+"px",
        paddingLeft : "10px",
        paddingRight : "10px",
        paddingBottom : "10px",
        borderColor : "#ccffcc",
        borderRadius : "2px",
        width : window.screen.availWidth*0.9+"px",
        height : window.screen.availHeight*0.9+"px",
        background : "#ffff",
        menuBar : {
          width : window.screen.availWidth*0.9+"px",
          height : "30px",
          background : "lightgray"
        },
        divs : {
          first : {
             paddingTop : "10px",
             paddingLeft : "10px",
             paddingRight : "10px",
             paddingBottom : "10px",
             borderColor : "#ccffcc",
             borderRadius : "2px",
             width : window.screen.availWidth*0.3+"px",
             height : window.screen.availHeight*0.9+"px",
             background : "lightgreen",
             "float" : "left"
          },
          second : {
             paddingTop : "10px",
             paddingLeft : "10px",
             paddingRight : "10px",
             paddingBottom : "10px",
             borderColor : "#ccffcc",
             borderRadius : "2px",
             width : window.screen.availWidth*0.6+"px",
             height : window.screen.availHeight*0.9+"px",
             background : "lightgreen",
             "float" : "right"
          }
        }
     } 
 }
 
 

 
 
 
 
 
 
 
}







var Ajax={
    /**
     *@param data the data to be pushed to the server
     */
    run:function(data){  
       /*
        * url
        * loadArea
        * data
        * type
        * success
        * error
        */
   
       if(data.loadArea){
           //show that this page is loading
          dom.el(data.loadArea).style.display="block"; 
       }
      function callback(xhr){
        return function(){
         if (xhr.readyState == 4) {
           if (xhr.status == 200) {
             var resp=xhr.responseText;
             var cType=xhr.getResponseHeader("Content-Type");
             if(cType=="application/octet-stream"){
                 console.log(xhr.getAllResponseHeaders());
                dom.el(data.loadArea).style.display="none";
                return;
             }
            
             var json=JSON.parse(resp);
              if(json.request_msg=="redirect"){
                 window.location=json.url;   
               }
             if(data.success!=null){
                   if(data.loadArea){
                      dom.el(data.loadArea).style.display="none";
                   }
                   data.success(json);    
               }

           } else {
             if(data.error!=null){
                data.error(data);
             }  
           }
          }
        }
      }
        
      return function(){
           var xhr=getRequestObject();
           if(data.error!=null){
               if(xhr.onerror){
                 xhr.onerror=data.error;  
               }
           }
           sendJSON(data.url,data.data,xhr,callback(xhr),data.type);
     }();
     
    }
 
   
    
};










/*
 * this function returns an xml http object
 */

function getRequestObject(){
    if(window.ActiveXObject){
      return new ActiveXObject("Microsoft.XMLHTTP");  
    }
    else if(window.XMLHttpRequest){
       return new  XMLHttpRequest();
    }
    else{
       return null; 
    }
    
}






/**
 * used to send json data to the server
 * @param serverUrl the url to the server where the data is to be sent
 * @param json this is the json data to be sent to the server
 * @param request this is the xmlhttp request object
 * @param callback this is the callback function
 * @param type post or get
 */
function sendJSON(serverUrl,json,request,callback,type){
       json="json="+JSON.stringify(json);
       request.onreadystatechange=callback
       request.open(type, serverUrl, true);
       request.setRequestHeader("Content-Type","application/x-www-form-urlencoded");
       request.send(json);   
}


function addOption(selectbox,text,value){
   var optn=document.createElement("OPTION")
   optn.innerText=text;
   optn.text=text;
   optn.value=value;
   var box=document.getElementById(selectbox)
   box.appendChild(optn);
}
//populate a select box from an array
function populateSelect(selectbox,arrayoptions,arrayvalues){
    if(arrayvalues==null || arrayvalues=="undefined"){
      return;  
    }
 for(var y=0; y<arrayvalues.length; ++y){
  addOption(selectbox,arrayoptions[y],arrayvalues[y])
}

}



function depopulateSelect(selectbox){
   var select=document.getElementById(selectbox);
   while (select.firstChild) {
       select.removeChild(select.firstChild);
     }
    
}



  window.cookieStorage = {
    getItem: function (sKey) {
      if (!sKey || !this.hasOwnProperty(sKey)) { return null; }
      return unescape(document.cookie.replace(new RegExp("(?:^|.*;\\s*)" + escape(sKey).replace(/[\-\.\+\*]/g, "\\$&") + "\\s*\\=\\s*((?:[^;](?!;))*[^;]?).*"), "$1"));
    },
    key: function (nKeyId) {
      return unescape(document.cookie.replace(/\s*\=(?:.(?!;))*$/, "").split(/\s*\=(?:[^;](?!;))*[^;]?;\s*/)[nKeyId]);
    },
    setItem: function (sKey, sValue) {
      if(!sKey) { return; }
      document.cookie = escape(sKey) + "=" + escape(sValue) + "; expires=Tue, 19 Jan 2038 03:14:07 GMT; path=/";
      this.length = document.cookie.match(/\=/g).length;
    },
    length: 0,
    removeItem: function (sKey) {
      if (!sKey || !this.hasOwnProperty(sKey)) { return; }
      document.cookie = escape(sKey) + "=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/";
      this.length--;
    },
    hasOwnProperty: function (sKey) {
      return (new RegExp("(?:^|;\\s*)" + escape(sKey).replace(/[\-\.\+\*]/g, "\\$&") + "\\s*\\=")).test(document.cookie);
    }
  };
  window.localStorage.length = (document.cookie.match(/\=/g) || window.localStorage).length;