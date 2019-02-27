package org.swissbib.srw;

import com.mongodb.client.MongoCollection;
import org.apache.axiom.om.*;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.description.Parameter;
import org.bson.Document;
import org.swissbib.srw.serialization.CBSDataConsumer;
import org.swissbib.srw.serialization.KafkaConsumer;
import org.swissbib.srw.serialization.PipeData;

import javax.xml.namespace.QName;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Pattern;


/**
 * [...description of the type ...]
 * <p/>
 * <p/>
 * <p/>
 * Copyright (C) project swissbib, University Library Basel, Switzerland
 * http://www.swissbib.org  / http://www.swissbib.ch / http://www.ub.unibas.ch
 * <p/>
 * Date: 6/20/16
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 * <p/>
 * license:  http://opensource.org/licenses/gpl-2.0.php GNU General Public License
 *
 * @author Guenter Hipler  <guenter.hipler@unibas.ch>
 * @link http://www.swissbib.org
 * @link https://github.com/swissbib/srwMessageCatcher
 */
public class UpdateImplementation {

    private static Pattern pDeleteAction =   Pattern.compile(SRWUpdateService.SRUActions.delete.getValue(),Pattern.CASE_INSENSITIVE);

    private OMElement originalUpdateMessage;
    private SRWUpdateService.UpdateType updateType;
    private String actionText;
    private String recordId;
    private SRWUpdateService.SRUActions action;
    private boolean transformRecord;
    private boolean mismatchLeaderDeleteMessageType = false;
    private String leaderTypeChar = "";
    private boolean processedDelayed = false;


    private final String CHECK_LEADER_FOR_DELETE = "checkLeaderForDelete";




    public UpdateImplementation (OMElement originalUpdateMessage, SRWUpdateService.UpdateType updateType) {

        this.originalUpdateMessage = originalUpdateMessage;
        this.updateType = updateType;
        this.actionText = getActionText();
        this.recordId = getRecordId();
        this.action = SRWUpdateService.SRUActions.fromString(this.actionText);
    }

    private String getRecordId() {
        return this.originalUpdateMessage.getFirstChildWithName(new QName(SRWUpdateService.SRUNamespaces.zingSRWupdate.getValue(),"recordIdentifier")).getText();
    }

    private String getActionText() {
        return this.originalUpdateMessage.getFirstChildWithName(new QName(SRWUpdateService.SRUNamespaces.zingSRWupdate.getValue(),"action")).getText();
    }



    public OMElement processMessage() {

        OMElement responseElement = null;
        MessageContext mc =  MessageContext.getCurrentMessageContext();
        this.transformRecord = this.updateType == SRWUpdateService.UpdateType.swissbib_classic ?
                Boolean.valueOf( mc.getAxisService().getParameter("transformClassicRecord").getValue().toString()) :
                Boolean.valueOf( mc.getAxisService().getParameter("transformRdfRecord").getValue().toString());


        try {
            OMElement srwRecord = this.originalUpdateMessage.getFirstChildWithName(new QName(SRWUpdateService.SRUNamespaces.zingSRW.getValue(), "record"));
            if (null != srwRecord) {
                OMElement srwRecordData = srwRecord.getFirstChildWithName(new QName(SRWUpdateService.SRUNamespaces.zingSRW.getValue(), "recordData"));

                if (null != srwRecordData) {
                    OMElement completeRecordOmElement = srwRecordData.getFirstChildWithName(new QName(SRWUpdateService.SRUNamespaces.marc21Slim.getValue(), "record"));


                    if (null != completeRecordOmElement) {

                        KafkaConsumer kc = KafkaConsumer.createConsumer(mc);


                        this.checkRecordForDelayedProcessing(completeRecordOmElement);
                        if (pDeleteAction.matcher(actionText).find() ||
                                (Boolean.valueOf(mc.getAxisService().getParameter(CHECK_LEADER_FOR_DELETE).getValue().toString())
                                        && checkLeaderForDelete(completeRecordOmElement))) {
                            serializeRecord(completeRecordOmElement, kc);
                            //we create a different responseElement for delete messages
                            //why? is this a commitment with OCLC (H.v.E) ??
                            responseElement = createDeleteResponse();
                        } else {


                            //for the swissbib classic procedure we serialize all the records into the updateDir
                            serializeRecord(completeRecordOmElement,kc);
                            responseElement = createUpdateReplaceResponse(completeRecordOmElement);

                        }
                        logMessages(completeRecordOmElement);
                    } else {

                        responseElement = createFailureResponse("replace or create message without complete record element", "info:srw/diagnostic/12/1");
                    }


                } else {
                    responseElement = createFailureResponse("replace or create message without record data", "info:srw/diagnostic/12/1");
                }


            }
        } catch (Throwable throwable) {
            //Todo: mach hier noch etwas
            throwable.printStackTrace();
        }



        return responseElement;

    }


    private String getOutputDir (SRWUpdateService.SRUActions action) {

        MessageContext mc =  MessageContext.getCurrentMessageContext();
        String outputDir = null;

        if (this.processedDelayed)
            outputDir = mc.getAxisService().getParameter(ApplicationConstants.PROCESSED_DELAYED_DIR.getValue()).getValue().toString();
        else {


            switch (action) {
                case create:
                    outputDir = mc.getAxisService().getParameter("createDir").getValue().toString();
                    break;
                case replace:
                    outputDir = mc.getAxisService().getParameter("updateDir").getValue().toString();
                    break;
                case delete:
                    outputDir = mc.getAxisService().getParameter("deleteDir").getValue().toString();
                    break;
            }
        }

        return outputDir;

    }

    private boolean checkLeaderForDelete(OMElement completeRecordOmElement) {

        OMElement leaderOME =  completeRecordOmElement.getFirstChildWithName(new QName(SRWUpdateService.SRUNamespaces.marc21Slim.getValue(),"leader"));
        String leaderChar = leaderOME != null ? leaderOME.getText().substring(5,6): "";
        this.mismatchLeaderDeleteMessageType = false;
        if (leaderChar.equalsIgnoreCase("d") && !this.actionText.equalsIgnoreCase(SRWUpdateService.SRUActions.delete.getValue())) {
            this.mismatchLeaderDeleteMessageType = true;
            this.actionText = SRWUpdateService.SRUActions.delete.getValue();
            this.action = SRWUpdateService.SRUActions.delete;
        }

        this.leaderTypeChar = leaderChar;
        return this.mismatchLeaderDeleteMessageType;
    }


    private void serializeRecord (OMElement completeRecord, CBSDataConsumer dataconsumer) throws Exception {



        final String RECORD_NS = "recordWithNamespaces";
        final String NORMALIZE_CHARS = "normalizeChars";

        MessageContext mc =  MessageContext.getCurrentMessageContext();


        StringWriter serializedRecord = new StringWriter();
        completeRecord.serialize(serializedRecord);


        //do we want the target record without namespaces?
        if (transformRecord && !Boolean.valueOf(mc.getAxisService().getParameter(RECORD_NS).getValue().toString())) {
            Source sourceWithNS = new StreamSource(new StringReader(serializedRecord.toString()));
            serializedRecord = new StringWriter();
            Result tempXsltResult = new StreamResult(serializedRecord);

            Parameter template = this.updateType == SRWUpdateService.UpdateType.linked_swissbib ?
                    mc.getAxisService().getParameter(ApplicationConstants.TRANSFORM_RDF_TEMPLATE.getValue()):
                    mc.getAxisService().getParameter(ApplicationConstants.TRANSFORM_CLASSIC_TEMPLATE.getValue());

            Templates recordTemplate  =  ((Templates) template.getValue());
            recordTemplate.newTransformer().transform(sourceWithNS,tempXsltResult);
        }

        //do we want to normalize the chars?
        if (Boolean.valueOf(mc.getAxisService().getParameter(NORMALIZE_CHARS).getValue().toString())) {

            //http://docs.oracle.com/javase/tutorial/i18n/text/normalizerapi.html

            serializedRecord = new StringWriter().append(Normalizer.normalize(serializedRecord.toString(), Normalizer.Form.NFC));
        }

        PipeData pd = new PipeData();
        pd.action = PipeData.Action.create;
        pd.body = serializedRecord.toString();
        pd.id = this.recordId;

        dataconsumer.accept(pd);

        //BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream( getFileForSerialization()),"UTF-8"));
        //bw.write(serializedRecord.toString());

        //bw.flush();
        //bw.close();


    }



    private File getFileForSerialization () {


        MessageContext mc = MessageContext.getCurrentMessageContext();
        Date currentDate = new Date();

        File recordFile = null;
        if (this.updateType == SRWUpdateService.UpdateType.swissbib_classic) {

            SimpleDateFormat exactHumanReadbleTime = new SimpleDateFormat("yyyy_MM_dd:HH_mm_ss.SS");
            //SimpleDateFormat exactHumanReadbleTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
            String timeFilePrefix = exactHumanReadbleTime.format(currentDate);
            recordFile = new File(getOutputDir(this.action) + timeFilePrefix + "_" +
                    mc.getAxisService().getParameter(ApplicationConstants.FILE_PREFIX.getValue()).getValue().toString() +
                    this.recordId +
                    mc.getAxisService().getParameter(ApplicationConstants.FILE_SUFFIX.getValue()).getValue().toString());
        } else {

            //SimpleDateFormat exactHumanReadbleTime = new SimpleDateFormat("yyyy_MM_dd:HH_mm_ss.SS");
            SimpleDateFormat exactHumanReadbleTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
            String timeFilePrefix = exactHumanReadbleTime.format(currentDate);
            //linked serialization file doesn't need the FilePrefix because it's only to improve
            //human readability
            recordFile = new File(getOutputDir(this.action) + timeFilePrefix + "_" +
                    this.recordId + "_" + this.action.name() +
                    mc.getAxisService().getParameter(ApplicationConstants.FILE_SUFFIX.getValue()).getValue().toString());

        }


        return recordFile;

    }


    private OMElement createDeleteResponse () {



        //method creates the content which is sent back to the client and child of soapenv:Body tag

        OMFactory fac = OMAbstractFactory.getOMFactory();

        OMNamespace nsUpdate = fac.createOMNamespace( SRWUpdateService.SRUNamespaces.zingSRWupdate.getValue(),"ns4");
        OMNamespace nsDiagnostic = fac.createOMNamespace(SRWUpdateService.SRUNamespaces.diagnostic.getValue(),"ns3");
        OMNamespace nsSRW = fac.createOMNamespace(SRWUpdateService.SRUNamespaces.zingSRW.getValue(),"ns5");
        OMNamespace nsXSI = fac.createOMNamespace(SRWUpdateService.SRUNamespaces.xmlSchemaInstance.getValue(),"xsi");

        OMElement responseElement = fac.createOMElement("updateResponse", nsUpdate);



        responseElement.declareNamespace(nsDiagnostic);
        responseElement.declareNamespace(nsSRW);

        fac.createOMElement("version",nsSRW,responseElement).setText("1.0");
        fac.createOMElement("operationStatus",nsSRW,responseElement).setText("success");
        fac.createOMElement("recordIdentifier",nsSRW,responseElement).setText(this.recordId);
        OMElement versionsElement = fac.createOMElement("recordVersions",nsXSI,responseElement);
        versionsElement.addAttribute("nil","true",nsXSI);
        OMElement recordElement = fac.createOMElement("record",nsSRW,responseElement);


        fac.createOMElement("recordSchema", nsSRW, recordElement).setText(SRWUpdateService.ResponseRecordMetaInfo.recordSchema.getValue());
        fac.createOMElement("recordPacking",nsSRW,recordElement).setText(SRWUpdateService.ResponseRecordMetaInfo.packaging.getValue());
        fac.createOMElement("recordData",nsSRW,recordElement);

        return responseElement;
    }

    /**
     *
     * @param completeRecord content of the SRW request
     * @return    content of the SOAP message (child tag of soapenv:Body)
     */
    private OMElement createUpdateReplaceResponse (OMNode completeRecord) {

        final String RECORD_IN_RESPONSE  = "includeRecordInResponse";

        //method creates the content which is sent back to the client and child of soapenv:Body tag

        OMFactory fac = OMAbstractFactory.getOMFactory();

        OMNamespace nsUpdate = fac.createOMNamespace( SRWUpdateService.SRUNamespaces.zingSRWupdate.getValue(),"ns4");
        OMNamespace nsDiagnostic = fac.createOMNamespace(SRWUpdateService.SRUNamespaces.diagnostic.getValue(),"ns3");
        OMNamespace nsSRW = fac.createOMNamespace(SRWUpdateService.SRUNamespaces.zingSRW.getValue(),"ns5");
        OMNamespace nsXSI = fac.createOMNamespace(SRWUpdateService.SRUNamespaces.xmlSchemaInstance.getValue(),"xsi");

        OMElement responseElement = fac.createOMElement("updateResponse", nsUpdate);
        responseElement.declareNamespace(nsDiagnostic);
        responseElement.declareNamespace(nsSRW);

        fac.createOMElement("version",nsSRW,responseElement).setText("1.0");
        fac.createOMElement("operationStatus",nsSRW,responseElement).setText("success");
        fac.createOMElement("recordIdentifier",nsSRW,responseElement).setText(this.recordId);
        OMElement versionsElement = fac.createOMElement("recordVersions",nsXSI,responseElement);
        versionsElement.addAttribute("nil","true",nsXSI);
        OMElement recordElement = fac.createOMElement("record",nsSRW,responseElement);

        fac.createOMElement("recordSchema", nsSRW, recordElement).setText(SRWUpdateService.ResponseRecordMetaInfo.recordSchema.getValue());
        fac.createOMElement("recordPacking",nsSRW,recordElement).setText(SRWUpdateService.ResponseRecordMetaInfo.packaging.getValue());

        OMElement recordData = fac.createOMElement("recordData",nsSRW,recordElement);

        if (Boolean.valueOf(MessageContext.getCurrentMessageContext().getAxisService().getParameter(RECORD_IN_RESPONSE).getValue().toString())) {
            recordData.addChild(completeRecord);
        }

        return responseElement;
    }


    private OMElement createFailureResponse ( String failureMessage, String uri) {


        //method creates the content which is sent back to the client and child of soapenv:Body tag

        OMFactory fac = OMAbstractFactory.getOMFactory();

        OMNamespace nsUpdate = fac.createOMNamespace( SRWUpdateService.SRUNamespaces.zingSRWupdate.getValue(),"ns4");
        OMNamespace nsDiagnostic = fac.createOMNamespace(SRWUpdateService.SRUNamespaces.diagnostic.getValue(),"ns3");
        OMNamespace nsSRW = fac.createOMNamespace(SRWUpdateService.SRUNamespaces.zingSRW.getValue(),"ns5");
        OMNamespace nsXSI = fac.createOMNamespace(SRWUpdateService.SRUNamespaces.xmlSchemaInstance.getValue(),"xsi");

        OMElement responseElement = fac.createOMElement("updateResponse", nsUpdate);
        responseElement.declareNamespace(nsDiagnostic);
        responseElement.declareNamespace(nsSRW);




        fac.createOMElement("version",nsSRW,responseElement).setText("1.0");
        fac.createOMElement("operationStatus",nsSRW,responseElement).setText("fail");
        fac.createOMElement("recordIdentifier",nsSRW,responseElement).setText(this.recordId);
        OMElement versionsElement = fac.createOMElement("recordVersions",nsXSI,responseElement);
        versionsElement.addAttribute("nil","true",nsXSI);

        OMElement diagnosticsElement = fac.createOMElement("diagnostics",null,responseElement);
        OMElement diagnosticElement = fac.createOMElement("diagnostic",nsDiagnostic,diagnosticsElement);
        fac.createOMElement("uri",null,diagnosticElement).setText(uri);
        if (null != failureMessage) {
            fac.createOMElement("message",null,diagnosticElement).setText(failureMessage);

        }



        return responseElement;
    }

    private void  logMessages (OMElement recordToLog) {

        if ( this.loggingForMessageTypeActivated()) {

            try {
                MessageContext mc = MessageContext.getCurrentMessageContext();
                @SuppressWarnings("unchecked") MongoCollection<Document> mongoCollection = (MongoCollection<Document>) mc.getAxisService().
                        getParameter(ApplicationConstants.ACTIVE_MONGO_COLLECTION.getValue()).getValue();

                //SimpleDateFormat simpleFormat = new SimpleDateFormat("yyyy-mm-dd hh:mm:ss");
                SimpleDateFormat simpleFormatDay = new SimpleDateFormat("yyyy-MM-dd");
                //SimpleDateFormat exactHumanReadbleTime = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss");
                SimpleDateFormat exactHumanReadbleTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

                Date currentDate = new Date();

                StringWriter serializedRecord = new StringWriter();

                boolean logCompleteRecord = this.updateType == SRWUpdateService.UpdateType.swissbib_classic ?
                        Boolean.valueOf(mc.getAxisService().getParameter(ApplicationConstants.LOG_COMPLETE_RECORD_CLASSIC.getValue()).getValue().toString()) :
                        Boolean.valueOf(mc.getAxisService().getParameter(ApplicationConstants.LOG_COMPLETE_RECORD_RDF.getValue()).getValue().toString());

                if (logCompleteRecord) {
                    Source sourceWithNS = new StreamSource(new StringReader(recordToLog.toString()));
                    Result tempXsltResult = new StreamResult(serializedRecord);
                    Parameter template = mc.getAxisService().getParameter("templateCreateMarcXml");
                    Templates recordTemplate = ((Templates) template.getValue());
                    recordTemplate.newTransformer().transform(sourceWithNS, tempXsltResult);
                    serializedRecord = new StringWriter().append(Normalizer.normalize(serializedRecord.toString(), Normalizer.Form.NFC));
                }


                Document doc = new Document().
                        append("id", this.recordId).
                        append("action", this.actionText).
                        append("marcStatus", this.leaderTypeChar).
                        append("messageMismatch", String.valueOf(this.mismatchLeaderDeleteMessageType)).
                        append("updateDay", simpleFormatDay.format(currentDate)).
                                append("timestamp", currentDate.getTime()).
                                append("record", serializedRecord.toString()).
                                append("readTime", exactHumanReadbleTime.format(currentDate));

                mongoCollection.insertOne(doc);
            } catch (Throwable thr) {
                System.out.println("Exception  trying to write log message into Mongo DB for id: " + this.recordId + " action: " + actionText);
                System.out.print(thr.getMessage());
            }


        }



    }

    private boolean loggingForMessageTypeActivated() {

        MessageContext mc =  MessageContext.getCurrentMessageContext();

        return this.updateType == SRWUpdateService.UpdateType.linked_swissbib &&
                Boolean.valueOf(mc.getAxisService().getParameter(ApplicationConstants.LOG_MESSAGES_LINKED.getValue()).getValue().toString()) ||
                this.updateType == SRWUpdateService.UpdateType.swissbib_classic &&
                        Boolean.valueOf(mc.getAxisService().getParameter(ApplicationConstants.LOG_MESSAGES_CLASSIC.getValue()).getValue().toString());


    }

    private void checkRecordForDelayedProcessing(OMElement recordToCheck) {

        MessageContext mc = MessageContext.getCurrentMessageContext();

        @SuppressWarnings("unchecked") HashMap<String, String> checkForDelay =
                (HashMap<String, String>) mc.getAxisService().getParameter(ApplicationConstants.PARSE_DELAYED_PROCESSING.getValue()).getValue();

        boolean isDelayed = Boolean.valueOf(checkForDelay.get("isDelayed"));

        if (isDelayed) {
            String code = checkForDelay.get("code");
            String tag = checkForDelay.get("tag");
            String fieldType = checkForDelay.get("fieldType");
            String textValue =  checkForDelay.get("value");

            Iterator iter = recordToCheck.getChildrenWithName(
                    new QName(SRWUpdateService.SRUNamespaces.marc21Slim.getValue(), fieldType));

            while (iter.hasNext()) {

                OMElement dataElement = (OMElement) iter.next();
                OMAttribute tagAttribute = dataElement.getAttribute(new QName("tag"));
                if (null != tagAttribute) {
                    if (tagAttribute.getAttributeValue().equalsIgnoreCase(tag)) {
                        Iterator iSubfields = dataElement.getChildrenWithName(new QName(SRWUpdateService.SRUNamespaces.marc21Slim.getValue(),
                                "subfield"));

                        while (iSubfields.hasNext()) {
                            OMElement subFieldElement = (OMElement) iSubfields.next();
                            OMAttribute subAttribute = subFieldElement.getAttribute(new QName("code"));
                            if (null != subAttribute && subAttribute.getAttributeValue().equalsIgnoreCase(code)) {
                                if (subFieldElement.getText().toLowerCase().contains(textValue.toLowerCase())) {
                                    this.processedDelayed = true;
                                    break;
                                }

                            }
                        }
                    }
                }


            }

        }
    }


}
