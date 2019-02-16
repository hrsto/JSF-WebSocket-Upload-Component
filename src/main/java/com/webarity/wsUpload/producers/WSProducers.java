package com.webarity.wsUpload.producers;

import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;

import com.webarity.wsUpload.WSUploadUIRenderer;
import com.webarity.wsUpload.qualifiers.WSConfig;
import com.webarity.wsUpload.websocket.Config;
import javax.websocket.server.ServerEndpointConfig;

import com.webarity.wsUpload.websocket.UploaderEndpoint;

public class WSProducers {

    @Produces
    @WSConfig
    public ServerEndpointConfig qwe(InjectionPoint ip) {
        String wsAddr = ip.getAnnotated().getAnnotation(WSConfig.class).endpointAddress();
        wsAddr = wsAddr.isBlank() ? WSUploadUIRenderer.getEndpointAddress() : wsAddr;

        ServerEndpointConfig.Builder b = ServerEndpointConfig.Builder.create(UploaderEndpoint.class, wsAddr);
        b.configurator(new Config());

        return b.build();
    }
}