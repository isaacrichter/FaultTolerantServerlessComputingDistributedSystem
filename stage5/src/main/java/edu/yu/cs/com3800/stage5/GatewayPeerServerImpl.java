package edu.yu.cs.com3800.stage5;

import edu.yu.cs.com3800.ZooKeeperPeerServer;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;

public class GatewayPeerServerImpl extends ZooKeeperPeerServerImpl{
    public GatewayPeerServerImpl(int myPort, long peerEpoch, Long id, Map<Long, InetSocketAddress> peerIDtoAddress, Set<Long> observers) {
        super(myPort, peerEpoch, id, peerIDtoAddress, observers);
        this.setPeerState(ZooKeeperPeerServer.ServerState.OBSERVER);
    }
}
