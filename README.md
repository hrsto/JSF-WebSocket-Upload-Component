# WebSocket upload JSF Module

JSF component that enable mass file upload over a binary WebSocket connection.

## Features:
* Multiple files upload via drag and drop
* Dynamically set via EL:
    * destination path - 
    * max upload size (total) - per a single bulk upload
    * intermediary buffer size - in memory buffer to limit writes to disk
* User supplied JavaScript callbacks for:
    * `onProgress` - executed during the upload with percentage, `instanceId`, and `transferId` passed as arguments
    * `onStart` - before upload starts, this will be called and is going to be passed the upload ID string along with  `instanceId`, and `transferId` passed as arguments
    * `onSuccess` - after successful upload, this is called and the number of uploaded files, `instanceId`, and `transferId` passed as arguments
    * `onFail` - if upload fails, this is called and the fail message and `instanceId`, and `transferId` passed as arguments

    `instanceId` means the current element. I.e. if there are multiple drap and drop uploaded elements on the same page, they will have unique `instanceId`. This paramter is used internally to supply different arguments (like path, max upload size, buffer size, etc...) to the different instance of the component within the same session.
    
    `transferId` - unique id for transfer. Internally a subdir to the main upload directory and there are the files ultimately put. This subdir is named after `transferId`.

* Backing bean receives a `List<Path>` on successful upload
* Automatic clean up on failed or unclaimed uploads
* Easily localizable
* supports `<f:ajax/>`

## Usage:

In the facelets page import the namespace `https://www.webarity.com/custom-comps/ws-upload`

```html
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html lang="#{siteLocales.getCurrentLocale().getLanguage()}" xmlns="http://www.w3.org/1999/xhtml" xmlns:h="http://xmlns.jcp.org/jsf/html" xmlns:f="http://xmlns.jcp.org/jsf/core" xmlns:ui="http://java.sun.com/jsf/facelets" xmlns:h5a="http://xmlns.jcp.org/jsf/passthrough" xmlns:h5e="http://xmlns.jcp.org/jsf"
xmlns:ccs="https://www.webarity.com/custom-comps/aceui"
xmlns:cb="https://www.webarity.com/custom-behaviors/autocom"
xmlns:wsu="https://www.webarity.com/custom-comps/ws-upload">
...
</html>
```

Then use the component:

```html
<h:form>
    <wsu:WSUpload maxUploadSize="${1000 * 1000 * 1500}" wsBufferSize="${1000 * 1000 * 30}" value="#{someBean.files}" filePath="#{someBean.filePath}" />
    ...
</h:form>
```

There is support for ajax, but the tag is purely optional. The component essentially behaves like UIInput so it must be put inside a h:form element.

File upload is triggered immediately. If user clicks submit while file still uploads, then the request will be blocked until the file completes uploading. If file is uploaded, but user never hits submit, the file/s will be deleted when the session is destroyed by the server. After the setter method of your bean is hit with the list of uploaded files, it's up to the user to determine what will happen next; the component will simply forget about the files.

## Attributes:

* `ctxRoot` - In cases where the app is running from a different context root than `/`, you will need to set  this. Example:
    * for `http://192.168.0.1:8080/my-contact-page` : context root is `/`, thus no need to set this
    * for `http://192.168.0.1:8080/mySite/my-contact-page` : context root is `mySite` and this attibute must be set to `/mySite`
* `wsBufferSize` - in bytes, defaults to 500KB; memory buffer used to limit number of writes to disk. Set to higher if expecting large files
* `label` - defaults to `Drop files here.`
* `styleClass` - defaults to `ws_upld_style`; the html css class for this element
* `style` - embedded css rules for the element
* `maxUploadSize` - in bytes, defaults 5MB; currently, on Wildfly 15 and JSF 2.3, it will crash if over 2Gigs are uploaded (behavior may vary)
* `filePath` - defaults to `System.getenv("temp")`; where the files are stored. Each upload session will create a unique directory with the same name as the upload session ID string and place the files there
* rendered - defaults to `true`; if set to false, component will not show up and no uploads will be allowed
* `onProgress`, `onStart`, `onSuccess`, `onFail` - user supplied JavaScript callbacks

Attributes support EL.

## Socket address:

The system property `com.webarity.wsupload.endpointaddr` can be set to a custom websocket address. The WS address is constructed as follows:

    ws://<some host>:<some port>/<System.getProperty("com.webarity.wsupload.endpointaddr")>

It defaults to `com.webarity.upload`.


## Localization

It's done quite easily. Go to `src\main\resources\localization\com\webarity\wsUpload` and copy the root bundle. Rename it to something like `WSUploadUI_fr_FR.properties` and translate the text to French. Edit `src\main\resources\META-INF\faces-config.xml`: `<application>` -> `<locale-config>` -> add a `<supported-locale>` element for the new language.

---

## Prerequisites
* Java EE8
* Run in CDI container
* JDK >= 10
* JSF >= 2.3
* Maven >= 3.5.x

## Running
* mvn clean package
* copy `jar` file to `WEB-INF/lib/` for `.war` deployments
* copy `jar` file to `/lib/` for `.ear` deployments

## Tested on
* WildFly 15 with jre v11.0.2

---

https://www.webarity.com
