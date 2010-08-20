/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSource, Inc.  All rights reserved.  http://www.mulesource.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.test.integration.client;

import org.mule.api.MuleMessage;
import org.mule.module.client.MuleClient;
import org.mule.tck.FunctionalTestCase;

public class MuleClientInThreadTestCase extends FunctionalTestCase
{
    int numMessages = 100000;
    
    @Override
    protected String getConfigResources()
    {
        return "org/mule/test/integration/client/client-in-thread.xml";
    }

    public void testException() throws Exception
    {
        Thread tester1 = new Tester();
        tester1.start();
    }
    
    class Tester extends Thread
    {
        @Override
        public void run()
        {
            try
            {
                MuleClient client = new MuleClient(muleContext);
                
                for (int i = 0; i < numMessages; ++i)
                {
                    client.dispatch("vm://in", "test", null);
                }
    
                MuleMessage msg;
                for (int i = 0; i < numMessages; ++i)
                {
                    msg = client.request("vm://out", 5000);
                    assertNotNull(msg);
                }
            }
            catch (Exception e)
            {
                fail(e.getMessage());
            }
        }        
    };
}


