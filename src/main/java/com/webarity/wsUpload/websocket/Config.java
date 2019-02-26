package com.webarity.wsUpload.websocket;

import javax.servlet.http.HttpSession;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;

public class Config extends ServerEndpointConfig.Configurator {

    protected static final String ID_KEY = "session_id";

    public static final String INSTANCES_MAP = "instances_map";
    
    public static final String MAX_SIZE_PARAM = "max_transfer_size";
    public static final String WS_BUFFER_SIZE = "ws_buffer_size";
    public static final String WS_TEMP_FILE_PATH = "ws_temp_file_path";
    public static final String WS_RESOUCE_BUNDLE = "ws_resource_bundle";

    @Override
    public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest rq, HandshakeResponse rs) {
        HttpSession httpSession = (HttpSession)rq.getHttpSession();
        if (httpSession != null && !httpSession.isNew() && httpSession.getId() != null && !httpSession.getId().isEmpty()) {
            sec.getUserProperties().put(ID_KEY, httpSession.getId());
            sec.getUserProperties().put(INSTANCES_MAP, httpSession.getAttribute(INSTANCES_MAP));
            sec.getUserProperties().put(WS_RESOUCE_BUNDLE, httpSession.getAttribute(WS_RESOUCE_BUNDLE));
        }
    }

}