package com.sun.sgs.example.battleboard.server;

import java.io.Serializable;
import java.util.Properties;
import java.util.logging.Logger;

import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.ManagedObject;

/**
 * The {@link SimBoot} class of the BattleBoard server application. 
 * <p>
 */
public class BattleBoardServer
    implements ManagedObject, Serializable, AppListener
{
    private static final long serialVersionUID = 1;

    private static Logger log =
        Logger.getLogger(BattleBoardServer.class.getName());

    // Implement AppListener

    /**
     * Boots the BattleBoard application.
     * <p>
     * Invoked by the SGS stack when BattleBoard is booted.
     */
    public void initialize(Properties props) {
        
        log.info("Booting BattleBoard Server");

        Matchmaker.initialize(props);
    }

    // SimUserListener methods

    /**
     * {@inheritDoc}
     */
    public ClientSessionListener loggedIn(ClientSession session) {
        return Player.loggedIn(session);
    }
}
