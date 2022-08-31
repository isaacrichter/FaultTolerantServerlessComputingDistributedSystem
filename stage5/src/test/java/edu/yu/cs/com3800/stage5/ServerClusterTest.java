package edu.yu.cs.com3800.stage5;
import edu.yu.cs.com3800.ServerCluster;
import org.junit.Before;
import org.junit.Test;

public class ServerClusterTest {

    final int httpServerPort = 8888;

    public ServerClusterTest(){
        System.out.println("\n Quick Tip:  Log files are organized by class. If two tests are run, their log files will end up next to each other in each class folder \n");
    }


    @Test
    public void DemoScriptEquivalentTest() throws Exception {
        System.out.println("Test: DemoScriptEquivalentTest");
        int requests = 9; //set to any number for testing, run twice, before and after killing servers
        int numWorkerLeaderServers = 7; // servers that can be workers or leaders i.e. non-observers
        int startPortForWorkerLeaders = 1000;
        ServerCluster newCluster = new ServerCluster(numWorkerLeaderServers, startPortForWorkerLeaders, httpServerPort, requests);
        newCluster.buildAndRun();
        Thread.sleep(1000);
        System.out.println("Test complete");
    }

}