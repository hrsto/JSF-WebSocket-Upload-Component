package com.webarity.wsUpload.fileOps;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileDeleter implements FileVisitor<Path> {

    private static final Logger l = Logger.getLogger(FileDeleter.class.getName());

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (Files.isWritable(file)) {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
        }
        else {
            l.log(Level.SEVERE, String.format("File couldn't deleted: %s", file.toAbsolutePath().toString()));
            return FileVisitResult.TERMINATE;
        }
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
        l.log(Level.SEVERE, String.format("File visit failure for: %s", file.toAbsolutePath().toString()));
        return FileVisitResult.TERMINATE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        if (Files.isDirectory(dir) && Files.isWritable(dir)) {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
        }
        else {
            l.log(Level.SEVERE, String.format("Dir couldn't deleted: %s", dir.toAbsolutePath().toString()));
            return FileVisitResult.TERMINATE;
        }
    }

}