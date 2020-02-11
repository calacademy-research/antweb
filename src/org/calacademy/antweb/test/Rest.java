package org.calacademy.antweb.test;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;

//import io.restassured.RestAssured.*;
//import io.restassured.matcher.RestAssuredMatchers.*;
//import org.hamcrest.Matchers.*;
//import io.restassured.module.jsv.JsonSchemaValidator.*;

public class Rest {

  private static Log s_log = LogFactory.getLog(Rest.class);

  public static void main(String[] args) { 
   // To execute:    ant restTests   
    Rest.restTests();
  }

  //@Test
  private static void restTests() {
    s_log.warn("restTests");
    
    System.out.println("restTests()");
   /* 
    given().when().get("http://www.google.com").then().statusCode(200);
    
    when().get("http://api.antweb.org/v3.1/specimens?specimenCode=casent0922626");
    then().
        body("count", equalTo(1));
    */
  }

}
