package edu.yu.cs.com3800.stage3;

import static org.junit.Assert.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.*;

public class Stage3Test {
    public Stage3Test(){
    }

    //see javadocs on edu.yu.cs.com3800.stage3.FlexTests
    //better to run one test at a time for keeping track of log files
    //@Test
    public void fiveServersSixteenWorkRequests() throws Exception {
        new FlexTests(5,16, 1000, 999);
    }
    @Test
    public void TenServersEightRequests() throws Exception {
        new FlexTests(20,43, 2000, 1999);
    }
}
