package com.webarity.wsUpload.websocket;

import java.io.Serializable;

import javax.enterprise.inject.Default;

@Default
public class FileUploadedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    public FileUploadedEvent() {
    }
}