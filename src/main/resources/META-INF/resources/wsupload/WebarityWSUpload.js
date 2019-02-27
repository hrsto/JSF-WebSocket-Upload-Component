function WebarityWSUploader(wsAddr, onProgress, onStart, onSuccess, onFail, txt, l, inpID, dzID, ctxRoot, instance) {
    this.dat;
    this.address;
    this.ws;

    this.instance = instance; //If we want to keep files uniquely separated for each instance of this component, we use this identifier, which will translate to a specific child dir under the main dir.

    //client supplied callbacks
    this._onProgress = onProgress;
    this._onStart = onStart;
    this._onSuccess = onSuccess;
    this._onFail = onFail;

    this.text = txt;

    if (!this.isUploadSupported) throw text.HTML_wsNotSupported;

    this.dropZoneID = document.getElementById(dzID);
    this.inputElement = document.getElementById(inpID);
    this.statusElement = this.dropZoneID.getElementsByTagName('span')[0]; //the <span> that display messages
    this.label = l;

    this.host = location.host;
    this.protocol = location.protocol.startsWith("http") ? (location.protocol.endsWith('s:') ? 'wss' : 'ws') : "";
    if (this.protocol == "") return;
    if (ctxRoot) {
        this.address = this.protocol.concat('://').concat(this.host).concat(ctxRoot).concat(wsAddr).concat('?');
    } else {
        this.address = this.protocol.concat('://').concat(this.host).concat(wsAddr).concat('?');
    }

    this.dropZoneID.ondragover = e => {
        e.preventDefault();
        this.dropZoneID.classList.add('dragOver');
    };
    this.dropZoneID.ondrop = e => {
        e.preventDefault();
        this.dropZoneID.classList.remove('dragOver');
        this.initiateTransfer(e.dataTransfer.files);
    };
    this.dropZoneID.ondragleave = e => {
        e.preventDefault();
        this.dropZoneID.classList.remove('dragOver');
    };
}

WebarityWSUploader.prototype = {
    constructor: WebarityWSUploader,
    
    td: new TextDecoder("utf-8", {fatal:true}),
    
    defaultListenerErrMessage: (listener) => console.error(`No ${listener} listener defined.`),   
    
    isUploadSupported: function() {
        return window.File && window.FileReader && window.FileList && window.Blob && window.WebSocket && window.location && window.TextDecoder;
    },   
    
    syntheticChangeEvent: (function() {
        var temp = document.createEvent("Event");
        temp.initEvent("change", true, true);
        return temp;
    })(),

    timedMessage: function(label, bg, secondsDelay) {
        setTimeout(() => {
            this.statusElement.textContent = label;
            this.dropZoneID.style.background = bg;
        }, secondsDelay * 1000);
    },

    initiateTransfer: function(files) {
        var query = JSON.stringify(Array.from(files, f => (
            {
                'name':f.name,
                'size':f.size,
                'type':f.type,
                'lastMod':f.lastModified
            }
        )));
        var header = new Blob([query], {type: "application/json"});

        this.dat = Array.from(files, f => f.slice());
        this.dat.unshift(header);

        this.ws = new WebSocket(this.address.concat(`headerSize=${header.size}&id=${this.instance}`));
        this.ws.binaryType = "blob";

        this.ws.onopen = (e) => {
            this.ws.send(new Blob(this.dat));
        }
        this.ws.onmessage = (e) => {
            new Response(e.data).arrayBuffer().then(evt => {
                var socketResponse = JSON.parse(this.td.decode(evt));
                if (socketResponse.f) { //final message, upload complete
                    var count = socketResponse.cnt;
                    this.statusElement.textContent =  count > 1 ? this.text.HTML_filesUploaded.replace('{0}', count) : this.text.HTML_fileUploaded.replace('{0}', count);
                    this.timedMessage(this.label, '', 5);
    
                    if (typeof this._onSuccess === 'function') this._onSuccess(count, this.instance, this.inputElement.value);
                } else if (socketResponse.p) { //upload in progress
                    this.statusElement.textContent = this.text.HTML_percent.replace('{0}', socketResponse.p);
                    this.dropZoneID.style.background = `linear-gradient(90deg, rgb(46, 186, 253, 0.5) ${socketResponse.p}%, rgb(101, 152, 198, 0.5) ${socketResponse.p}%, rgba(0, 0, 0, 0) ${socketResponse.p + 0.5}%)`;
    
                    if (typeof this._onProgress === 'function') this._onProgress(socketResponse.p, this.instance, this.inputElement.value);
                } else if (!socketResponse.f && socketResponse.id) { //server has send an id string identifying this transfer session, upload has started
                    this.inputElement.value = socketResponse.id;
                    this.inputElement.dispatchEvent(this.syntheticChangeEvent);
    
                    if (typeof this._onStart === 'function') this._onStart(this.instance);
                }
            });
        }
        this.ws.onerror = (e) => {
            var msg = `${this.text.HTML_err_uploadFailed} ${e}:${e.reason}`;
            this.timedMessage(msg, '', 1);
            this.timedMessage(this.label, '', 8);
            console.error(msg);
            if (typeof this._onFail === 'function') _onFail(msg, this.instance, this.inputElement.value);
        }
        this.ws.onclose = (e) => {
            if (e.wasClean) {
                console.log(`${this.text.HTML_connetion_closed} ${e.reason}`);
            } else {
                var msg = `${this.text.HTML_connection_err} ${e.code}: ${e.reason}`;
                this.timedMessage(msg, '', 1);
                this.timedMessage(this.label, '', 8);
                console.error(msg);
            }
        }
    }
}