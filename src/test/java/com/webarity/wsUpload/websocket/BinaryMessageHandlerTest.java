package com.webarity.wsUpload.websocket;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Stream;

public class BinaryMessageHandlerTest {

    int headerSize = 123;
    int fileMarker = 0;
    FileModel[] uploadedFiles;
    long totalSize = 0;

    @BeforeEach
    void initUploadedFiles() {
        FileModel fm1 = new FileModel();
        fm1.setName("first");
        fm1.setSize(500L);
        fm1.setType("thing");
        FileModel fm2 = new FileModel();
        fm2.setName("second");
        fm2.setSize(465L);
        fm2.setType("thing");
        uploadedFiles = new FileModel[] { fm1, fm2 };
        
        totalSize = Stream.of(uploadedFiles).reduce(0L, (totalSize, file) -> totalSize + file.getSize(), (a, b) -> a + b) + headerSize;
    }

    @Test
    void expectedLength() {
        assertEquals(totalSize, 465 + 500 + headerSize);
    }

    @Test
    @DisplayName("First starts at test")
    void testOffests() {
        long start = Stream.of(uploadedFiles).limit(fileMarker).reduce(0L, (totalSize, file) -> totalSize + file.getSize(), (a, b) -> a + b) + headerSize;
        assertEquals(123, start);
    }

    @Test
    @DisplayName("Second starts at test")
    void testOffests2() {
        long start = Stream.of(uploadedFiles).limit(1).reduce(0L, (totalSize, file) -> totalSize + file.getSize(), (a, b) -> a + b) + headerSize;
        assertEquals(uploadedFiles[0].getSize() + headerSize, start);
    }
}