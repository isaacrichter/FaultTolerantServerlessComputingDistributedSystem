package edu.yu.cs.com3800;

import edu.yu.cs.com3800.stage5.GatewayPeerServerImpl;
import edu.yu.cs.com3800.stage5.GatewayServer;
import edu.yu.cs.com3800.stage5.ZooKeeperPeerServerImpl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.util.*;
import java.util.logging.Logger;

public class ServerCluster implements LoggingServer{
    private LinkedList<Integer> ports;
    private ArrayList<ZooKeeperPeerServer> servers;
    private InetSocketAddress clientAddress;
    private int numWorkerLeaderServers;
    private  GatewayServer gateway;
    private int portForHTTPServer;
    private int startPortForWorkerLeaders;
    private HashMap<Long, InetSocketAddress> peerIDtoAddress;
    private Logger logger;
    private int numRequests;

    private String validClass = "package edu.yu.cs.fall2019.com3800.stage1;\n\npublic class HelloWorld\n{\n    public String run()\n    {\n        return \"Hello world!\";\n    }\n}\n";


    public ServerCluster(int numWorkerLeaderServers, int startPortForWorkerLeaders, int portForHTTPServer, int numRequests) throws IOException {

        this.numWorkerLeaderServers = numWorkerLeaderServers;
        this.portForHTTPServer = portForHTTPServer;
        this.ports = new LinkedList<>();
        this.startPortForWorkerLeaders = startPortForWorkerLeaders;
        this.numRequests = numRequests;
        logger = initializeLogging(-1, "ServerCluster");
        logger.fine("ServerCluster initialized.");
    }

    public void buildAndRun(){
        logger.info("Building cluster...");
        createIDsAndAddresses(startPortForWorkerLeaders);
        GatewayPeerServerImpl gatewayPeerServerImpl = createStartServers();
        logger.fine("gateway is server: " + gatewayPeerServerImpl.getServerId());
        gateway = new GatewayServer(portForHTTPServer, gatewayPeerServerImpl);
        gateway.start();
        //wait for servers to get started
        try { Thread.sleep(3000); } catch (Exception e) {e.printStackTrace();}

        printLeaders();

        try{
            //step 3: send requests to gateway

            List<ClientImpl> clients = new LinkedList<>();
            logger.info("building " + numRequests + " clients and sending one request from each");
            for(int i = 0; i < numRequests; i++){
                ClientImpl client = new ClientImpl("localhost", portForHTTPServer);//pass in port for server so client can connect
                clients.add(client);
                String code = this.validClass.replace("world!", "world! from code version " + i);
                client.sendCompileAndRunRequest(code);
            }

            logger.info("Client received responses:");
            int count = 0;
            for(ClientImpl client: clients){
                logger.info("client #" + count + "\nResponse code: " + client.getResponse().getCode() + "\nResponse body: " + client.getResponse().getBody());
                count++;
            }

            //KILL A FOLLOWER
            System.out.println("KILLING FOLLOWER WITH ID 2");
            shutdownServer2();

            //WAIT 3 * HEARTBEAT TIME
            Thread.sleep(40000);
            System.out.print("SERVERS ON FAILED LIST: ");

            if(gatewayPeerServerImpl.getFailedServers().size() > 0){
                for(long server: gatewayPeerServerImpl.getFailedServers()){
                    System.out.println(server + " ");
                }
            }


            //KILL THE LEADER
            shutdownLeader();
            // WAIT 1000,
            Thread.sleep(30000);
            //SEND 9 MORE REQUESTS
            List<ClientImpl> clients2 = new LinkedList<>();

            for(int i = 9; i < numRequests + 9; i++){

                ClientImpl client = new ClientImpl("localhost", portForHTTPServer);//pass in port for server so client can connect
                clients2.add(client);
                String code = this.validClass.replace("world!", "world! from code version " + i);
                client.sendCompileAndRunRequest(code);
            }
            //Print ID of new leader
            while(gatewayPeerServerImpl.getCurrentLeader() == null){
                Thread.sleep(500);
            }
            System.out.print("Leader: " + gatewayPeerServerImpl.getCurrentLeader().getProposedLeaderID());

            logger.info("Client received responses:");
            int count2 = 0;
            for(ClientImpl client: clients2){
                logger.info("client #" + (count2 + 9) + "\nResponse code: " + client.getResponse().getCode() + "\nResponse body: " + client.getResponse().getBody());
                count2++;
            }


            //send one more client request
            ClientImpl finalClient = new ClientImpl("localhost", portForHTTPServer);//pass in port for server so client can connect
            String code = this.validClass.replace("world!", "world! from final code version");
            finalClient.sendCompileAndRunRequest(code);
            logger.info("final client: " + "\nResponse code: " + finalClient.getResponse().getCode() + "\nResponse body: " + finalClient.getResponse().getBody());

            System.out.println("\n All gossip files are located under /logFiles/gossipManager");


            stopServers();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void shutdownServer2(){
        ZooKeeperPeerServer server2 = servers.get(2);
        server2.shutdown();
        System.out.println("SERVER 2 SHUTTING DOWN");
    }
    private void shutdownLeader(){
        ZooKeeperPeerServer leader = servers.get(numWorkerLeaderServers);
        leader.shutdown();
    }

    private void createIDsAndAddresses(int startPortForWorkerLeaders){
        //create IDs and addresses for workerleaders
        this.peerIDtoAddress = new HashMap<>(8);

        //first add observer
        peerIDtoAddress.put(0L, new InetSocketAddress("localhost", startPortForWorkerLeaders - 10));
        //add all others
        for (int i = 1; i <= numWorkerLeaderServers; i++) {
            peerIDtoAddress.put(Integer.valueOf(i).longValue(), new InetSocketAddress("localhost", startPortForWorkerLeaders));
            ports.add(startPortForWorkerLeaders);
            startPortForWorkerLeaders+=10;
        }
        logger.info("InetAddresses and ids created");
    }

    private GatewayPeerServerImpl createStartServers(){
        this.servers = new ArrayList<>(3);
        HashSet<Long> observerIDs = new HashSet<>();
        observerIDs.add(0L);
        GatewayPeerServerImpl toReturn = null;
        for (Map.Entry<Long, InetSocketAddress> entry : peerIDtoAddress.entrySet()) {
            HashMap<Long, InetSocketAddress> map = (HashMap<Long, InetSocketAddress>) peerIDtoAddress.clone();
            map.remove(entry.getKey());
            if(entry.getKey().equals(0L)){
                logger.fine("creating gateway zookeeper server");
                GatewayPeerServerImpl gatewayServerImpl = new GatewayPeerServerImpl(entry.getValue().getPort(), 0, entry.getKey(), map, observerIDs);
                toReturn = gatewayServerImpl;
                this.servers.add(gatewayServerImpl);
                logger.fine("starting gateway zookeeper");
                new Thread((Runnable) gatewayServerImpl, "Gateway server on port " + gatewayServerImpl.getAddress().getPort()).start();
                //gateway = new GatewayServer(portForHTTPServer, gatewayServerImpl);
                logger.fine("gateway zookeeper up and running");
            }else{
                logger.fine("creating server " + entry.getKey());
                ZooKeeperPeerServer server = new ZooKeeperPeerServerImpl(entry.getValue().getPort(), 0, entry.getKey(), map, observerIDs);
                this.servers.add(server);
                new Thread((Runnable) server, "Server on port " + server.getAddress().getPort()).start();
                logger.fine("server " + entry.getKey() + " up and running");
            }
        }
        logger.info("all servers up and running");
        return toReturn;
    }



    private void printLeaders() {
        for (ZooKeeperPeerServer server : this.servers) {
            Vote leader = server.getCurrentLeader();
            if (leader != null) {
                System.out.println("Server on port " + server.getAddress().getPort() + " whose ID is " + server.getServerId() + " has the following ID as its leader: " + leader.getProposedLeaderID() + " and its state is " + server.getPeerState().name());
            }
        }
    }

    private void stopServers() {
        for (ZooKeeperPeerServer server : this.servers) {
            server.shutdown();
        }
        gateway.stop();
    }


}
