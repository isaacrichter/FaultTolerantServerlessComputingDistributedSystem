package edu.yu.cs.com3800.stage5;

import edu.yu.cs.com3800.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
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
    private Set<Long> observers;

    private Logger logger;

    private UDPMessageSender senderWorker;
    private UDPMessageReceiver receiverWorker;

    private RoundRobinLeader roundRobinLeader;
    private TCPListener listener;
    private LinkedBlockingQueue<TCPListener.MessageAndSocket> incomingTCPMessagesAndSockets;

    private JavaRunnerFollower javaRunnerFollower;

    private  GossipManager gossipManager;
    private HashSet<Long> failedServers;

    public ZooKeeperPeerServerImpl(int myPort, long peerEpoch, Long id, Map<Long,InetSocketAddress> peerIDtoAddress, Set<Long> observers) {
        this.myPort = myPort;
        this.id = id;
        this.peerEpoch = peerEpoch;
        this.peerIDtoAddress = peerIDtoAddress;
        this.observers = observers;
        this.myAddress = new InetSocketAddress("localhost", myPort); //InetAddress.getByName("localhost")
        this.incomingMessages = new LinkedBlockingQueue<>();
        this.outgoingMessages = new LinkedBlockingQueue<>();
        this.setPeerState(ServerState.LOOKING);

        failedServers = new HashSet<>();

        try{
            this.logger = initializeLogging(this.id, "ZooKeeperPeerServerImpl");
        }catch(Exception e){
            e.printStackTrace();
        }

        logger.fine("initialized at " + System.currentTimeMillis());

    }

    @Override
    public void shutdown(){
        logger.log(Level.FINE, "server shutdown");
        if(this.javaRunnerFollower != null) {
            this.javaRunnerFollower.shutdown();
        }
        if(this.roundRobinLeader != null) {
            this.roundRobinLeader.shutdown();
            this.listener.shutdown();
        }
        this.gossipManager.shutdown();
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
        //step 2: create and run thread that listens for messages sent to this server along with the gossip thread
        try {
            LinkedBlockingQueue<Message> gossipMessages = new LinkedBlockingQueue<>();
            gossipManager = new GossipManager(gossipMessages, this);
            Util.startAsDaemon(gossipManager,"gossip-manager-on-server-" + this.id);
            receiverWorker = new UDPMessageReceiver(incomingMessages, myAddress, myPort, this, gossipMessages);
        } catch (IOException e) {
            logger.log(Level.WARNING, "IO error in receiver worker creation");
            //System.out.println("IO error in receiver worker creation");
        }
        senderWorker.start();
        receiverWorker.start();

        //step 3: main server loop
        try{
            while (!this.shutdown){
                switch (getPeerState()){
                    case LOOKING:
                        javaRunnerFollower = null;
                        roundRobinLeader = null;
                        logger.log(Level.FINE, "Case: Looking for leader, initializing election");
                        //start leader election, set leader to the election winner
                        ZooKeeperLeaderElection election = new ZooKeeperLeaderElection(this, incomingMessages, observers.size(), peerIDtoAddress.size() - failedServers.size());
                        election.lookForLeader(); // sets state, leader and clears incoming messages
                        election = null; // allows the now old one to get gc'ed
                        logger.log(Level.INFO, "Election ended: " + getCurrentLeader().getProposedLeaderID() + " elected");

                        break;
                    case FOLLOWING:
                        if (javaRunnerFollower == null){
                            logger.log(Level.FINE, "Case: Is follower, initializing java runner and TCPListener");
                            roundRobinLeader = null;
                            incomingTCPMessagesAndSockets = new LinkedBlockingQueue<>();
                            if(listener != null) {
                                listener.shutdown();
                            }
                            listener = new TCPListener(this.myPort, incomingTCPMessagesAndSockets, this.id, this);
                            Util.startAsDaemon(listener, "TCPListener for server " + this.id);
                            javaRunnerFollower = new JavaRunnerFollower(this, this.incomingTCPMessagesAndSockets, this.outgoingMessages);
                            Util.startAsDaemon(javaRunnerFollower, "JavaRunnerFollower on server #" + this.id);
                        }

                        //Makes instance of JavaRunnerFollower and waits for incoming work messages to feed it
                        break;
                    case LEADING:
                        if (roundRobinLeader == null) {
                            logger.log(Level.FINE, "Case: Is leader, initializing round robin");
                            javaRunnerFollower = null; //maybe shutdown instead? or both?

                            incomingTCPMessagesAndSockets = new LinkedBlockingQueue<>();
                            if(listener != null) {
                                listener.shutdown();
                            }
                            listener = new TCPListener(this.myPort, incomingTCPMessagesAndSockets, this.id, this);
                            Util.startAsDaemon(listener, "TCPListener for server " + this.id);
                            roundRobinLeader = new RoundRobinLeader(this, this.incomingTCPMessagesAndSockets, this.outgoingMessages, this.peerIDtoAddress, observers);
                            Util.startAsDaemon(roundRobinLeader, "RoundRobinLeader on server #" + this.id); //NEED NEW STRATEGY TO GET HAVING RETURN VAL WAITING TO RUN. CLASS LEVEL VARIABLE WITH LOOP?
                            //roundRobinLeader = null; //allows the now old one to get gc'ed
                            //logger.log(Level.INFO, "No longer leading");
                            //Makes instance of Round Robin Leader. When gets a request, it sends it to its RRL to give out.
                            //when gets a notice of finished work along with return string, returns in back to sender
                        }
                        break;
                    case OBSERVER:
                        //don't let it change its status from observer.
                        //nothing in this block should lead to a change of server state
                        if(currentLeader == null){
                            logger.log(Level.FINE, "Case: Observer looking for leader, initializing election");
                            //start leader election, set leader to the election winner
                            ZooKeeperLeaderElection observerElection = new ZooKeeperLeaderElection(this, incomingMessages, observers.size(), peerIDtoAddress.size());
                            observerElection.lookForLeader(); // sets state, leader and clears incoming messages
                            observerElection = null; // allows the now old one to get gc'ed
                            logger.log(Level.FINE, "Observer election ended");
                        }

                        break;
                }
            }

        } catch (Exception e) {
            //code...
            logger.log(Level.WARNING, "exception in server loop: " + e.getMessage());
        }
    }

    public void addToFailed(long serverID){
        failedServers.add(serverID);
    }

    public Set<Long> getLiveServers(){
        Set<Long> livePeers = peerIDtoAddress.keySet();
        livePeers.removeAll(failedServers);
        return livePeers;
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

    public void increaseEpoch(){ peerEpoch ++; }

    public void setEpoch(long newEpoch){
        peerEpoch = newEpoch;
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

    public InetSocketAddress getLeaderInetSocket(){
        if(currentLeader == null){
            return null;
        }
        return this.peerIDtoAddress.get(this.currentLeader.getProposedLeaderID());
    }

    @Override
    public  boolean isPeerDead(long peerID){
        if(failedServers.contains(peerID)){
            return true;
        }
        return false;
    }

    @Override
    public  boolean isPeerDead(InetSocketAddress socketAddress){
        //socket address into long peer id
        if(failedServers.contains(InetSocketAddressToServerID(socketAddress))){
            return true;
        }
        return false;
    }

    public Long InetSocketAddressToServerID(InetSocketAddress socketAddress){
        if(socketAddress == null){
            return null;
        }
        //flip around the hashmap, get the corresponding socket address
        HashMap<InetSocketAddress, Long> addressToPeerID = new HashMap<>();
        for (Long serverID : peerIDtoAddress.keySet()) {
            addressToPeerID.put(peerIDtoAddress.get(serverID), serverID);
        }
        return  addressToPeerID.get(socketAddress);
    }

    public HashSet<Long> getFailedServers(){
        return this.failedServers;
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

    public RoundRobinLeader getRoundRobinLeader(){
        return this.roundRobinLeader;
    }

}