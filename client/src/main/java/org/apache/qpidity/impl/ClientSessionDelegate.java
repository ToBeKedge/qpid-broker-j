package org.apache.qpidity.impl;

import java.nio.ByteBuffer;

import org.apache.qpidity.Frame;
import org.apache.qpidity.MessageAcquired;
import org.apache.qpidity.MessageReject;
import org.apache.qpidity.MessageTransfer;
import org.apache.qpidity.QpidException;
import org.apache.qpidity.Range;
import org.apache.qpidity.RangeSet;
import org.apache.qpidity.Session;
import org.apache.qpidity.SessionDelegate;
import org.apache.qpidity.Struct;
import org.apache.qpidity.client.MessagePartListener;


public class ClientSessionDelegate extends SessionDelegate
{    
    private MessageTransfer _currentTransfer;
    private MessagePartListener _currentMessageListener;
    
    @Override public void data(Session ssn, Frame frame)
    {
        for (ByteBuffer b : frame)
        {    
            _currentMessageListener.addData(b);
        }
        if (frame.isLastSegment() && frame.isLastFrame())
        {
            _currentMessageListener.messageReceived();
        }
        
    }

    @Override public void headers(Session ssn, Struct... headers)
    {
        _currentMessageListener.messageHeaders(headers);
    }


    @Override public void messageTransfer(Session session, MessageTransfer currentTransfer)
    {
        _currentTransfer = currentTransfer;
        _currentMessageListener = ((ClientSession)session).getMessageListerners().get(currentTransfer.getDestination());
        
        //a better way is to tell the broker to stop the transfer
        if (_currentMessageListener == null && _currentTransfer.getAcquireMode() == 1)
        {
            RangeSet transfers = new RangeSet();
            transfers.add(_currentTransfer.getId());            
            session.messageRelease(transfers);
        }
    }
    
    //  --------------------------------------------
    //   Message methods
    // --------------------------------------------
    
    
    @Override public void messageReject(Session session, MessageReject struct) 
    {
        for (Range range : struct.getTransfers())
        {
            for (long l = range.getLower(); l <= range.getUpper(); l++)
            {
                System.out.println("message rejected: " +
                        session.getCommand((int) l));
            }
        }
        ((ClientSession)session).setRejectedMessages(struct.getTransfers());
        ((ClientSession)session).notifyException(new QpidException("Message Rejected",0,null));
    }
    
    @Override public void messageAcquired(Session session, MessageAcquired struct) 
    {
        ((ClientSession)session).setAccquiredMessages(struct.getTransfers());
    }
}
