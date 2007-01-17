package com.sun.sgs.test.app.chat;

import java.io.Serializable;
import java.util.Properties;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameExistsException;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;

public class ChatApp
	implements ManagedObject, Serializable, AppListener, ChannelListener
{
    private static final long serialVersionUID = 1L;

    /** {@inheritDoc} */
    public void initialize(Properties props) {
        System.err.format("ChatApp: Starting up\n");

        AppContext.getChannelManager().createChannel(
        	"echo", this, Delivery.ORDERED_UNRELIABLE);
    }

    /** {@inheritDoc} */
    public ClientSessionListener loggedIn(ClientSession session) {
        System.err.format("ChatApp: ClientSession [%s] joined, named \"%s\"\n",
        	session.toString(), session.getName());
        return new ChatClientSessionListener(this, session);
    }

    /** {@inheritDoc} */
    public void receivedMessage(Channel channel, ClientSession sender,
            byte[] message)
    {
	String messageString = new String(message);
	System.err.format("ChatApp: Echoing to \"%s\": [%s]\n",
		sender.getName(), messageString);
        channel.send(sender, message);
    }

    static class ChatClientSessionListener
    	    implements ClientSessionListener, ManagedObject, Serializable
    {
	private static final long serialVersionUID = 1L;
	private final ManagedReference appRef;
	private final ClientSession session;

	public ChatClientSessionListener (ChatApp app, ClientSession session) {
	    this.appRef = AppContext.getDataManager().createReference(app);
	    this.session = session;
	}

	private ChatApp getApp() {
	    return appRef.get(ChatApp.class);
	}
            
	/** {@inheritDoc} */
	public void disconnected(boolean graceful) {
	    System.err.format("ChatApp: ClientSession [%s] disconnected, " +
		    "graceful = %b\n", session.toString(), graceful);
	}

	/** {@inheritDoc} */
	public void receivedMessage(byte[] message) {
	    String command = new String(message);
	    System.err.format("ChatApp: Command from \"%s\": [%s]\n",
		    session.getName(), command);

	    ChannelManager channelMgr = AppContext.getChannelManager();
	    if (command.startsWith("/join ")) {
		String channelName = command.substring(6);
		Channel channel;
		try {
		    channel = channelMgr.createChannel(
                            channelName, getApp(), Delivery.RELIABLE);
		} catch (NameExistsException e) {
		    channel = channelMgr.getChannel(channelName);
		}
		System.err.format("ChatApp: Joining \"%s\" to channel %s\n",
			session.getName(), channel.getName());
		channel.join(session, null);
	    } else if (command.startsWith("/leave ")) {
		String channelName = command.substring(7);
		Channel channel = channelMgr.getChannel(channelName);
		System.err.format("ChatApp: Removing \"%s\" from channel %s\n",
			session.getName(), channel.getName());
		channel.leave(session);
            } else if (command.startsWith("/echo ")) {
                String contents = command.substring(6);
                System.err.format("ChatApp: \"%s\" wants us to echo \"%s\"\n",
                        session.getName(), contents);
                String reply = "ECHO" + contents;
                session.send(reply.getBytes());
	    } else if (command.equals("/exit")) {
		System.err.format("ChatApp: \"%s\" requests exit\n",
                        session.getName());
		session.disconnect();
	    } else if (command.equals("/shutdown")) {
		System.err.format("ChatApp: \"%s\" requests shutdown\n",
                        session.getName());
		// TODO: AppContext.requestShutdown();
	    } else {
		System.err.format(
                        "ChatApp: Error; \"%s\" sent unknown command [%s]\n",
			session.getName(), command);
	    }
	}
    }
}
