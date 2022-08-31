package edu.yu.cs.com3800.stage5;


import edu.yu.cs.com3800.LoggingServer;
import edu.yu.cs.com3800.Message;
import edu.yu.cs.com3800.ZooKeeperPeerServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

public class GossipManager extends Thread implements LoggingServer {
    int heartbeat;
    ZooKeeperPeerServerImpl server;
    HashMap<Long, HeartbeatAndTimeStamp> gossipTable;
    LinkedBlockingQueue<Message> gossipMessages;
    HashSet<Long> cleanup;
    LinkedList<String> gossipMessagesLog;

    boolean shutdown;
    final int GOSSIP = 2000; // equivalent to 3 second, since we are using milliseconds
    final int FAIL = 10 * GOSSIP;
    final int CLEANUP = 2 * FAIL;
    long lastGossip = System.currentTimeMillis();

    private Logger logger;

    public GossipManager(LinkedBlockingQueue<Message> gossipMessages, ZooKeeperPeerServerImpl server){
        heartbeat = 0;
        this.server = server;
        gossipTable = new HashMap<>();
        cleanup = new HashSet<>();
        gossipMessagesLog = new LinkedList<>();
        shutdown = false;
        this.gossipMessages = gossipMessages;
        this.setDaemon(true);


        try {
            this.logger = initializeLogging(this.server.getServerId(), "GossipManager");
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.fine("initialized...");
    }

    /**SET THIS THREAD AS A DAEMON*/

    @Override
    public void run() {
        //actually run the diff part of the gossiper in one bigger loop
        //put own heartbeat into hashmap
        gossipTable.put(this.server.getServerId(), new HeartbeatAndTimeStamp(0, System.currentTimeMillis()));

        while(!shutdown){
            //check for incoming gossip messages and update the gossipTable accordingly
            processIncomingMessages();

            //every GOSSIP seconds, send out a notification and go through gossip table to find dead servers
            if(timeToGossip()){
                HeartbeatAndTimeStamp heartbeatAndTimeStamp = gossipTable.get(this.server.getServerId());
                heartbeatAndTimeStamp.setHeartbeat(heartbeatAndTimeStamp.getHeartbeat() + 1);
                heartbeatAndTimeStamp.setTimeStamp(System.currentTimeMillis());

                checkForFailedAndCleanups();
                sendGossip();
                lastGossip = System.currentTimeMillis();
            }
        }

    }

    private void processIncomingMessages(){
        while(!gossipMessages.isEmpty()){
            Message message = gossipMessages.poll();
            gossipMessagesLog.add( "From server " + this.server.InetSocketAddressToServerID(new InetSocketAddress(message.getSenderHost(), message.getSenderPort())) + " " + message.toString());
            logger.fine("Incoming message from server " + this.server.InetSocketAddressToServerID(new InetSocketAddress(message.getSenderHost(), message.getSenderPort())));
            HashMap<Long, Integer> serverToHeartbeat = getGossipFromMessage(message);
            for (Long server: serverToHeartbeat.keySet()) {
                HeartbeatAndTimeStamp oldHeartbeat = gossipTable.get(server);
                if(oldHeartbeat == null){
                    //logger.fine("Adding server " + server + " with heartbeat of " + serverToHeartbeat.get(server) + " to the gossip table");
                    gossipTable.put(server, new HeartbeatAndTimeStamp(serverToHeartbeat.get(server), System.currentTimeMillis()));
                    logger.fine(this.server.getServerId() + ": updated " + server + "'s heartbeat sequence to " + serverToHeartbeat.get(server) + " based on message from server " +  this.server.InetSocketAddressToServerID(new InetSocketAddress(message.getSenderHost(), message.getSenderPort())) + " at time " + gossipTable.get(server).getTimeStamp() + " (added)");
                    //“[insert this node’s ID here]: updated [serverID]’s heartbeat sequence to [sequence number] based on message from [source server ID] at node time [this node’s clock time]”
                } else {
                    if (oldHeartbeat.getHeartbeat() < serverToHeartbeat.get(server)){
                        //logger.fine("Updating server " + server + " to heartbeat of " + serverToHeartbeat.get(server) + " in the gossip table");
                        gossipTable.put(server, new HeartbeatAndTimeStamp(serverToHeartbeat.get(server), System.currentTimeMillis()));
                        logger.fine(this.server.getServerId() + ": updated " + server + "'s heartbeat sequence to " + serverToHeartbeat.get(server) + " based on message from server " + this.server.InetSocketAddressToServerID(new InetSocketAddress(message.getSenderHost(), message.getSenderPort())) + " at time " + gossipTable.get(server).getTimeStamp() + " (updated)");

                    }
                }
            }

        }
    }

    private void sendGossip(){
        //choose random server from alive servers a to send the gossip to
        Set<Long> liveServers = server.getLiveServers();
        long targetServer = Long.MIN_VALUE; //stand in val
        int size = liveServers.size();
        int random = new Random().nextInt(size); // In real life, the Random object should be rather more shared than this
        int i = 0;
        for(Long server : liveServers) {
            if (random == i) {
                targetServer = server;
                break;
            }
            i++;
        }
        logger.fine("Sending gossip to server " + targetServer);

        //construct message, pass to udp sender
        this.server.sendMessage(Message.MessageType.GOSSIP, buildGossipMsgContent(gossipTable), this.server.getPeerByID(targetServer));
    }


    private void checkForFailedAndCleanups(){
        LinkedList<Long> toRemove = new LinkedList<>();
        for (long serverID: gossipTable.keySet()){
            if(System.currentTimeMillis() - gossipTable.get(serverID).getTimeStamp() >= FAIL){
                if(System.currentTimeMillis() - gossipTable.get(serverID).getTimeStamp() < CLEANUP){
                    logger.info(this.server.getServerId() + ": no heartbeat from server " + serverID + " - server failed"); //will also print to console (level.info)
                    //gossipTable.remove(serverID);
                    toRemove.add(serverID);
                    //this.server.addToFailed(serverID);
                    //this.cleanup.add(serverID);
                }else{
                    logger.fine("Server " + serverID + "cleaned up");
                    this.cleanup.remove(serverID);
                }
            }
        }
        for(Long id : toRemove){
            gossipTable.remove(id);
            this.server.addToFailed(id);
            this.cleanup.add(id);
            if(server.getCurrentLeader() != null && server.getCurrentLeader().getProposedLeaderID() == id){
                //leader has died, call new election
                // ANY COMPLETED WORK MUST BE QUEUED
                logger.info("Leader died. Entering new election...");
                server.increaseEpoch();
                server.setPeerState(ZooKeeperPeerServer.ServerState.LOOKING);
                try {
                    server.setCurrentLeader(null);
                }catch (Exception ignored){}
            }
        }
    }

    private boolean timeToGossip(){
        if(System.currentTimeMillis() - lastGossip >= GOSSIP){
            return true;
        }
        return false;
    }

    public void shutdown(){
        shutdown = true;
    }

    static byte[] buildGossipMsgContent(HashMap<Long, HeartbeatAndTimeStamp> gossipTable) {
        //build message from gossip table
        //contents byte array. semi opposite of getGossipFromMessage
        // size of int + mapSize( size of long + int (server-heartbeat pairs))
        //4 + mapSize(4 + 8)
        //initialize byte array of size, put the stuff in
        int pairs = gossipTable.size();
        byte[] contents = new byte[4 + (pairs * 12)];
        ByteBuffer buffer = ByteBuffer.allocate(4 + (pairs * 12));
        //ORDER MIGHT NEED TO BE FLIPPED
        buffer.putInt(pairs);
        for (long server: gossipTable.keySet()){
            buffer.putLong(server);
            buffer.putInt(gossipTable.get(server).getHeartbeat());
        }

        return  buffer.array(); //MIGHT
    }

    static HashMap<Long, Integer> getGossipFromMessage(Message message) {
        //messsage.contents is a byte array that holds the election notification info.
        //get it, and then unpack it into an election notification constructor
        HashMap<Long,Integer> serverToHeartbeat = new HashMap<>();
        byte[] contents = message.getMessageContents();
        if(message.getMessageType() == Message.MessageType.GOSSIP) {
            ByteBuffer msgBytes = ByteBuffer.wrap(message.getMessageContents());
            int pairs = msgBytes.getInt();
            for(int i = 0; i < pairs; i++){
                serverToHeartbeat.put(msgBytes.getLong(), msgBytes.getInt());
            }
            return serverToHeartbeat;
            //return new ElectionNotification(leader, ZooKeeperPeerServer.ServerState.getServerState(stateChar), senderID, peerEpoch);
        } else {
            // in later stages for different types, might need to add else
            return null;
        }

    }

    public class HeartbeatAndTimeStamp{
        int heartbeat;
        long timeStamp;

        public HeartbeatAndTimeStamp(int heartbeat, long timeStamp){
            this.heartbeat = heartbeat;
            this.timeStamp = timeStamp;
        }

        public void setHeartbeat(int heartbeat) {
            this.heartbeat = heartbeat;
        }

        public int getHeartbeat() {
            return heartbeat;
        }

        public void setTimeStamp(long timeStamp) {
            this.timeStamp = timeStamp;
        }

        public long getTimeStamp() {
            return timeStamp;
        }
    }


}
