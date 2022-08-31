package edu.yu.cs.com3800;

import edu.yu.cs.com3800.stage4.GatewayPeerServerImpl;
import edu.yu.cs.com3800.stage4.GatewayServer;
import edu.yu.cs.com3800.stage4.ZooKeeperPeerServerImpl;

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
            //step 3: send one requests to gateway
            List<ClientImpl> clients = new LinkedList<>();
            logger.info("building " + numRequests + " clients and sending one request from each");
            for(int i = 0; i < numRequests; i++){
                ClientImpl client = new ClientImpl("localhost", portForHTTPServer);//pass in port for server so client can connect
                clients.add(client);
                String code = this.validClass.replace("world!", "world! from code version " + i);
                client.sendCompileAndRunRequest(code);
            }
            Thread.sleep(3000);
            logger.info("Client received responses - each client's response should correspond with its number:");
            int count = 0;
            for(ClientImpl client: clients){
                logger.info("client #" + count + "\nResponse code: " + client.getResponse().getCode() + "\nResponse body: " + client.getResponse().getBody());
                count++;
            }


            stopServers();
        }catch (Exception e){
            e.printStackTrace();
        }



        //After this works, design tests so that more than one requests is being send to the gateway at once
        /*
        for (int i = 0; i < this.numWorkRequests; i++) {
            String code = this.validClass.replace("world!", "world! from code version " + i);
            client.sendCompileAndRunRequest(code);
            System.out.println(client.getResponse());
        }

         */
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
