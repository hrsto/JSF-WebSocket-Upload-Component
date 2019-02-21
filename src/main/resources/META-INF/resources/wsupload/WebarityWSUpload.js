var WebarityWSUploader = (function () {

    var defaultListenerErrMessage = (listener) => console.error(`No ${listener} listener defined.`);
    var isUploadSupported = () => window.File && window.FileReader && window.FileList && window.Blob && window.WebSocket && window.location && window.TextDecoder;

    var syntheticChangeEvent = document.createEvent("Event");
    syntheticChangeEvent.initEvent("change", true, true);

    var td = new TextDecoder("utf-8", {fatal:true});

    var label;
    var dropZoneID;
    var statusElement; //the <span> that display messages
    var dat;
    var address;
    var ws;

    var _onProgress, _onStart, _onSuccess, _onFail; //client supplied callbacks

    var text;

    var timedMessage = (label, bg, secondsDelay) => {
        setTimeout(() => {
            statusElement.textContent = label;
            dropZoneID.style.background = bg;
        }, secondsDelay * 1000);
    }
    

    var inputElement;

    var callbacks = {
        wsOnOpen: (e) => {
            WebarityWSUploader.ws = ws;

            ws.send(new Blob(dat));
        },
        wsOnMessage: (e) => {
            new Response(e.data).arrayBuffer().then(evt => {
                var socketResponse = JSON.parse(td.decode(evt));
                if (socketResponse.f) { //final message, upload complete
                    var count = socketResponse.cnt;
                    statusElement.textContent =  count > 1 ? text.HTML_filesUploaded.replace('{0}', count) : text.HTML_fileUploaded.replace('{0}', count);
                    timedMessage(label, '', 5);

                    if (typeof _onSuccess === 'function') _onSuccess(count);
                } else if (socketResponse.p) { //upload in progress
                    statusElement.textContent = text.HTML_percent.replace('{0}', socketResponse.p);
                    dropZoneID.style.background = `linear-gradient(90deg, rgb(46, 186, 253, 0.5) ${socketResponse.p}%, rgb(101, 152, 198, 0.5) ${socketResponse.p}%, rgba(0, 0, 0, 0) ${socketResponse.p + 0.5}%)`;

                    if (typeof _onProgress === 'function') _onProgress(socketResponse.p);
                } else if (!socketResponse.f && socketResponse.id) { //server has send an id string identifying this transfer session, upload has started
                    inputElement.value = socketResponse.id;
                    inputElement.dispatchEvent(syntheticChangeEvent);

                    if (typeof _onStart === 'function') _onStart();
                }
            });
        },
        wsOnError: (e) => {
            var msg = `${text.HTML_err_uploadFailed} ${e}:${e.reason}`;
            timedMessage(msg, '', 1);
            console.error(msg);
            if (typeof _onFail === 'function') _onFail(msg);
        },
        wsOnClose: (e) => {
            isOpen = false;
            if (e.wasClean) {
                console.log(`${text.HTML_connetion_closed} ${e.reason}`);
            } else {
                var msg = `${text.HTML_connection_err} ${e.code}: ${e.reason}`;
                timedMessage(msg, '', 1);
                console.error(msg);
            }
        },
    }

    return {

        init: (wsAddr, onProgress, onStart, onSuccess, onFail, txt, l, inpID, dzID, ctxRoot) => {

            text = txt;

            if (!isUploadSupported) throw text.HTML_wsNotSupported;

            dropZoneID = document.getElementById(dzID);
            inputElement = document.getElementById(inpID);
            statusElement = dropZoneID.getElementsByTagName('span')[0];
            label = l;

            _onProgress = onProgress;
            _onStart = onStart;
            _onSuccess = onSuccess;
            _onFail = onFail;

            var host = location.host;
            var protocol = location.protocol.startsWith("http") ? (location.protocol.endsWith('s:') ? 'wss' : 'ws') : "";
            if (protocol == "") return;
            if (ctxRoot) {
                address = protocol.concat('://').concat(host).concat(ctxRoot).concat(wsAddr).concat('?');
            } else {
                address = protocol.concat('://').concat(host).concat(wsAddr).concat('?');
            }

            dropZoneID.ondragover = e => {
                e.preventDefault();
                dropZoneID.classList.add('dragOver');
            };
            dropZoneID.ondrop = e => {
                e.preventDefault();
                dropZoneID.classList.remove('dragOver');
                WebarityWSUploader.send(e.dataTransfer.files);
            };
            dropZoneID.ondragleave = e => {
                e.preventDefault();
                dropZoneID.classList.remove('dragOver');
            };
        },
        send: (files) => {
            var query = JSON.stringify(Array.from(files, f => (
                {
                    'name':f.name,
                    'size':f.size,
                    'type':f.type,
                    'lastMod':f.lastModified
                }
            )));
            var header = new Blob([query], {type: "application/json"});

            dat = Array.from(files, f => f.slice());
            dat.unshift(header);

            ws = new WebSocket(address.concat(`headerSize=${header.size}`));
            ws.binaryType = "blob";

            ws.onopen = callbacks.wsOnOpen;
            ws.onmessage = callbacks.wsOnMessage;
            ws.onerror = callbacks.wsOnError;
            ws.onclose = callbacks.wsOnClose;
        }
    };
})();