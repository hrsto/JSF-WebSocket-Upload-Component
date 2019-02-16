package com.webarity.wsUpload;

/**
 * Central point where all keys of the resource bundle are aggregated. Used with resource bundles instead of specifying keys manually.
 */
public enum Messages {
    COMMON_dropHere("common.dropHere"),
    ERROR_WebSocketNotOpen("err.WebSocketNotOpen"),
    ERROR_failUpload("err.failUpload"),
    ERROR_noSession("err.noSession"),
    ERROR_pathNotAccessible("err.pathNotAccessible"),

    HTML_percent("html.percent"),
    HTML_err_uploadFailed("html.err.uploadFailed"),
    HTML_connetion_closed("html.connetion.closed"),
    HTML_connection_err("html.connection.err"),
    HTML_wsNotSupported("html.wsNotSupported"),
    HTML_filesUploaded("html.filesUploaded"),
    HTML_fileUploaded("html.fileUploaded"),

    WS_sessionExists("ws.sessionExists"),
    WS_sessionNotWhitelisted("ws.sessionNotWhitelisted"),
    WS_sessionExpired("ws.sessionExpired"),
    WS_sessionMaxTransferExceeded("ws.sessionMaxTransferExceeded"),
    WS_sessionTransferComplete("ws.sessionTransferComplete"),
    WS_sessionFailTransfer("ws.sessionFailTransfer"),
    
    ;

    private Messages(String val) {
        this.val = val;
    }

    private String val;

    /**
     * 
     * @return key to the resource bundle .properties 
     */
    public String getVal() { return val; }
}