package edu.yu.cs.com3800.stage1;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ClientImpl implements Client {
    private final HttpClient httpClient;
    private final String hostName;
    private final int hostPort;
    private Response response;

    public ClientImpl(String hostName, int hostPort) throws MalformedURLException{
        httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.hostName = hostName;
        this.hostPort = hostPort;


    }


    public void sendCompileAndRunRequest(String src) throws IOException{
        try{ if (src == null) throw new IllegalArgumentException("Code must not be null"); }
        catch(IllegalArgumentException e){
            e.printStackTrace();
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + hostName + ":" + hostPort + "/compileandrun"))
                .setHeader("Content-Type", "text/x-java-source")
                .POST(HttpRequest.BodyPublishers.ofString(src))
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            this.response = new Response(response.statusCode(),response.body());
        } catch (InterruptedException e) {
            e.printStackTrace();
            System.out.println("CLient's Connection Interrupted");

        }


        //HTTPREQUEST.GETREQUESTBODY() WHEN CALLED ON THE SERVER SHOULD RETURN THE STRING SRC
    }
    public Response getResponse() throws IOException{
        return this.response;
    }

     void sendCompileAndRunRequestBadHeader(String src) throws IOException {
         try {
             if (src == null) throw new IllegalArgumentException("Code must not be null");
         } catch (IllegalArgumentException e) {
             e.printStackTrace();
         }

         HttpRequest request = HttpRequest.newBuilder()
                 .uri(URI.create("http://" + hostName + ":" + hostPort + "/compileandrun"))
                 .setHeader("Content-Type", "bad header")
                 .POST(HttpRequest.BodyPublishers.ofString(src))
                 .version(HttpClient.Version.HTTP_1_1)
                 .build();

         try {
             HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
             this.response = new Response(response.statusCode(), response.body());
         } catch (InterruptedException e) {
             e.printStackTrace();
             System.out.println("CLient's Connection Interrupted");

         }
     }
}