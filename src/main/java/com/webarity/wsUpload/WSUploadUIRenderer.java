package com.webarity.wsUpload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.faces.application.FacesMessage;
import javax.faces.application.ResourceDependencies;
import javax.faces.application.ResourceDependency;
import javax.faces.component.UIComponent;
import javax.faces.component.behavior.ClientBehaviorContext;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseWriter;
import javax.faces.convert.ConverterException;
import javax.faces.render.Renderer;
import javax.servlet.http.HttpSession;

import com.webarity.wsUpload.websocket.BinaryMessageHandler;
import com.webarity.wsUpload.websocket.CompletableFutureUpload;
import com.webarity.wsUpload.websocket.Config;
import com.webarity.wsUpload.websocket.SessionRegistry;

/**
 * <p>
 * That static block initializes the endpoint address. Can be customized by a
 * System property keyed to {@code com.webarity.wsupload.endpointaddr}. If such
 * is not provided, a default one is given - {@code /com.webarity.upload}
 * </p>
 */
@ResourceDependencies({ @ResourceDependency(library = "wsupload", name = "WebarityWSUpload.js", target = "head") })
public class WSUploadUIRenderer extends Renderer {

    private static String ENDPOINT_ADDR;
    public static final AtomicBoolean ENDPOINT_STATE = new AtomicBoolean(false);

    @Override
    public void encodeEnd(FacesContext ctx, UIComponent comp) throws IOException {
        WSUploadUI c = (WSUploadUI) comp;

        if (!c.isRendered()) return;

        ResourceBundle txt = WSUploadUI.getResourceBundle(ctx.getViewRoot().getLocale());

        if (!ENDPOINT_STATE.get()) {
            ctx.addMessage(c.getClientId(), new FacesMessage(FacesMessage.SEVERITY_FATAL, txt.getString(Messages.ERROR_WebSocketNotOpen.getVal()), ""));
            return;
        }

        Path rootPath = Paths.get(c.getFilePath());
        if (!Files.isWritable(rootPath)) {
            ctx.addMessage(c.getClientId(), new FacesMessage(FacesMessage.SEVERITY_FATAL, txt.getString(Messages.ERROR_pathNotAccessible.getVal()), ""));
            return;
        }

        String label = Optional.ofNullable(c.getLabel()).orElse(txt.getString(Messages.COMMON_dropHere.getVal()));

        ctx.setProcessingEvents(true);
        ResponseWriter w = ctx.getResponseWriter();
        w.startElement("div", c);
        String dropZoneID = String.format("drop-zone:%s", c.getClientId());
        w.writeAttribute("id", dropZoneID, null);

        if (c.getStyle() != null)
            w.writeAttribute("style", c.getStyle(), "style");

        if (c.getStyleClass() != null && !c.getStyleClass().isBlank()) {
            w.writeAttribute("class", c.getStyleClass(), "class");
        } else {
            w.writeAttribute("class", WSUploadUI.DEFAULT_CLASSNAME, "class");
        }

        w.startElement("span", c);
        w.writeText(label, null);
        w.endElement("span");

        c.getClientBehaviors().entrySet().stream().filter(entry -> c.getEventNames().contains(entry.getKey()))
                .forEach(entry -> {
                    ClientBehaviorContext cbCtx = ClientBehaviorContext.createClientBehaviorContext(ctx, c,
                            entry.getKey(), c.getClientId(), null);
                    entry.getValue().stream().forEach(cb -> {
                        try {
                            w.writeAttribute(String.format("on%s", entry.getKey()), cb.getScript(cbCtx), null);
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    });
                });

        w.endElement("div");

        HttpSession tempHttpSession = (HttpSession) ctx.getExternalContext().getSession(false);
        if (tempHttpSession == null) {
            ctx.addMessage(c.getClientId(), new FacesMessage(FacesMessage.SEVERITY_FATAL, txt.getString(Messages.ERROR_noSession.getVal()), ""));
            return;
        }

        @SuppressWarnings({"unchecked"})
        Map<String, Map<String, Object>> compInstanceConfMap = (Map<String, Map<String, Object>>)tempHttpSession.getAttribute(Config.COMPONENT_INSTANCES);
        if (compInstanceConfMap == null) {
            compInstanceConfMap = new HashMap<>();
            tempHttpSession.setAttribute(Config.COMPONENT_INSTANCES, compInstanceConfMap);
        }
        Map<String, Object> compInstance = compInstanceConfMap.get(c.getClientId());
        if (compInstance == null) {
            compInstance = new HashMap<>();
            compInstanceConfMap.put(c.getClientId(), compInstance);
        }
        compInstance.put(Config.MAX_SIZE_PARAM, c.getMaxUploadSize());
        compInstance.put(Config.WS_BUFFER_SIZE, c.getWsBufferSize());
        compInstance.put(Config.WS_TEMP_FILE_PATH, c.getFilePath());

        SessionRegistry.whitelistSession(ctx.getExternalContext().getSessionId(false));

        tempHttpSession.setAttribute(Config.WS_RESOUCE_BUNDLE, txt);
        

        w.startElement("input", c);
        w.writeAttribute("type", "text", "type");
        w.writeAttribute("hidden", true, null);
        String fileInputElemID = c.getClientId();
        w.writeAttribute("name", fileInputElemID, null);
        w.writeAttribute("id", fileInputElemID, null);
        w.endElement("input");

        w.startElement("script", c);

        w.writeText(String.format("new WebarityWSUploader('%s', %s, %s, %s, %s, %s, '%s', '%s', '%s', '%s', '%s');",
                getEndpointAddress(), c.getOnProgress(), c.getOnStart(), c.getOnSuccess(), c.getOnFail(), renderLocalizedStrings(txt, ctx.getViewRoot().getLocale()), label,
                fileInputElemID, dropZoneID, c.getCtxRoot(), c.getClientId()), null);

        w.endElement("script");

        // w.close(); FIXME: some kind of a conflict with ajax - would show emptyResponse - An empty response was received from the server.  Check server error logs.
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public Object getConvertedValue(FacesContext ctx, UIComponent comp, Object submittedValue) throws ConverterException {
        return (List<Path>)submittedValue;
    }

    /**
     * <p>{@link BinaryMessageHandler}'s constructor will register an upload session right away and associate it with a {@link CompletableFutureUpload}. Decode method's logic is to get the CompletableFutureUpload transfer object by using the current session ID and transfer ID . The Decode method will then call the .get() method, which will block until the WebSocket upload completes.</p>
     * <p>Transfer ID is set via BinaryMessageHandler in the contructor by sending it as postback to the client, which will listen for incoming messages of various kinds. For this particular message, it will set the ID for element that carries that request parameter.</p>
     * <p>If a transfer is successfuly retrieved and set as the submitted value, it will be removed from the list of transfers.</p>
     */
    @Override
    public void decode(FacesContext ctx, UIComponent comp) {
        WSUploadUI c = (WSUploadUI) comp;
        String transferID = ctx.getExternalContext().getRequestParameterMap().get(c.getClientId());

        Map<String, String> msgs = ctx.getViewRoot().getResourceBundleMap();
        List<CompletableFutureUpload> transfers = Optional.ofNullable(SessionRegistry.getTransfers(ctx.getExternalContext().getSessionId(false))).orElseGet(() -> Collections.emptyList());

        int idx = transfers.indexOf(new CompletableFutureUpload(transferID, null));

        if (idx != -1) {
            try {
                c.setSubmittedValue(transfers.get(idx).get());
                transfers.remove(idx);
            } catch (InterruptedException | ExecutionException ex) {
                ex.printStackTrace();
                SessionRegistry.cleanFiles(c.getFilePath(), transferID);
                ctx.addMessage(c.getClientId(), new FacesMessage(FacesMessage.SEVERITY_ERROR, msgs.get(Messages.ERROR_failUpload.getVal()), msgs.get(Messages.ERROR_failUpload.getVal())));
                throw new ConverterException(String.format("%s: %s", msgs.get(Messages.ERROR_failUpload.getVal()), msgs.get(Messages.ERROR_failUpload.getVal())));
            }
        } else {
            c.setSubmittedValue(null);
        }
    }

    public static final String getEndpointAddress() {
        return ENDPOINT_ADDR;
    }

    /**
     * <p>Encode bundle keys for the HTML side of things as a JavaScript Object; this is used as a form of localization. JavaScipt will get these messages via: {@code text.HTML_connection_err} where "text." is the JavaScript object to this this was assigned.</p>
     * <p>{@code Messages.HTML_connection_err.getVal()} will return the actual String that is the key to the resource bundle and it's used strictly from Java.</p>
     * @param rb
     * @param l
     * @return
     */
    private static String renderLocalizedStrings(ResourceBundle rb, Locale l) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        sb.append(strinfigy(Messages.HTML_connection_err, rb.getString(Messages.HTML_connection_err.getVal()), l));
        sb.append(strinfigy(Messages.HTML_connetion_closed, rb.getString(Messages.HTML_connetion_closed.getVal()), l));
        sb.append(strinfigy(Messages.HTML_err_uploadFailed, rb.getString(Messages.HTML_err_uploadFailed.getVal()), l));
        sb.append(strinfigy(Messages.HTML_fileUploaded, rb.getString(Messages.HTML_fileUploaded.getVal()), l));
        sb.append(strinfigy(Messages.HTML_filesUploaded, rb.getString(Messages.HTML_filesUploaded.getVal()), l));
        sb.append(strinfigy(Messages.HTML_percent, rb.getString(Messages.HTML_percent.getVal()), l));
        sb.append(strinfigy(Messages.HTML_wsNotSupported, rb.getString(Messages.HTML_wsNotSupported.getVal()), l));

        sb.append("}");
        return sb.toString();
    }
    private static String strinfigy(Messages key, String val, Locale l) {
        return String.format(l, "%s:\"%s\",", key, val);
    }

    static {
        Optional.ofNullable(System.getProperty("com.webarity.wsupload.endpointaddr")).ifPresentOrElse(
                addr -> ENDPOINT_ADDR = addr.startsWith("/") ? addr : "/".concat(addr),
                () -> ENDPOINT_ADDR = "/com.webarity.upload");
    }
}