package edu.yu.cs.com3800;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class ClientImpl implements Client{
    private final HttpClient httpClient;
    private final String hostName;
    private final int hostPort;
    private Response response;
    private CompletableFuture<HttpResponse<String>> completableResponse;

    public ClientImpl(String hostName, int hostPort) throws MalformedURLException{
        httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.hostName = hostName;
        this.hostPort = hostPort;


    }


    //run
    public void sendCompileAndRunRequest(String inputString) {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + hostName + ":" + hostPort + "/compileandrun"))
                .setHeader("Content-Type", "text/x-java-source")
                .POST(HttpRequest.BodyPublishers.ofString(inputString))
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        try {
            //HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            completableResponse = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());


            //this.response = new Response(response.statusCode(),response.body());
        } catch (Exception e) {
            e.printStackTrace();
        }


        //HTTPREQUEST.GETREQUESTBODY() WHEN CALLED ON THE SERVER SHOULD RETURN THE STRING SRC
    }

    public Response getResponse() throws IOException{
        HttpResponse<String> completedResponse = null;
        try {
            completedResponse = completableResponse.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        this.response = new Response(completedResponse.statusCode(),completedResponse.body());
        return this.response;
    }

}