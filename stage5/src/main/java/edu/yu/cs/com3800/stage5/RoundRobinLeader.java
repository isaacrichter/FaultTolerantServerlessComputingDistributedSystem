package edu.yu.cs.com3800.stage5;


import edu.yu.cs.com3800.LoggingServer;
import edu.yu.cs.com3800.Message;
import edu.yu.cs.com3800.Util;
import edu.yu.cs.com3800.ZooKeeperPeerServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import static edu.yu.cs.com3800.Util.toTCPPort;

public class RoundRobinLeader extends Thread implements LoggingServer {
    //constructor takes name of server it is running on, the names of all worker servers
    private ZooKeeperPeerServerImpl myServer;
    private LinkedBlockingQueue<TCPListener.MessageAndSocket> incomingTCPMessagesAndSockets;
    private LinkedBlockingQueue<Message> outgoingMessages;
    private Map<Long, InetSocketAddress> otherServers;
    private Queue<Long> roundRobinOrder;
    private HashMap<Long, Integer> requestIDtoSenderPort;
    private HashMap<Long, String> requestIDtoSenderHost;
    private Logger logger;
    //private Long requestIDsCount = 0L;
    private Set<Long> observers;


    public RoundRobinLeader(ZooKeeperPeerServer server, LinkedBlockingQueue<TCPListener.MessageAndSocket> incomingTCPMessagesAndSockets, LinkedBlockingQueue<Message> outgoingMessages, Map<Long, InetSocketAddress> otherServers, Set<Long> observers){
        this.myServer = (ZooKeeperPeerServerImpl) server;
        this.incomingTCPMessagesAndSockets = incomingTCPMessagesAndSockets;
        this.outgoingMessages = outgoingMessages;
        this.otherServers = otherServers; //upon construction, this hashmap of other servers does not contain itself
        this.observers = observers;
        try {
            this.logger = initializeLogging(this.myServer.getServerId(), "RoundRobinLeader");
        } catch (IOException e) {
            e.printStackTrace();
        }
        //make list of all other servers
        roundRobinOrder = new LinkedList<>();
        roundRobinOrder.addAll(otherServers.keySet());
        // if it is in the list of observers, take out of the round robin cycle
        roundRobinOrder.removeIf(observers::contains);


        requestIDtoSenderPort = new HashMap<>(); // needs to the requast id, and the port and host to know where to send it back to. map the request to the port and hostname
        requestIDtoSenderHost = new HashMap<>();
    }

    public void shutdown(){
        interrupt();
    }

    public void run(){
        logger.fine("running...");


        while (this.myServer.getPeerState() == ZooKeeperPeerServer.ServerState.LEADING) {
            logger.fine("is leader");
            TCPListener.MessageAndSocket incoming = null;
            while (incoming == null){
                incoming = incomingTCPMessagesAndSockets.poll();
            }
            logger.fine("message incoming...");

            switch (incoming.getMessage().getMessageType()){
                case ELECTION:
                    //no election messages should be coming through tcp
                    break;
                case WORK:
                    //incoming.getMessage().setRequestID(requestIDsCount++);
                    requestIDtoSenderHost.put(incoming.getMessage().getRequestID(), incoming.getMessage().getSenderHost());
                    requestIDtoSenderPort.put(incoming.getMessage().getRequestID(), incoming.getMessage().getSenderPort());

                    byte[] codeInBytes = incoming.getMessage().getMessageContents();

                    Long serverAtBat = getNextLiveServer();

                    Message workOrder = new Message(Message.MessageType.WORK, codeInBytes, myServer.getAddress().getHostName(), myServer.getAddress().getPort(), otherServers.get(serverAtBat).getHostName(), Util.toTCPPort(otherServers.get(serverAtBat).getPort()), incoming.getMessage().getRequestID());
                    logger.info("Leader serving Request #" + incoming.getMessage().getRequestID() + " to server #" + serverAtBat);
                    //CHANGES FROM HERE
                    //TO SEND OUT THE MESSAGES, CONNECT WITH THE WORKER AND SEND IT OVER USING A THREAD

                    //Instead of using COMPLETED_WORK make a connection and wait for the response in a thread friendly way - how?
                    RoundRobinLeaderTCPWorker speaker = new RoundRobinLeaderTCPWorker(workOrder, incoming.getSocket(), this.myServer.getServerId(), this.myServer);
                    Util.startAsDaemon(speaker, "work order for worker "+ serverAtBat);

                    //if the given server dies, it needs to be resent. can do this having tcphelper sending work message back the leaders listner

                    break;
                case COMPLETED_WORK:
                    String clientHost = requestIDtoSenderHost.remove(incoming.getMessage().getRequestID());
                    Integer clientPort = requestIDtoSenderPort.remove(incoming.getMessage().getRequestID());
                    Message results = new Message(Message.MessageType.COMPLETED_WORK, incoming.getMessage().getMessageContents(), myServer.getAddress().getHostName(), myServer.getAddress().getPort(), clientHost, clientPort, incoming.getMessage().getRequestID(), incoming.getMessage().getErrorOccurred());
                    try {
                        logger.info( "returning result of request #" + incoming.getMessage().getRequestID());
                        outgoingMessages.put(results);
                    } catch (InterruptedException e) {
                        logger.warning(e.getMessage());
                    }
                    // get the actual string from the message
                case GOSSIP:
                    //not for stage 4
                    break;
                case NEW_LEADER_GETTING_LAST_WORK:
                    //not for stage 4
                    break;

            }

        }
    }

    public Long getNextLiveServer(){
        Long serverAtBat = roundRobinOrder.poll();

        //dead followers not assigned work, and are removed from servers that will be given work in the future
        while(myServer.getFailedServers().contains(serverAtBat)){
            serverAtBat = roundRobinOrder.poll();
        }
        roundRobinOrder.add(serverAtBat); //put it back in line
        return serverAtBat;
    }




}
