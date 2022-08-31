package edu.yu.cs.com3800;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public interface Client{
    //constructor must be commented out as per requirements
    //public ClientImpl(String hostName, int hostPort) throws MalformedURLException
    class Response {
        private int code;
        private String body;

        public Response(int code, String body) {
            this.code = code;
            this.body = body;
        }

        public int getCode() {
            return this.code;
        }

        public String getBody() {
            return this.body;
        }
    }

    void sendCompileAndRunRequest(String inputString) throws IOException;

    Response getResponse() throws IOException;
}