package edu.yu.cs.com3800.stage4;
import edu.yu.cs.com3800.ServerCluster;
import org.junit.Before;
import org.junit.Test;

public class ServerClusterTest {

    final int httpServerPort = 8888;

    public ServerClusterTest(){
        System.out.println("\n Quick Tip:  Log files are organized by class. If two tests are run, their log files will end up next to each other in each class folder \n");
    }


    @Test
    public void runServerCluster2Requests3Servers() throws Exception {
        int requests = 2; //set to any number for testing
        int numWorkerLeaderServers = 3; // servers that can be workers or leaders i.e. non-observers
        int startPortForWorkerLeaders = 1000;
        ServerCluster newCluster = new ServerCluster(numWorkerLeaderServers, startPortForWorkerLeaders, httpServerPort, requests);
        newCluster.buildAndRun();
        Thread.sleep(1000);
        System.out.println("Test complete");

    }


    @Test
    public void runServerCluster5Requests3Servers() throws Exception {
        int requests = 5; //set to any number for testing
        int numWorkerLeaderServers = 3; // servers that can be workers or leaders i.e. non-observers
        int startPortForWorkerLeaders = 2000;
        ServerCluster newCluster = new ServerCluster(numWorkerLeaderServers, startPortForWorkerLeaders, httpServerPort, requests);
        newCluster.buildAndRun();
        Thread.sleep(1000);
        System.out.println("Test complete");

    }
    @Test
    public void runServerCluster7Servers() throws Exception {
        int requests = 7; //set to any number for testing
        int numWorkerLeaderServers = 7; // servers that can be workers or leaders i.e. non-observers
        int startPortForWorkerLeaders = 3000;
        ServerCluster newCluster = new ServerCluster(numWorkerLeaderServers, startPortForWorkerLeaders, httpServerPort, requests);
        newCluster.buildAndRun();
        Thread.sleep(1000);
        System.out.println("Test complete");

    }
}