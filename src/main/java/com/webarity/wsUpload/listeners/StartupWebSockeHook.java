package com.webarity.wsUpload.listeners;

import static com.webarity.wsUpload.WSUploadUIRenderer.ENDPOINT_STATE;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.faces.event.AbortProcessingException;
import javax.faces.event.SystemEvent;
import javax.faces.event.SystemEventListener;
import javax.inject.Inject;
import javax.servlet.ServletContext;
import javax.websocket.DeploymentException;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpointConfig;

import com.webarity.wsUpload.WSUploadUIRenderer;
import com.webarity.wsUpload.qualifiers.WSConfig;

public class StartupWebSockeHook implements SystemEventListener {

    private static final Logger l = Logger.getLogger(StartupWebSockeHook.class.getName());

    @Inject @WSConfig ServerEndpointConfig conf;

    @Override
    public void processEvent(SystemEvent event) throws AbortProcessingException {
        ServletContext ctx = (ServletContext) event.getFacesContext().getExternalContext().getContext();
        ServerContainer sc = (ServerContainer) ctx.getAttribute(ServerContainer.class.getName());

        try {
            sc.addEndpoint(conf);
            l.log(Level.INFO, String.format("WebSocket endpoint for '%s' created at: %s", "WSUpload", WSUploadUIRenderer.getEndpointAddress()));
            ENDPOINT_STATE.set(true);
        } catch (DeploymentException ex) {
            ex.printStackTrace();
            ENDPOINT_STATE.set(false);
            l.log(Level.SEVERE, String.format("Failed to deploy WebSocket endpoint for %s. Reason is: %s", "WSUpload", ex.getMessage()));
        }
    }

    @Override
    public boolean isListenerForSource(Object source) {
        return source instanceof javax.faces.application.Application;
    }
}