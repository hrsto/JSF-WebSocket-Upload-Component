package com.webarity.wsUpload.websocket;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.Initialized;
import javax.enterprise.context.SessionScoped;
import javax.enterprise.event.Observes;
import javax.servlet.http.HttpSession;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.Session;

import com.webarity.wsUpload.Messages;
import com.webarity.wsUpload.WSUploadUIRenderer;
import com.webarity.wsUpload.fileOps.FileDeleter;

@SessionScoped
public class SessionRegistry implements Serializable {

    private static final long serialVersionUID = 1L;

    static private ConcurrentMap<String, Set<Session>> sessions = new ConcurrentHashMap<>();
    static private ConcurrentMap<String, List<CompletableFutureUpload>> transferRegistry = new ConcurrentHashMap<>();

    private String sid; // session id from HttpRequest

    /**
     * <p>If this component is used, then its render method will be used as well. The render method will call this method with a HttpSession ID String to signal that a WebSocket connection opened from within the allowed HttpSession should be permitted.</p>
     * 
     * @param httpSessionID
     */
    public static void whitelistSession(String httpSessionID) {
        if (httpSessionID == null || httpSessionID.isEmpty()) return;

        sessions.putIfAbsent(httpSessionID, new HashSet<Session>());
        transferRegistry.putIfAbsent(httpSessionID, new ArrayList<CompletableFutureUpload>());
    }

    public static void includeSession(Session s) {
        Optional.ofNullable(sessions.get(getHTTPSessionID(s)))
        .ifPresentOrElse(openSessions -> openSessions.stream()
        .filter(singleSession -> singleSession.getId().compareTo(s.getId()) == 0)
        .findAny()
        .ifPresentOrElse(any -> UploaderEndpoint.closeWebSocketSession(s, CloseCodes.VIOLATED_POLICY, Messages.WS_sessionExists), 
        () -> {
            //HttpSession is whitelisted and there aren't any duplicate WebSocket session, so add this session to the list
            openSessions.add(s);
        }), 
        () -> {
            //HttpSession is apparently not whitelisted, close the WebSocket session
            UploaderEndpoint.closeWebSocketSession(s, CloseCodes.VIOLATED_POLICY, Messages.WS_sessionNotWhitelisted);
        });
    }

    public static void removeSession(Session s) {
        Optional.ofNullable(sessions.get(getHTTPSessionID(s))).ifPresent(sessionSet -> sessionSet.remove(s));
    }

    private static String getHTTPSessionID(Session s) {
        return Optional.ofNullable((String)s.getUserProperties().get(Config.ID_KEY)).orElse("");
    }

    protected static void addTransferID(String sessionID, CompletableFutureUpload transfer) {
        Optional.ofNullable(transferRegistry.get(sessionID)).ifPresent(treg -> treg.add(transfer));
    }

    public static List<CompletableFutureUpload> getTransfers(String sessionID) {
        return transferRegistry.get(sessionID);
    }

    /**
     * <p>On session end:</p>
     * <ul>
     *  <li>All open WebSocket session for the current HttpSession will be close.</li>
     *  <li>All files that were uploaded, but user haven't clicked the submit button - i.e. the {@link WSUploadUIRenderer}'s decode method haven't been invoked, will be deleted.</li>
     * </ul>
     */
    @PreDestroy
    private void clean() {
        Optional.ofNullable(sessions.remove(sid))
        .ifPresent(wsSessions -> wsSessions.forEach(wsSession -> UploaderEndpoint.closeWebSocketSession(wsSession, CloseCodes.GOING_AWAY, Messages.WS_sessionExpired)));

        Optional.ofNullable(transferRegistry.get(sid))
        .ifPresent(transfers -> transfers.forEach(asdf -> cleanFiles(asdf.getPath(), asdf.getTransferID())));
    }

    @PostConstruct
    private void creation() {

    }

    @SuppressWarnings({ "unused" })
    private void initSess(@Observes @Initialized(SessionScoped.class) HttpSession evt) {
        if (!WSUploadUIRenderer.ENDPOINT_STATE.get()) return;
        this.sid = evt.getId();
    }

    public static void cleanFiles(String path, String transferID) {
        try {
            Files.walkFileTree(Paths.get(path, transferID), new FileDeleter());
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}