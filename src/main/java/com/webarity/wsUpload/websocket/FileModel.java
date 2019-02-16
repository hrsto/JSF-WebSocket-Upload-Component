package com.webarity.wsUpload.websocket;

public class FileModel {

    private String name;
    private String type;
    private Long size;
    private Long lastMod;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }
    public Long getLastMod() { return lastMod; }
    public void setLastMod(Long lastMod) { this.lastMod = lastMod; }
}