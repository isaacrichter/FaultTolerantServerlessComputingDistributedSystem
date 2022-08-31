package edu.yu.cs.com3800.stage4;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;

public class GatewayPeerServerImpl extends ZooKeeperPeerServerImpl{
    public GatewayPeerServerImpl(int myPort, long peerEpoch, Long id, Map<Long, InetSocketAddress> peerIDtoAddress, Set<Long> observers) {
        super(myPort, peerEpoch, id, peerIDtoAddress, observers);
        this.setPeerState(ServerState.OBSERVER);
    }
}
