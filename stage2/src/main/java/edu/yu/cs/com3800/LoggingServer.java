package edu.yu.cs.com3800;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public interface LoggingServer {
    //supposed to make this interface with default methods to make logging easier
    //lofgiles should have names based on time stamps, but the loggers themselves can be based on cannonical name and server id, since theose wont overlap in the same server system
    default Logger initializeLogging(long serverID, String cannonName) throws IOException {
        //create folder for all server logs if doesn't already exist
        File dir = new File(System.getProperty("user.dir") + File.separator + "logFiles" + File.separator + cannonName + File.separator);
        if (!dir.exists()) {
            dir.mkdir(); //only makes dir if doesnt exist
        }
        FileHandler fh = new FileHandler(System.getProperty("user.dir") + File.separator + "logFiles" + File.separator + cannonName + File.separator + serverID + "-" +  System.currentTimeMillis() + ".log");
        Logger logger = Logger.getLogger(cannonName + serverID);
        logger.setUseParentHandlers(false);
        logger.addHandler(fh);
        SimpleFormatter formatter = new SimpleFormatter();
        fh.setFormatter(formatter);

        logger.log(Level.INFO, "Initialized at: " +  System.currentTimeMillis() + " for server " + serverID +" \n");
        return logger;
    }

}
