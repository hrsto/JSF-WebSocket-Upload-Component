package com.webarity.wsUpload;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

import javax.faces.component.UIComponent;
import javax.faces.component.UIInput;
import javax.faces.component.UIOutput;
import javax.faces.component.behavior.ClientBehaviorHolder;
import javax.faces.context.FacesContext;
import javax.faces.event.AbortProcessingException;
import javax.faces.event.ComponentSystemEvent;
import javax.faces.event.ListenerFor;
import javax.faces.event.PostAddToViewEvent;

/**
 * <p>When user drop a file, upload is triggered right away. If user hits the submit button at that moment, the request handler thread will block on the {@link WSUploadUIRenderer#decode(FacesContext, UIComponent)} method until that upload completes or fails.</p>
 */
@ListenerFor(systemEventClass=PostAddToViewEvent.class)
public class WSUploadUI extends UIInput implements ClientBehaviorHolder {

    protected static final String DEFAULT_CLASSNAME = "ws_upld_style";
    private static Long DEFAULT_UPLOAD_SIZE = 1000 * 1000 * 5L; // 5 megs
    private static Integer DEFAULT_SOCKET_BUFFER_SIZE = 1000 * 500; // 500 KB
    private static final List<String> SUPPORTED_EVENTS = Arrays.asList("change");
    protected static final String DEFAULT_PATH = System.getenv("temp");

    private static enum Props {
        label, styleClass, style, maxUploadSize, wsBufferSize, filePath, rendered, onProgress, onStart, onSuccess, onFail, ctxRoot, thisInstanceId;
    }

    public String getLabel() {
        return (String) getStateHelper().eval(Props.label);
    }

    public void setLabel(String label) {
        getStateHelper().put(Props.label, label);
    }

    public String getThisInstanceId() {
        return (String) getStateHelper().eval(Props.thisInstanceId);
    }

    public void setThisInstanceId(String thisInstanceId) {
        getStateHelper().put(Props.thisInstanceId, thisInstanceId);
    }

    public String getCtxRoot() {return (String) getStateHelper().eval(Props.ctxRoot);}
    public void setCtxRoot(String ctxRoot) {
        String _c = ctxRoot;

        if (_c != null && !_c.isEmpty()) {
            _c = _c.startsWith("/") ? _c : "/".concat(_c);
        }
        getStateHelper().put(Props.ctxRoot, _c);
    }

    //user defined JavaScipt callbacks
    public String getOnProgress() {return (String) getStateHelper().eval(Props.onProgress);}
    public void setOnProgress(String onProgress) {getStateHelper().put(Props.onProgress, onProgress);}
    public String getOnStart() {return (String) getStateHelper().eval(Props.onStart);}
    public void setOnStart(String onStart) {getStateHelper().put(Props.onStart, onStart);}
    public String getOnSuccess() {return (String) getStateHelper().eval(Props.onSuccess);}
    public void setOnSuccess(String onSuccess) {getStateHelper().put(Props.onSuccess, onSuccess);}
    public String getOnFail() {return (String) getStateHelper().eval(Props.onFail);}
    public void setOnFail(String onFail) {getStateHelper().put(Props.onFail, onFail);}

    public Boolean getRendered() {
        return Optional.ofNullable((Boolean) getStateHelper().eval(Props.rendered)).orElse(true);
    }

    public void setRendered(Boolean rendered) {
        getStateHelper().put(Props.rendered, rendered);
    }

    public Long getMaxUploadSize() {
        return Optional.ofNullable((Long) getStateHelper().eval(Props.maxUploadSize)).orElse(DEFAULT_UPLOAD_SIZE);
    }

    public void setMaxUploadSize(Long maxUploadSize) { //FIXME: large files (> 2 gigs) failed to upload with some error...
        getStateHelper().put(Props.maxUploadSize, maxUploadSize);
    }

    public String getFilePath() {
        return Optional.ofNullable((String) getStateHelper().eval(Props.filePath)).orElse(DEFAULT_PATH);
    }

    public void setFilePath(String filePath) {
        getStateHelper().put(Props.filePath, filePath);
    }

    public Integer getWsBufferSize() {
        return Optional.ofNullable((Integer) getStateHelper().eval(Props.wsBufferSize)).orElse(DEFAULT_SOCKET_BUFFER_SIZE);
    }

    public void setWsBufferSize(Integer wsBufferSize) {
        if (wsBufferSize < (DEFAULT_SOCKET_BUFFER_SIZE)) getStateHelper().put(Props.wsBufferSize, wsBufferSize);
        else getStateHelper().put(Props.wsBufferSize, wsBufferSize);
    }

    public String getStyleClass() {
        return (String) getStateHelper().eval(Props.styleClass);
    }

    public void setStyleClass(String styleClass) {
        getStateHelper().put(Props.styleClass, styleClass);
    }

    public String getStyle() {
        return (String) getStateHelper().eval(Props.style);
    }

    public void setStyle(String style) {
        getStateHelper().put(Props.style, style);
    }

    @Override
    public Collection<String> getEventNames() {
        return SUPPORTED_EVENTS;
    }

    @Override
    public String getDefaultEventName() {
        return "change";
    }

    @Override
    public String getFamily() {
        return "WebarityCustomComponents";
    }

    @Override
    public void processEvent (ComponentSystemEvent e) throws AbortProcessingException {
        FacesContext ctx = e.getFacesContext();
        if (getStyleClass() == null || (getStyleClass() != null && getStyleClass().isBlank())) {
            UIOutput cssOut = (UIOutput) ctx.getApplication().createComponent(UIOutput.COMPONENT_TYPE);
            cssOut.setRendererType(ctx.getApplication().getResourceHandler().getRendererTypeForResourceName("WebarityWSUpload.css"));
            cssOut.getAttributes().put("library", "wsupload");
            cssOut.getAttributes().put("name", "WebarityWSUpload.css");
            cssOut.getAttributes().put("target", "head");
            ctx.getViewRoot().addComponentResource(ctx, cssOut);
        }
        // super.processEvent(e);
    }

    /**
     * Used instead {@link UIComponent#getResourceBundleMap()} because couldn't get it to find the bundle. It would always return EMPTY_MAP because the bundle will be null.
     * @param l
     * @return
     */
    public static ResourceBundle getResourceBundle(Locale l) {
        return ResourceBundle.getBundle(String.format("localization.%s", WSUploadUI.class.getName()), l);
    }
}