package edu.yu.cs.com3800;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.logging.*;

public interface LoggingServer {
    //supposed to make this interface with default methods to make logging easier
    //lofgiles should have names based on time stamps, but the loggers themselves can be based on cannonical name and server id, since theose wont overlap in the same server system
    default Logger initializeLogging(long serverID, String cannonName) throws IOException {
        //create folder for all server logs if doesn't already exist
        File dir = new File(System.getProperty("user.dir") + File.separator + "logFiles" + File.separator + cannonName + File.separator);
        dir.mkdirs(); //only makes dir if doesnt exist

        Logger logger;
        if(cannonName.equals("RoundRobinLeaderTCPWorker") || cannonName.equals("GatewayServer")){
            logger = Logger.getLogger(cannonName + serverID + System.currentTimeMillis()); //need to the millis so that each RoundRobinLeaderTCPWorker doesn't get the same logger object
        }else{
            logger = Logger.getLogger(cannonName + serverID); //need to the millis so that each RoundRobinLeaderTCPWorker doesn't get the same logger object
        }

        FileHandler fh = new FileHandler(System.getProperty("user.dir") + File.separator + "logFiles" + File.separator + cannonName + File.separator + serverID + "-" +  System.currentTimeMillis() + ".log");
        SimpleFormatter formatter = new SimpleFormatter();
        fh.setFormatter(formatter);
        fh.setLevel(Level.ALL);

        logger.setLevel(Level.ALL);
        logger.addHandler(fh);

        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.INFO);
        logger.addHandler(handler);


        logger.setUseParentHandlers(false);
        logger.log(Level.FINE, "Initialized at: " +  System.currentTimeMillis() + " for server " + serverID +" \n");
        return logger;
    }

}
