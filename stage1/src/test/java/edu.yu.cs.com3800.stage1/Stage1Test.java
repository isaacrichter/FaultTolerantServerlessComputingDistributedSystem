package edu.yu.cs.com3800.stage1;

import static org.junit.Assert.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.*;
import java.net.MalformedURLException;
import java.util.Date;

/**
 * The test class TestRouter.
 */

public class Stage1Test {


    String jc1 = "public class Simple{   public Simple(){} public String run(){return (\"Hello to a working client server connection. Woohoo!!!\");}}";
    String r1 = "Hello to a working client server connection. Woohoo!!!";
    String jc2 = "public class Simple{   public Simple(){} public String run(){return (\"Hello to another working \" + \"client server connection. Woohoo!!!\");}}";
    String r2 = "Hello to another working client server connection. Woohoo!!!";
    String badJavaCode = "as far as i know this should not compile... Right?";

    //empty constructor
    public Stage1Test() {
    }
    /**
     * TO-DO:
     * 1) OutputStream to string to log javarunner response
     * */

    @Before
    public void setup(){
        System.out.println("Log file located under stage1/logFiles");
    }

    @Test
    public void simpleTwoRequestTest() throws IOException {
        SimpleServerImpl server = new SimpleServerImpl(9000);
        server.start();

        ClientImpl client = new ClientImpl("localhost",9000);

        client.sendCompileAndRunRequest(jc1);
        Client.Response response = client.getResponse();

        System.out.println("Expected response: \n" + response.getCode() + ", " + response.getBody());
        System.out.println("Actual response: \n" + "200" + ", " + r1 + "\n");

        client.sendCompileAndRunRequest(jc2);
        Client.Response response2 = client.getResponse();

        System.out.println("Expected response: \n" + response2.getCode() + ", " + response2.getBody());
        System.out.println("Actual response: \n" + "200" + ", " + r2 + "\n");

        server.stop();

        assertEquals(r2,response2.getBody());
        assertEquals(r1,response.getBody());

    }

    @Test
    public void badJavaCodeTest() throws IOException {
        SimpleServerImpl server = new SimpleServerImpl(9000);
        server.start();

        ClientImpl client = new ClientImpl("localhost",9000);

        client.sendCompileAndRunRequest(badJavaCode);
        Client.Response response = client.getResponse();

        System.out.println("Expected response: \n" + response.getCode() + ", " + response.getBody());
        System.out.println("Actual response: \n" + "400" + ", " + "\n");

        server.stop();

    }

    @Test
    public void badHeader() throws IOException {
        System.out.println(new Date(System.currentTimeMillis()));
        SimpleServerImpl server = new SimpleServerImpl(9000);
        server.start();

        ClientImpl client = new ClientImpl("localhost",9000);

        client.sendCompileAndRunRequestBadHeader(jc1);
        Client.Response response = client.getResponse();

        System.out.println("Expected response: \n" + response.getCode() + ", " + response.getBody());
        System.out.println("Actual response: \n" + "400" + ", " + "\n");

        server.stop();
        assertEquals("",response.getBody());
    }


    @Test
    public void twoClientTest() throws IOException {
        SimpleServerImpl server = new SimpleServerImpl(9000);
        server.start();

        ClientImpl client = new ClientImpl("localhost",9000);

        client.sendCompileAndRunRequest(jc1);
        Client.Response response = client.getResponse();

        System.out.println("Expected response: \n" + response.getCode() + ", " + response.getBody());
        System.out.println("Actual response: \n" + "200" + ", " + r1 + "\n");


        ClientImpl client2 = new ClientImpl("localhost",9000);
        client2.sendCompileAndRunRequest(jc2);
        Client.Response response2 = client2.getResponse();

        System.out.println("Expected response: \n" + response2.getCode() + ", " + response2.getBody());
        System.out.println("Actual response: \n" + "200" + ", " + r2 + "\n");

        server.stop();

        assertEquals(r1,response.getBody());
        assertEquals(r2,response2.getBody());
    }



}