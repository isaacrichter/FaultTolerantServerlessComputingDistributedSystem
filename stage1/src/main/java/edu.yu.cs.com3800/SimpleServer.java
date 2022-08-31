package edu.yu.cs.com3800;

public interface SimpleServer {
    //constructor must be commented out as per requirements
    //public SimpleServerImpl(int port) throws IOException

    /**
     * start the server
     */
    void start();

    /**
     * stop the server
     */
    void stop();
}