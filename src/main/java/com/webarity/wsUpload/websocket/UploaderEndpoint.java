package com.webarity.wsUpload.websocket;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import javax.websocket.CloseReason.CloseCodes;

import com.webarity.wsUpload.Messages;
import com.webarity.wsUpload.WSUploadUI;

public class UploaderEndpoint extends Endpoint {

    /**
     * <p>This is necessary because of 2 reasons:</p>
     * <ul>
     *  <li>A call to {@link Session#isOpen()} works on WebSocket level and not for a particular {@link Session}</li>
     *  <li>A call to {@link Session#getOpenSessions()} while it works and can be used to determine if certain {@link Session} is part of the Set or not, on Wildfly 15 at least, sometimes misses when a Session was just closed. Probably a timing issue.</li>
     * </ul>
     * <p>There this is used a Session user property via {@link Session#getUserProperties()} and set/unset on {@link #onOpen(Session, EndpointConfig)} and {@link #onClose(Session, CloseReason)} respectively.</p>
     */
    public static final String IS_CLOSED = "isSessionClosed";

    @Override
    public void onOpen(Session session, EndpointConfig config) {
        session.getUserProperties().put(IS_CLOSED, false);
        SessionRegistry.includeSession(session);
        if (Optional.ofNullable((Boolean)session.getUserProperties().get(IS_CLOSED)).orElse(true)) {
            return; // if Session were to be invalid, it would be closed in the includeSession call.
        }
        try {
            session.addMessageHandler(new BinaryMessageHandler(session));
        } catch (IllegalStateException | IOException ex) {
            try {
                session.close(new CloseReason(CloseCodes.UNEXPECTED_CONDITION, "Failed to start transfer."));
            } catch (IOException exx) {
                exx.printStackTrace();
            }
            ex.printStackTrace();
        }
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        session.getUserProperties().put(IS_CLOSED, true);
        SessionRegistry.removeSession(session);
    }

    @Override
    public void onError(Session session, Throwable thr) {
        super.onError(session, thr);
    }

    public static void closeWebSocketSession(Session s, CloseCodes cd, Messages msg) {
        try {
            String message = Optional.ofNullable((ResourceBundle) s.getUserProperties().get(Config.WS_RESOUCE_BUNDLE)).orElse(WSUploadUI.getResourceBundle(Locale.getDefault())).getString(msg.getVal());
            s.close(new CloseReason(cd, message));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}