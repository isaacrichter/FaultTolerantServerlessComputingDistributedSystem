package edu.yu.cs.com3800;

import edu.yu.cs.com3800.stage3.ZooKeeperPeerServerImpl;
import  edu.yu.cs.com3800.Util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RoundRobinLeader extends Thread implements LoggingServer{
    //constructor takes name of server it is running on, the names of all worker servers
    ZooKeeperPeerServer myServer;
    LinkedBlockingQueue<Message> incomingMessages;
    LinkedBlockingQueue<Message> outgoingMessages;
    Map<Long, InetSocketAddress> otherServers;
    Queue<Long> roundRobinOrder;
    HashMap<Long, Integer> requestIDtoSenderPort;
    HashMap<Long, String> requestIDtoSenderHost;
    Logger logger;
    private Long requestIDsCount = 0L;


    public RoundRobinLeader(ZooKeeperPeerServer server, LinkedBlockingQueue<Message> incomingMessages, LinkedBlockingQueue<Message> outgoingMessages,Map<Long, InetSocketAddress> otherServers){
        this.myServer = server;
        this.incomingMessages = incomingMessages;
        this.outgoingMessages = outgoingMessages;
        this.otherServers = otherServers;
        try {
            this.logger = initializeLogging(this.myServer.getServerId(), this.getClass().getCanonicalName());
        } catch (IOException e) {
            e.printStackTrace();
        }
        //make list of all other servers
        roundRobinOrder = new LinkedList<>();
        roundRobinOrder.addAll(otherServers.keySet());
        requestIDtoSenderPort = new HashMap<>(); // needs to the requast id, and the port and host to know where to send it back to. map the request to the port and hostname
        requestIDtoSenderHost = new HashMap<>();
    }

    public void shutdown(){
        interrupt();
    }

    public void run(){

        while (this.myServer.getPeerState() == ZooKeeperPeerServer.ServerState.LEADING) {
            Message incoming = null;
            while (incoming == null){
                incoming = incomingMessages.poll();
            }


            switch (incoming.getMessageType()){
                case ELECTION:
                    //for stage 3, assume its from old election
                    break;
                case WORK:
                    incoming.setRequestID(requestIDsCount++);
                    requestIDtoSenderHost.put(incoming.getRequestID(), incoming.getSenderHost());
                    requestIDtoSenderPort.put(incoming.getRequestID(), incoming.getSenderPort());

                    byte[] codeInBytes = incoming.getMessageContents();

                    Long serverAtBat = roundRobinOrder.poll();
                    roundRobinOrder.add(serverAtBat); //put in back in line

                    Message workOrder = new Message(Message.MessageType.WORK, codeInBytes, myServer.getAddress().getHostName(), myServer.getAddress().getPort(), otherServers.get(serverAtBat).getHostName(), otherServers.get(serverAtBat).getPort(), incoming.getRequestID());
                    try {
                        logger.log(Level.INFO, "Serving Request #" + requestIDsCount + " to server #" + serverAtBat);
                        outgoingMessages.put(workOrder);
                    } catch (InterruptedException e) {
                        logger.log(Level.INFO, e.getMessage());
                    }
                    //add it to the hashmap of requests to senderID, and send of to next server in round robin
                    break;
                case COMPLETED_WORK:
                    String clientHost = requestIDtoSenderHost.remove(incoming.getRequestID());
                    Integer clientPort = requestIDtoSenderPort.remove(incoming.getRequestID());
                    Message results = new Message(Message.MessageType.COMPLETED_WORK, incoming.getMessageContents(), myServer.getAddress().getHostName(), myServer.getAddress().getPort(), clientHost, clientPort, incoming.getRequestID(), incoming.getErrorOccurred());
                    try {
                        logger.log(Level.INFO, "returning result of request #" + incoming.getRequestID());
                        outgoingMessages.put(results);
                    } catch (InterruptedException e) {
                        logger.log(Level.INFO, e.getMessage());
                    }
                    // get the actual string from the message
                case GOSSIP:
                    //not for stage 3
                    break;
                case NEW_LEADER_GETTING_LAST_WORK:
                    //not for stage 3
                    break;

            }

        }
    }




}
