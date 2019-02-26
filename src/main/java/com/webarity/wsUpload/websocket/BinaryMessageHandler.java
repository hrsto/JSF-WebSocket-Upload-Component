package com.webarity.wsUpload.websocket;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbException;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.MessageHandler.Partial;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;

import com.webarity.wsUpload.Messages;

//TODO: possibly figure out file storage strategies. I.e. let user supply a class that implements an interface, and this interface to be used by #onMessage() as output; so that instead of writting to SeekableByteChannel that is made inside #onMessage(), data is passed to the user supplied class...
public class BinaryMessageHandler implements Partial<ByteBuffer> {

    private static final Jsonb JSON = JsonbBuilder.create();

    private Session thisSession;

    /**
     * For sending progress WebSocket messages back to client
     */
    private RemoteEndpoint.Basic postback;

    /**
     * Max allowed size, set via the component. If exceeded, terminate session. Works with totalTransferSize
     */
    private long maxSize = 0;

    /**
     * In memory buffer to cache the tiny WebSocket frames before writting to file system
     */
    private int bufferSize = 0;

    /**
     * Tracks the size of the header segment (i.e. the json with file info)
     */
    private long headerSizeTracker = 0;

    /**
     * Received from client via WebSocket query param
     */
    private int headerSize;

    /**
     * Used as meta info when determining where in the stream a file is located (based on size), its name, etc...
     */
    private FileModel[] uploadedFiles = null;

    /**
     * Tracks the total bytes that have been transferred. When this exceeds maxSize, terminate session
     */
    private long totalTransferSize = 0;

    /**
     * Holds the path where file will be put. In that path a dir is created. The dir will serve as a key to this transfer. The dir is set via the `id` String below
     */
    private final String tempPath;

    /**
     * Random generated name used as a transfer ID. This is the value for the transferRegistry map
     */
    private String id;

    /**
     * Holds HttpSessionId, when file completes this is will be the key for the transferRegistry map
     */
    private final String sessionID;

    /**
     * Memory buffer to consolidate the tiny WebSocket frames so that bigger chunks can be written at a time to the file system
     */
    private ByteBuffer bb;

    /**
     * <p>Will hold json. After the JSON is deserialized, can be discarded.</p>
     */
    private byte[] head;

    /**
     * <p>Safe check against malicious upload request where header size is abnormally huge</p>
     */
    private static final int MAX_HEADER_SIZE = 1000 * 250;

    /**
     * Flag that switches to false once the header has been received and processed; i.e. isolates the block of code that is reponsible for the processing of the header.
     */
    boolean procHead = true;

    /**
     * Flag that switches after the current file has been transferred to trigger creation of the next file
     */
    boolean fileCreate = true;

    /**
     * Tracks the transferred size of the currently processed file, used to check whether the file has been transferred
     */
    long fileSizeTrack = 0;

    /**
     * Files are uploaded in the order they are received. This tracks which file currenly is being processed
     */
    int fileIdx = 0;

    /**
     * Creates and writes the files to disk
     */
    SeekableByteChannel sbc = null;

    /**
     * This is the sum of the size of all the files
     */
    Long totalFileSize = 0L;

    /**
     * Key used to get the right socket instance parameters from the common http session. 
     */
    String instanceId;

    /**
     * This represents the upload session, it contains the files and transfer ID. It will be immediatelly passed to client whether or not it's completed. It's up to the backing bean to figure that out. User will be able to click the submit button right away while this will download in the background.
     */
    CompletableFutureUpload upload;

    public BinaryMessageHandler(Session thisSession) throws IOException {
        this.instanceId = (String) thisSession.getRequestParameterMap().get("id").get(0);

        @SuppressWarnings({"unchecked"})
        ConcurrentHashMap<String, Map<String, Object>> chm = (ConcurrentHashMap<String, Map<String, Object>>) thisSession.getUserProperties().get(Config.INSTANCES_MAP);
        Map<String, Object> optsMap = chm.get(instanceId);

        
        this.tempPath = Paths.get((String)optsMap.get(Config.WS_TEMP_FILE_PATH), instanceId).toString();
        this.maxSize = (Long)optsMap.get(Config.MAX_SIZE_PARAM);
        this.bufferSize = (Integer)optsMap.get(Config.WS_BUFFER_SIZE);

        this.sessionID = (String)thisSession.getUserProperties().get(Config.ID_KEY);;
        this.thisSession = thisSession;

        this.headerSize = Integer.parseInt(thisSession.getRequestParameterMap().get("headerSize").get(0));
        if (headerSize >= MAX_HEADER_SIZE) throw new IOException("Header abnormally big.");

        this.postback = thisSession.getBasicRemote();
        this.head = new byte[headerSize];
        this.bb = ByteBuffer.allocate(bufferSize);
        this.id = UUID.randomUUID().toString();

        //Immediatelly create the transfer object, register it, send its ID to the client; so that if user clicks the submit button, client will send that ID as a request param which will be read by the renderer (and subsequently the decode() method), and get it from the session store.
        this.upload = new CompletableFutureUpload(id, tempPath);
        SessionRegistry.addTransferID(sessionID, upload);
        postback.sendBinary(ByteBuffer.wrap(String.format("{\"f\":false, \"id\":\"%s\"}", id).getBytes()));
    }

    @Override
    public void onMessage(ByteBuffer frag, boolean last) {
        try {
            do {

                //the temp buffer is large, or should be larger than the WebSocket frame that is received. This block will continuously fill the temp buffer with all frames until it's full and after that the execution will continue with the next set of operations that will empty it.
                //The do { } while() loop takes care of the situation where temp buffer is filled, but there are some bytes remaining in the current frag buffer (i.e. a part of the WebSocket frame couldn't fit in the buffer).
                if (bb.remaining() > frag.remaining()) {
                    totalTransferSize += frag.remaining();

                    if (totalTransferSize > maxSize) {
                        UploaderEndpoint.closeWebSocketSession(thisSession, CloseCodes.VIOLATED_POLICY, Messages.WS_sessionMaxTransferExceeded);
                        if (sbc != null && sbc.isOpen()) sbc.close();
                        SessionRegistry.cleanFiles(tempPath, id);
                        return;
                    }

                    bb.put(frag);
                    if (!last && !frag.hasRemaining()) return;
                }
                
                bb.flip();
                //--------------

                //the very first step in processing the file/s is extracting the header info from the stream, this block does just that. After the header is received, procHead will flip to false and disable this block
                while (procHead && bb.hasRemaining()) {
                    if (bb.remaining() + headerSizeTracker >= headerSize) {
                        bb.get(head, Long.valueOf(headerSizeTracker).intValue(), head.length - Long.valueOf(headerSizeTracker).intValue());

                        uploadedFiles = deserializeFileList(head);
                        if (uploadedFiles == null) throw new Exception("Extracted file list from header is corrupted.");
                        
                        procHead = false;
                        headerSizeTracker += head.length - headerSizeTracker;
                        totalFileSize = Stream.of(uploadedFiles).reduce(0L, (a, b) -> a + b.getSize(), (a, b) -> a + b);
                    } else {
                        headerSizeTracker += bb.remaining();
                        bb.get(head, Long.valueOf(headerSizeTracker).intValue(), bb.remaining());
                    }
                }
                if (procHead) return;
                //--------------

                //this executes just once, for the very first file. After fileCreate flips, this block is disabled and execution never enters here again. Subsequent files are created in the next block, below...
                if (fileCreate) {
                    sbc = createFile(id, tempPath, uploadedFiles[fileIdx].getName());
                    fileCreate = !fileCreate;
                }
                //--------------

                //This loop writes files to disk. When one file is done, the next one is immediatelly created. Loop is broken when the buffer is depleted, at which point execution continues...
                while (bb.hasRemaining()) {
                    if (bb.remaining() + fileSizeTrack >= uploadedFiles[fileIdx].getSize()) {
                        byte[] tempp = new byte[Long.valueOf(uploadedFiles[fileIdx].getSize() - fileSizeTrack).intValue()];
                        bb.get(tempp);
                        fileSizeTrack = 0;
                        sbc.write(ByteBuffer.wrap(tempp));
                        sbc.close();
                        if (!(uploadedFiles.length == fileIdx + 1)) {
                            sbc = createFile(id, tempPath, uploadedFiles[++fileIdx].getName());
                        }
                    } else {
                        fileSizeTrack += bb.remaining();
                        sbc.write(bb);
                    }
                }
                // --------------

                //notification back to client happens here
                postback.sendBinary(ByteBuffer.wrap(String.format("{\"p\":%.2f}", getPerc(totalTransferSize, totalFileSize)).getBytes()));
                
                //this buffer is all written out, so reset it, and on next cycle fill it up again...
                bb.clear();

                //if the last flag is met, all data is transferred. Then client will receive the last message that will contain ID string of this transaction - which is just a directory name, that will be sent back to the server and more specifically - WSUploadUIRenderer's decode method.
                if (last) {
                    postback.sendBinary(ByteBuffer.wrap(String.format("{\"f\":true, \"cnt\":%d}", uploadedFiles.length).getBytes()));
                    upload.complete(uploadedFiles);
                    UploaderEndpoint.closeWebSocketSession(thisSession, CloseCodes.NORMAL_CLOSURE, Messages.WS_sessionTransferComplete);
                }
                // --------------

            } while(frag.hasRemaining() && !last);

        } catch (Exception ex) {
            SessionRegistry.cleanFiles(tempPath, id);
            try {
                upload.completeExceptionally(ex);
                if (sbc != null && sbc.isOpen()) sbc.close();
                UploaderEndpoint.closeWebSocketSession(thisSession, CloseCodes.UNEXPECTED_CONDITION, Messages.WS_sessionFailTransfer);
            } catch (IOException exx) {
                exx.printStackTrace();
            }
            ex.printStackTrace();
        }
    }

    private static FileModel[] deserializeFileList(byte[] buff) {
        try {
            Charset cs = StandardCharsets.UTF_8;
            return JSON.fromJson(new String(cs.decode(ByteBuffer.wrap(buff)).array()), FileModel[].class);
        } catch (JsonbException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private static double getPerc(long a, long b) {
        return BigDecimal.valueOf(a).divide(BigDecimal.valueOf(b), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100, 0)).doubleValue();
    }

    private static SeekableByteChannel createFile(String id, String path, String name, StandardOpenOption... opts) throws IOException {
        Path dirPath = Paths.get(path, id);
        
        if (!Files.exists(dirPath)) dirPath = Files.createDirectory(dirPath);
        Path file = Files.createFile(dirPath.resolve(name));
        if (opts.length <= 0) return Files.newByteChannel(file, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        else return Files.newByteChannel(file, opts);
    }
}