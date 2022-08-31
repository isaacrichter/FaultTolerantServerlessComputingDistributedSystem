package edu.yu.cs.com3800.stage2;

import edu.yu.cs.com3800.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;


public class ZooKeeperPeerServerImpl extends Thread implements ZooKeeperPeerServer{
    private final InetSocketAddress myAddress;
    private final int myPort;
    private ServerState state;
    private volatile boolean shutdown;
    private LinkedBlockingQueue<Message> outgoingMessages;
    private LinkedBlockingQueue<Message> incomingMessages;
    private Long id;
    private long peerEpoch;
    private volatile Vote currentLeader; //current leader mean the server that the server is currently voting for = thinks will be the leader
    private Map<Long,InetSocketAddress> peerIDtoAddress;

    private Logger logger;

    private UDPMessageSender senderWorker;
    private UDPMessageReceiver receiverWorker;

    public ZooKeeperPeerServerImpl(int myPort, long peerEpoch, Long id, Map<Long,InetSocketAddress> peerIDtoAddress) {
        //code here...
        System.out.println("CREATING SERVER #" + id);
        this.myPort = myPort;
        this.id = id;
        this.peerEpoch = peerEpoch;
        this.peerIDtoAddress = peerIDtoAddress;
        this.myAddress = new InetSocketAddress("localhost", myPort); //InetAddress.getByName("localhost")
        this.incomingMessages = new LinkedBlockingQueue<>();
        this.outgoingMessages = new LinkedBlockingQueue<>();
        this.setPeerState(ServerState.LOOKING);

        try{
            this.logger = initializeLogging(this.id,ZooKeeperPeerServerImpl.class.getCanonicalName());
        }catch(Exception e){
            e.printStackTrace();
        }

    }

    @Override
    public void shutdown(){
        logger.log(Level.INFO, "server shutdown");
        this.shutdown = true;
        this.senderWorker.shutdown();
        this.receiverWorker.shutdown();
    }

    @Override
    public void run(){
        //step 1: create and run thread that sends broadcast messages
        //-------WHAT SHOULD THIS BE SET TO
        senderWorker = new UDPMessageSender(outgoingMessages, myPort); //put in (outgoing messages queue, server udp port)
        senderWorker.setServerID(this.id); //must be called before senderWorker.start() or log will be saved to wrong folder
        //step 2: create and run thread that listens for messages sent to this server
        try {
            receiverWorker = new UDPMessageReceiver(incomingMessages, myAddress, myPort, this);
        } catch (IOException e) {
            logger.log(Level.INFO, "IO error in receiver worker creation");
            //System.out.println("IO error in receiver worker creation");
        }
        senderWorker.start();
        receiverWorker.start();

        //step 3: main server loop
        try{
            while (!this.shutdown){
                switch (getPeerState()){
                    case LOOKING:
                        logger.log(Level.INFO, "Case: Looking for leader, initializing election");
                        //start leader election, set leader to the election winner
                        ZooKeeperLeaderElection election = new ZooKeeperLeaderElection(this, incomingMessages);
                        election.lookForLeader(); // sets state, leader and clears incoming messages
                        logger.log(Level.INFO, "Election ended");

                        break;
                    case FOLLOWING:
                        //send reply of who this server is following
                        //not for stage 2
                        break;
                    case LEADING:
                        //send reply of this server/ its id
                        //not for stage 2
                        break;
                    case OBSERVER:
                        //do nothing - observers dont send messages
                        //not for stage 2
                }
            }

        } catch (Exception e) {
            //code...
            logger.log(Level.WARNING, "exception in server loop: " + e.getMessage());
        }
    }

    public Long getServerId(){
        return this.id;
    }

    public int getUdpPort(){
        return this.myPort; //is udp port supposed to be the same as myPort? it is in current implementation above
    }

    public long getPeerEpoch(){
        return this.peerEpoch;
    }

    public InetSocketAddress getAddress(){
        return this.myAddress;
    }

    public Vote getCurrentLeader(){
        return this.currentLeader;
    }

    public void setCurrentLeader(Vote v) throws IOException{
        this.currentLeader = v;
    }

    public void sendMessage(Message.MessageType type, byte[] messageContents, InetSocketAddress target) throws IllegalArgumentException{
        //build message
        Message message = new Message(type,messageContents, getAddress().getHostName(), getAddress().getPort(), target.getHostName(), target.getPort()); // getHostName() or getHostNameString()
        //put in outgoing queue
        this.outgoingMessages.add(message);

    }

    public void sendBroadcast(Message.MessageType type, byte[] messageContents){
        //build messages to send for all active serevers, add to outgoing queue
        for (InetSocketAddress target: this.peerIDtoAddress.values()) {
            //build message
            Message message = new Message(type,messageContents, getAddress().getHostName(), getAddress().getPort(), target.getHostName(), target.getPort()); // getHostName() or getHostNameString()
            //put in outgoing queue
            this.outgoingMessages.add(message);

        }
    }

    public ServerState getPeerState(){
        return this.state;
    }

    public void setPeerState(ServerState newState){
        this.state = newState;
    }

    public InetSocketAddress getPeerByID(long peerId){
        return this.peerIDtoAddress.get(peerId);
    }

    public int getQuorumSize(){ //Does this account for self? maybe need to add 1
        return this.peerIDtoAddress.size();
    }

}