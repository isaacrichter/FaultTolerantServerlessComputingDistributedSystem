package edu.yu.cs.com3800.stage3;

import edu.yu.cs.com3800.*;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Input into constructor number of servers, number of requests to send the servers as a client,
 * and look at log files to see which requests were handled by which servers, and what the leader output is
 * start port and client port are parameters as well, as ports seem to take a little extra time to become available again
 */
public class FlexTests{
    private String validClass = "package edu.yu.cs.fall2019.com3800.stage1;\n\npublic class HelloWorld\n{\n    public String run()\n    {\n        return \"Hello world!\";\n    }\n}\n";

    private LinkedBlockingQueue<Message> outgoingMessages;
    private LinkedBlockingQueue<Message> incomingMessages;
    private LinkedList<Integer> ports;
    //private int[] ports = {8010, 8020};
    private int leaderPort;
    private int clientPort;
    private InetSocketAddress clientAddress;
    private ArrayList<ZooKeeperPeerServer> servers;
    private int numWorkRequests;

    public FlexTests(int numServers, int numWorkRequests, int startPort, int clientPort) throws Exception {
        this.numWorkRequests = numWorkRequests;
        this.ports = new LinkedList<>();
        this.clientPort = clientPort;
        this.clientAddress = new InetSocketAddress("localhost", this.clientPort);
        //step 1: create sender & sending queue
        this.outgoingMessages = new LinkedBlockingQueue<>();
        UDPMessageSender sender = new UDPMessageSender(this.outgoingMessages, clientPort);
        //step 2: create servers

        //create IDs and addresses
        HashMap<Long, InetSocketAddress> peerIDtoAddress = new HashMap<>(8);
        for (int i = 0; i < numServers; i++) {
            peerIDtoAddress.put(Integer.valueOf(i).longValue(), new InetSocketAddress("localhost", startPort));
            ports.add(startPort);
            leaderPort = startPort; // last port becomes leader;
            startPort+=10;
        }

        //create servers
        this.servers = new ArrayList<>(3);
        for (Map.Entry<Long, InetSocketAddress> entry : peerIDtoAddress.entrySet()) {
            HashMap<Long, InetSocketAddress> map = (HashMap<Long, InetSocketAddress>) peerIDtoAddress.clone();
            map.remove(entry.getKey());
            ZooKeeperPeerServer server = new ZooKeeperPeerServerImpl(entry.getValue().getPort(), 0, entry.getKey(), map);
            this.servers.add(server);
            new Thread((Runnable) server, "Server on port " + server.getAddress().getPort()).start();
        }


        //step2.1: wait for servers to get started
        try {
            Thread.sleep(3000);
        }
        catch (Exception e) {
        }
        printLeaders();
        //step 3: since we know who will win the election, send requests to the leader, this.leaderPort
        for (int i = 0; i < this.numWorkRequests; i++) {
            String code = this.validClass.replace("world!", "world! from code version " + i);
            sendMessage(code);
        }
        Util.startAsDaemon(sender, "Sender thread");
        this.incomingMessages = new LinkedBlockingQueue<>();
        UDPMessageReceiver receiver = new UDPMessageReceiver(this.incomingMessages, this.clientAddress, this.clientPort, null);
        Util.startAsDaemon(receiver, "Receiver thread");
        //step 4: validate responses from leader

        printResponses();

        //step 5: stop servers
        stopServers();
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
    }

    private void printResponses() throws Exception {
        String completeResponse = "";
        for (int i = 0; i < numWorkRequests; i++) {
            Message msg = this.incomingMessages.take();
            String response = new String(msg.getMessageContents());
            completeResponse += "Response #" + i + ":\n" + response + "\n";
        }
        System.out.println(completeResponse);
    }

    private void sendMessage(String code) throws InterruptedException {
        Message msg = new Message(Message.MessageType.WORK, code.getBytes(), this.clientAddress.getHostString(), this.clientPort, "localhost", this.leaderPort);
        this.outgoingMessages.put(msg);
    }

}