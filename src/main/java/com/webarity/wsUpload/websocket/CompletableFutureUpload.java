package com.webarity.wsUpload.websocket;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class CompletableFutureUpload extends CompletableFuture<List<Path>> implements Comparable<CompletableFutureUpload> {

    private String transferID;
    private String path;

    public CompletableFutureUpload(String transferID, String path) {
        this.transferID = transferID;
        this.path = path;
    }

    public String getTransferID() {
        return transferID;
    }

    public String getPath() {
        return path;
    }

    @Override
    public int hashCode() {
        return transferID.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CompletableFutureUpload)
            return ((CompletableFutureUpload) obj).getTransferID().compareTo(getTransferID()) == 0;
        else if (obj instanceof String)
            return ((String) obj).compareTo(getTransferID()) == 0;
        else
            return false;
    }

    public boolean complete(FileModel[] value) {
        try {
            return super.complete(Files.walk(Paths.get(getPath(), getTransferID())).filter(file -> !Files.isDirectory(file)).collect(Collectors.toList()));
        } catch (IOException ex) {
            ex.printStackTrace();
            return super.completeExceptionally(ex);
        }
    }

    @Override
    public int compareTo(CompletableFutureUpload obj) {
        return obj.getTransferID().compareTo(getTransferID());
    }
}