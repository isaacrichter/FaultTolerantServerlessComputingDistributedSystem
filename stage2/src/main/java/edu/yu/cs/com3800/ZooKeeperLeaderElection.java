package edu.yu.cs.com3800;

import edu.yu.cs.com3800.stage2.ZooKeeperPeerServerImpl;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static edu.yu.cs.com3800.ZooKeeperPeerServer.ServerState;

public class ZooKeeperLeaderElection {
    /**
     * time to wait once we believe we've reached the end of leader election.
     */
    private final static int finalizeWait = 800; // change to 200

    /**
     * Upper bound on the amount of time between two consecutive notification checks.
     * This impacts the amount of time to get the system up again after long partitions. Currently 60 seconds.
     */
    private final static int maxNotificationInterval = 60000;

    private ZooKeeperPeerServer myPeerServer;
    private LinkedBlockingQueue<Message> incomingMessages;
    private int proposedEpoch;
    private long proposedLeader;
    private Map<Long, ElectionNotification> votes;


    public ZooKeeperLeaderElection(ZooKeeperPeerServer server, LinkedBlockingQueue<Message> incomingMessages) {
        this.incomingMessages = incomingMessages;
        this.myPeerServer = server;
        votes = new HashMap<>();

        this.proposedLeader = myPeerServer.getServerId();
        proposedEpoch = 0;
    }

    private synchronized Vote getCurrentVote() {
        return new Vote(this.proposedLeader, this.proposedEpoch);
    }

    public synchronized Vote lookForLeader() {
        Logger logger = Logger.getLogger(ZooKeeperPeerServerImpl.class.getCanonicalName() + "-server-id-" + this.myPeerServer.getServerId());
        ElectionNotification selfVote = new ElectionNotification(this.myPeerServer.getServerId(), this.myPeerServer.getPeerState(), this.myPeerServer.getServerId(), this.myPeerServer.getPeerEpoch());
        votes.put(this.myPeerServer.getServerId(), selfVote);
        //send initial notifications to other peers to get things started
        sendNotifications(selfVote);

        //Loop, exchanging notifications with other servers until we find a leader
        while (this.myPeerServer.getPeerState() == ServerState.LOOKING) {
            //Remove next notification from queue, timing out after 2 times the termination time
            Message incoming = null;
            try {
                int waiTime = 2;
                while ((incoming = incomingMessages.poll(waiTime, TimeUnit.MILLISECONDS)) == null) {
                    if(waiTime > maxNotificationInterval){
                        logger.log(Level.WARNING, "Null Poll Time Limit Exceeded, exiting election without choosing leader");
                        return null; // no leader found, server continues in its running loop
                    }
                    waiTime *= 2;
                }
            }catch (Exception e){
                logger.log(Level.INFO, e.getMessage());
            }
            ElectionNotification currentNotification = getNotificationFromMessage(incoming);

            //if no notifications received..
            //..resend notifications to prompt a reply from others..
            //.and implement exponential back-off when notifications not received..
            //if/when we get a message and it's from a valid server and for a valid server..
            //switch on the STATE OF THE SENDER:
            switch (currentNotification.getState()) {
                case LOOKING: //if the sender is also looking
                    processVote(currentNotification);
                    break;
                case FOLLOWING: /*OR*/
                case LEADING: //if the sender is following a leader already or thinks it is the leader
                    logger.log(Level.INFO, "got message from server with leader");
                    //IF: see if the sender's vote allows me to reach a conclusion based on the election epoch that I'm in, i.e. it gives the majority to the vote of the FOLLOWING or LEADING peer whose vote I just received.
                    if(this.myPeerServer.getPeerEpoch() == currentNotification.getPeerEpoch()) {
                        processVote(currentNotification);
                    //ELSE: if n is from a LATER election epoch
                    //IF a quorum from that epoch are voting for the same peer as the vote of the FOLLOWING or LEADING peer whose vote I just received.
                    //      THEN accept their leader, and update my epoch to be their epoch
                    } else if(this.myPeerServer.getPeerEpoch() < currentNotification.getPeerEpoch()){
                        //STAGE 3 LOGIC
                    }
                    //ELSE:
                    //      keep looping on the election loop.
                    break;
            }
        }
        return null;
    }

    private void processVote(ElectionNotification currentNotification){
        //if the received message has a vote for a leader which supersedes mine,
        // change my vote and tell all my peers what my new vote is.
        Logger logger = Logger.getLogger(ZooKeeperPeerServerImpl.class.getCanonicalName() + "-server-id-" + this.myPeerServer.getServerId());

        //keep track of the votes I received and who I received them from.
        votes.put(currentNotification.getSenderID(), currentNotification);

        if (supersedesCurrentVote(currentNotification.getSenderID(), currentNotification.getPeerEpoch())) {
            logger.log(Level.INFO, "got a better vote-- changing vote to server: " + currentNotification.getProposedLeaderID());
            this.proposedLeader = currentNotification.getProposedLeaderID();
            this.proposedEpoch = (int) currentNotification.getPeerEpoch();
            ElectionNotification myNewElectionN = new ElectionNotification(currentNotification.getProposedLeaderID(), this.myPeerServer.getPeerState(), this.myPeerServer.getServerId(), currentNotification.getPeerEpoch());
            votes.put(this.myPeerServer.getServerId(), myNewElectionN);
            sendNotifications(myNewElectionN);
        }


        // if I have enough votes to declare my currently proposed leader as the leader:
        if(haveEnoughVotes(votes, currentNotification)){
            logger.log(Level.INFO,   currentNotification.getProposedLeaderID() + " seems to have enough votes...");
            //      first check if there are any new votes for a higher ranked possible leader before I declare a leader. //////use a a while loop
            try {
                logger.log(Level.INFO, "checking Q... ");
                Message n = null;
                while ((n = incomingMessages.poll(finalizeWait, TimeUnit.MILLISECONDS)) != null){
                    ElectionNotification en = getNotificationFromMessage(n);
                    logger.log(Level.INFO, "Q says: server " + en.getSenderID() + " votes for " + en.getProposedLeaderID());
                    //if(supersedesCurrentVote(en.getProposedLeaderID(),en.getPeerEpoch())){ //ERROR HERE!!!!!
                    if(supersedesPresumedWinner(en.getProposedLeaderID(), en.getPeerEpoch(), currentNotification.getProposedLeaderID(), currentNotification.getPeerEpoch())){
                        incomingMessages.put(n);
                        logger.log(Level.INFO, currentNotification.getProposedLeaderID() + " not accepted as winner - had incoming vote for " + en.getProposedLeaderID() + " by server " + en.getSenderID());
                        break;

                    }
                }
                if (n == null){
                    logger.log(Level.INFO, currentNotification.getProposedLeaderID() + " accepted as leader!" );
                    this.acceptElectionWinner(currentNotification);
                }
            }catch(Exception e){ logger.log(Level.INFO, "error occured");}
        }
    }

    static ElectionNotification getNotificationFromMessage(Message message) {
        //messsage.contents is a byte array that holds the election notification info.
        //get it, and then unpack it into an election notification constructor
        byte[] contents = message.getMessageContents();
        //
        if(message.getMessageType() == Message.MessageType.ELECTION) {
            ByteBuffer msgBytes = ByteBuffer.wrap(message.getMessageContents());
            long leader = msgBytes.getLong();
            char stateChar = msgBytes.getChar();
            long senderID = msgBytes.getLong();
            long peerEpoch = msgBytes.getLong();
            return new ElectionNotification(leader, ZooKeeperPeerServer.ServerState.getServerState(stateChar), senderID, peerEpoch);
        } else {
            // in later stages for different types, might need to add else
            return null;
        }

    }

    static byte[] buildMsgContent(ElectionNotification electionNotification) {
        //build message from vote
        //contents byte array. semi opposite of getNotificationFromMessage
        // size of long, char, long, long
        //initialize byte array of size, but the stuff in
        // size 26 and add those four things
        byte[] contents = new byte[26];
        ByteBuffer buffer = ByteBuffer.allocate(26);
        //ORDER MIGHT NEED TO BE FLIPPED
        buffer.putLong(electionNotification.getProposedLeaderID());
        buffer.putChar(getChar(electionNotification.getState()));
        buffer.putLong(electionNotification.getSenderID());
        buffer.putLong(electionNotification.getPeerEpoch());
        return  buffer.array(); //MIGHT
    }

    private static char getChar(ServerState state) {
        switch (state) {
            case LOOKING:
                return 'O';
            case LEADING:
                return 'E';
            case FOLLOWING:
                return 'F';
            case OBSERVER:
                return 'B';
        }
        return 'Z';
    }

    private Vote acceptElectionWinner(ElectionNotification n) {
        //set my state to either LEADING or FOLLOWING
        if(n.getProposedLeaderID() == myPeerServer.getServerId()){
            myPeerServer.setPeerState(ServerState.LEADING);
        } else {
            myPeerServer.setPeerState(ServerState.FOLLOWING);
        }

        try {
            myPeerServer.setCurrentLeader(new Vote(n.getProposedLeaderID(), n.getPeerEpoch()));
        }catch(Exception e){
            e.printStackTrace();
        }

        //clear out the incoming queue before returning
        incomingMessages.clear();
        return  n;
        // ANYTHING REAL TO RETURN? IF NOT AND SERVER IS WORKING, CHANGE SIGNATURE TO VOID
    }

    /*
     * We return true if one of the following three cases hold:
     * 1- New epoch is higher
     * 2- New epoch is the same as current epoch, but server id is higher.
     */
    protected boolean supersedesCurrentVote(long newId, long newEpoch) {
        return (newEpoch > this.proposedEpoch) || ((newEpoch == this.proposedEpoch) && (newId > this.proposedLeader));
    }

    private boolean supersedesPresumedWinner(long incomingID, long incomingEpoch, long presumedWinnerId, long presumedWinnerEpoch){
        return (incomingEpoch > presumedWinnerEpoch) || ((incomingEpoch == presumedWinnerEpoch) && incomingID > presumedWinnerId);
    }

    /**
     * Termination predicate. Given a set of votes, determines if have sufficient support for the proposal to declare the end of the election round.
     * Who voted for who isn't relevant, we only care that each server has one current vote
     */
    protected boolean haveEnoughVotes(Map<Long, ElectionNotification> votes, Vote proposal) {
        //For future stages, will maybe need to check to see if any votes need to be eliminated based on old epochs
        //is the number of votes for the proposal > the size of my peer server’s quorum?
        int size = votes.size();
        int majority = (size / 2) + 1; //works for both evens and odds
        int voteCounter = 0;
        for(Long server: votes.keySet()){
            if(votes.get(server).getProposedLeaderID() == proposal.getProposedLeaderID() && votes.get(server).getPeerEpoch() == proposal.getPeerEpoch()){
                voteCounter++;
            }
            if(voteCounter >= majority){
                return true;
            }
        }

        Logger logger = Logger.getLogger(ZooKeeperPeerServerImpl.class.getCanonicalName()  + this.myPeerServer.getServerId());
        logger.log(Level.INFO, "not enough votes. Needed " + majority + " for majority " + "only had " + voteCounter);

        return false;
    }

    private void sendNotifications(ElectionNotification notification){
        this.myPeerServer.sendBroadcast(Message.MessageType.ELECTION, buildMsgContent(notification));
    }
}