package org.swissbib.srw;

import  com.mongodb.client.MongoCollection;
import org.apache.axiom.om.*;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.description.Parameter;
import org.bson.Document;

import javax.xml.namespace.QName;
import javax.xml.transform.*;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Pattern;

/**
 * [...description of the type ...]
 * <p/>
 * <p/>
 * <p/>
 * Copyright (C) project swissbib, University Library Basel, Switzerland
 * http://www.swissbib.org  / http://www.swissbib.ch / http://www.ub.unibas.ch
 * <p/>
 * Date: 11/4/13
 * Time: 4:16 PM
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
 * @link https://github.com/swissbib/xml2SearchDoc
 */


public class SRWUpdateService {



    private enum SRUActions {
        delete("info:srw/action/1/delete"),
        replace("info:srw/action/1/replace"),
        create("info:srw/action/1/create");

        String action;
        SRUActions(String action) {
            this.action = action;
        }
        public String getValue() {
            return this.action;
        }

        public static SRUActions fromString(String text) {
            if (text != null) {
                for (SRUActions action : SRUActions.values()) {
                    if (text.equalsIgnoreCase(action.getValue())) {
                        return action;
                    }
                }
            }
            return null;
        }

    }


    private enum ResponseRecordMetaInfo {
        packaging("xml"),
        recordSchema("info:srw/schema/1/marcxml-v1.1");

        String metaInfo;
        ResponseRecordMetaInfo(String info) {
            this.metaInfo = info;
        }

        public String getValue() {
            return this.metaInfo;
        }
    }


    private enum SRUNamespaces {
        marc21Slim("http://www.loc.gov/MARC21/slim"),
        diagnostic("http://www.loc.gov/zing/srw/diagnostic/"), //prefix -> ns3
        zingSRWupdate("http://www.loc.gov/zing/srw/update/"), //prefix -> ns4
        xmlSchemaInstance("http://www.w3.org/2001/XMLSchema-instance"), //prefix -> xsi
        zingSRW("http://www.loc.gov/zing/srw/"); //prefix -> ns5


        String namespace;
        SRUNamespaces(String ns) {
            this.namespace = ns;
        }

        public String getValue() {
            return this.namespace;
        }
    }

    public OMElement updateRDF (OMElement record) {
        final String CHECK_LEADER_FOR_DELETE = "checkLeaderForDelete";

        OMElement responseElement = null;

        try {


            String idText = getRecordId(record);
            String actionText = getActionText(record);

            Pattern pDeleteAction =   Pattern.compile(SRUActions.delete.getValue(),Pattern.CASE_INSENSITIVE);


            OMElement srwRecord = record.getFirstChildWithName(new QName(SRUNamespaces.zingSRW.getValue(),"record"));
            if (null != srwRecord) {
                OMElement srwRecordData = srwRecord.getFirstChildWithName(new QName(SRUNamespaces.zingSRW.getValue(),"recordData"));

                if (null != srwRecordData) {
                    OMElement completeRecordOmElement= srwRecordData.getFirstChildWithName(new QName(SRUNamespaces.marc21Slim.getValue(), "record"));


                    if (null != completeRecordOmElement) {
                        MessageContext mc =  MessageContext.getCurrentMessageContext();
                        if ( pDeleteAction.matcher(actionText).find() ||  (Boolean.valueOf( mc.getAxisService().getParameter(CHECK_LEADER_FOR_DELETE).getValue().toString()) && checkLeaderForDelete(completeRecordOmElement))) {
                            //Todo: write log
                            //serializeDeleteRecord(idText,record);
                            serializeRecord(idText,completeRecordOmElement, getOutputDir(SRUActions.delete), true);
                            responseElement =  createDeleteResponse(idText);
                            //we create a different responseElement for delete messages
                            //why? is this a commitment with OCLC (H.v.E) ??
                        } else {



                            //for the swissbib classic procedure we serialize all the records into the updateDir
                            serializeRecord(idText,completeRecordOmElement, getOutputDir(SRUActions.fromString(actionText)), true);
                            responseElement = createUpdateReplaceResponse(idText, completeRecordOmElement);

                        }
                        logMessages(idText,actionText);
                    } else {

                        responseElement = createFailureResponse(idText,"replace or create message without complete record element","info:srw/diagnostic/12/1");
                    }


                } else {
                    responseElement = createFailureResponse(idText,"replace or create message without record data","info:srw/diagnostic/12/1");
                }


            }

        } catch (Exception except) {

            except.printStackTrace();

        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        return responseElement;


    }



    public OMElement update (OMElement record) {


        final String CHECK_LEADER_FOR_DELETE = "checkLeaderForDelete";

        OMElement responseElement = null;

        try {


            String idText = getRecordId(record);
            String actionText = getActionText(record);

            Pattern pDeleteAction =   Pattern.compile(SRUActions.delete.getValue(),Pattern.CASE_INSENSITIVE);

            if (pDeleteAction.matcher(actionText).find())  {
                //Todo: serialize Delete record
                //serializeDeleteRecord(idText,record);
                serializeRecord(idText,record, getOutputDir(SRUActions.fromString(actionText)), false);
                responseElement =  createDeleteResponse(idText);
                logMessages(idText,actionText);
            } else {

                OMElement srwRecord = record.getFirstChildWithName(new QName(SRUNamespaces.zingSRW.getValue(),"record"));
                if (null != srwRecord) {
                    OMElement srwRecordData = srwRecord.getFirstChildWithName(new QName(SRUNamespaces.zingSRW.getValue(),"recordData"));

                    if (null != srwRecordData) {
                        OMElement completeRecordOmElement= srwRecordData.getFirstChildWithName(new QName(SRUNamespaces.marc21Slim.getValue(), "record"));


                        if (null != completeRecordOmElement) {
                            MessageContext mc =  MessageContext.getCurrentMessageContext();
                            if ( Boolean.valueOf( mc.getAxisService().getParameter(CHECK_LEADER_FOR_DELETE).getValue().toString()) && checkLeaderForDelete(completeRecordOmElement)) {

                                //serializeDeleteRecord(idText,record);
                                //swissbib classic is using the complete (SOAP) record in the document processing
                                //should be changed
                                serializeRecord(idText,record, getOutputDir(SRUActions.fromString(actionText)), false);
                                responseElement =  createDeleteResponse(idText);
                            } else {


                                //for the swissbib classic procedure we serialize all the records into the updateDir
                                serializeRecord(idText,completeRecordOmElement, getOutputDir(SRUActions.replace), true);
                                responseElement = createUpdateReplaceResponse(idText, completeRecordOmElement);



                            }
                            logMessages(idText,actionText);
                        } else {

                            responseElement = createFailureResponse(idText,"replace or create message without complete record element","info:srw/diagnostic/12/1");
                        }


                    } else {
                        responseElement = createFailureResponse(idText,"replace or create message without record data","info:srw/diagnostic/12/1");
                    }


                }
            }

        } catch (Exception except) {

            except.printStackTrace();

        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        return responseElement;

    }


    private String getOutputDir (SRUActions action) {

        MessageContext mc =  MessageContext.getCurrentMessageContext();
        String outputDir = null;


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

        return outputDir;

    }

    private boolean checkLeaderForDelete(OMElement completeRecordOmElement) {

        //OMElement leaderOME =  completeRecordOmElement.getFirstChildWithName(new QName(nsURImxSLIM,"leader"));
        //String leaderChar = leaderOME != null ? leaderOME.getText().substring(5,6): "";
        //Todo: implement this method

        //this was implemented and used before CBS chanegd the behaviour and has sent (hopefully) always correct delete messages
        //we are going to use it again once the CBS implementation is again wrong
        return false;
    }

    /**
     *
     * @param identifier of the record
     * @param completeRecord content of the SRW request
     * @return    content of the SOAP message (child tag of soapenv:Body)
     */
    private OMElement createUpdateReplaceResponse (String identifier, OMNode completeRecord) {

        final String RECORD_IN_RESPONSE  = "includeRecordInResponse";

        //method creates the content which is sent back to the client and child of soapenv:Body tag

        OMFactory fac = OMAbstractFactory.getOMFactory();

        OMNamespace nsUpdate = fac.createOMNamespace( SRUNamespaces.zingSRWupdate.getValue(),"ns4");
        OMNamespace nsDiagnostic = fac.createOMNamespace(SRUNamespaces.diagnostic.getValue(),"ns3");
        OMNamespace nsSRW = fac.createOMNamespace(SRUNamespaces.zingSRW.getValue(),"ns5");
        OMNamespace nsXSI = fac.createOMNamespace(SRUNamespaces.xmlSchemaInstance.getValue(),"xsi");

        OMElement responseElement = fac.createOMElement("updateResponse", nsUpdate);
        responseElement.declareNamespace(nsDiagnostic);
        responseElement.declareNamespace(nsSRW);

        fac.createOMElement("version",nsSRW,responseElement).setText("1.0");
        fac.createOMElement("operationStatus",nsSRW,responseElement).setText("success");
        fac.createOMElement("recordIdentifier",nsSRW,responseElement).setText(identifier);
        OMElement versionsElement = fac.createOMElement("recordVersions",nsXSI,responseElement);
        versionsElement.addAttribute("nil","true",nsXSI);
        OMElement recordElement = fac.createOMElement("record",nsSRW,responseElement);

        fac.createOMElement("recordSchema", nsSRW, recordElement).setText(ResponseRecordMetaInfo.recordSchema.getValue());
        fac.createOMElement("recordPacking",nsSRW,recordElement).setText(ResponseRecordMetaInfo.packaging.getValue());

        OMElement recordData = fac.createOMElement("recordData",nsSRW,recordElement);

        if (Boolean.valueOf(MessageContext.getCurrentMessageContext().getAxisService().getParameter(RECORD_IN_RESPONSE).getValue().toString())) {
            recordData.addChild(completeRecord);
        }

        return responseElement;
    }


    private OMElement createFailureResponse (String identifier, String failureMessage, String uri) {


        //method creates the content which is sent back to the client and child of soapenv:Body tag

        OMFactory fac = OMAbstractFactory.getOMFactory();

        OMNamespace nsUpdate = fac.createOMNamespace( SRUNamespaces.zingSRWupdate.getValue(),"ns4");
        OMNamespace nsDiagnostic = fac.createOMNamespace(SRUNamespaces.diagnostic.getValue(),"ns3");
        OMNamespace nsSRW = fac.createOMNamespace(SRUNamespaces.zingSRW.getValue(),"ns5");
        OMNamespace nsXSI = fac.createOMNamespace(SRUNamespaces.xmlSchemaInstance.getValue(),"xsi");

        OMElement responseElement = fac.createOMElement("updateResponse", nsUpdate);
        responseElement.declareNamespace(nsDiagnostic);
        responseElement.declareNamespace(nsSRW);


        /*

<diagnostics>
      <diagnostic xmlns="http://www.loc.gov/zing/srw/diagnostic/">
             <uri>info:srw/diagnostic/1/38</uri>
             <details>10</details>
             < message>
                Too many boolean operators, the maximum is 10.
                Please try a less complex query.</message>
     < /diagnostic>
< /diagnostics>
         */




        fac.createOMElement("version",nsSRW,responseElement).setText("1.0");
        fac.createOMElement("operationStatus",nsSRW,responseElement).setText("fail");
        fac.createOMElement("recordIdentifier",nsSRW,responseElement).setText(identifier);
        OMElement versionsElement = fac.createOMElement("recordVersions",nsXSI,responseElement);
        versionsElement.addAttribute("nil","true",nsXSI);
        //OMElement recordElement = fac.createOMElement("record",nsSRW,responseElement);

        //fac.createOMElement("recordSchema", nsSRW, recordElement).setText(ResponseRecordMetaInfo.recordSchema.getValue());
        //fac.createOMElement("recordPacking",nsSRW,recordElement).setText(ResponseRecordMetaInfo.packaging.getValue());

        OMElement diagnosticsElement = fac.createOMElement("diagnostics",null,responseElement);
        OMElement diagnosticElement = fac.createOMElement("diagnostic",nsDiagnostic,diagnosticsElement);
        fac.createOMElement("uri",null,diagnosticElement).setText(uri);
        if (null != failureMessage) {
            fac.createOMElement("message",null,diagnosticElement).setText(failureMessage);

        }


        //fac.createOMElement("recordData",nsSRW,recordElement);

        //if (Boolean.valueOf(MessageContext.getCurrentMessageContext().getAxisService().getParameter(RECORD_IN_RESPONSE).getValue().toString())) {
        //    recordData.addChild(completeRecord);
        //}

        return responseElement;
    }



    /**
     *
     * @param identifier of the record
     * @return    content of the SOAP message (child tag of soapenv:Body)
     */
    private OMElement createDeleteResponse (String identifier) {



        //method creates the content which is sent back to the client and child of soapenv:Body tag

        OMFactory fac = OMAbstractFactory.getOMFactory();

        OMNamespace nsUpdate = fac.createOMNamespace( SRUNamespaces.zingSRWupdate.getValue(),"ns4");
        OMNamespace nsDiagnostic = fac.createOMNamespace(SRUNamespaces.diagnostic.getValue(),"ns3");
        OMNamespace nsSRW = fac.createOMNamespace(SRUNamespaces.zingSRW.getValue(),"ns5");
        OMNamespace nsXSI = fac.createOMNamespace(SRUNamespaces.xmlSchemaInstance.getValue(),"xsi");

        OMElement responseElement = fac.createOMElement("updateResponse", nsUpdate);



        responseElement.declareNamespace(nsDiagnostic);
        responseElement.declareNamespace(nsSRW);

        fac.createOMElement("version",nsSRW,responseElement).setText("1.0");
        fac.createOMElement("operationStatus",nsSRW,responseElement).setText("success");
        fac.createOMElement("recordIdentifier",nsSRW,responseElement).setText(identifier);
        OMElement versionsElement = fac.createOMElement("recordVersions",nsXSI,responseElement);
        versionsElement.addAttribute("nil","true",nsXSI);
        OMElement recordElement = fac.createOMElement("record",nsSRW,responseElement);


        fac.createOMElement("recordSchema", nsSRW, recordElement).setText(ResponseRecordMetaInfo.recordSchema.getValue());
        fac.createOMElement("recordPacking",nsSRW,recordElement).setText(ResponseRecordMetaInfo.packaging.getValue());
        fac.createOMElement("recordData",nsSRW,recordElement);

        return responseElement;
    }



    private void serializeRecord (String recordID, OMElement completeRecord, String directoryPath, boolean transformRecord) throws Exception {

        final String TRANSFORM_TEMPLATE = "transformTemplate";
        final String RECORD_NS = "recordWithNamespaces";
        final String NORMALIZE_CHARS = "normalizeChars";

        MessageContext mc =  MessageContext.getCurrentMessageContext();
        //String outputDir = mc.getAxisService().getParameter(UPD_DIR).getValue().toString();

        File recordFile = createFileForSerialization(directoryPath,recordID);



        StringWriter serializedRecord = new StringWriter();
        completeRecord.serialize(serializedRecord);


        //do we want the target record without namespaces?
        if (transformRecord && !Boolean.valueOf(mc.getAxisService().getParameter(RECORD_NS).getValue().toString())) {
            Source sourceWithNS = new StreamSource(new StringReader(serializedRecord.toString()));
            serializedRecord = new StringWriter();
            Result tempXsltResult = new StreamResult(serializedRecord);
            Parameter template = mc.getAxisService().getParameter(TRANSFORM_TEMPLATE);
            Templates recordTemplate  =  ((Templates) template.getValue());
            recordTemplate.newTransformer().transform(sourceWithNS,tempXsltResult);
        }

        //do we want to normalize the chars?
        if (Boolean.valueOf(mc.getAxisService().getParameter(NORMALIZE_CHARS).getValue().toString())) {

            //http://docs.oracle.com/javase/tutorial/i18n/text/normalizerapi.html

            serializedRecord = new StringWriter().append(Normalizer.normalize(serializedRecord.toString(), Normalizer.Form.NFC));
        }

        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream( recordFile),"UTF-8"));
        bw.write(serializedRecord.toString());

        bw.flush();
        bw.close();

    }






    private void serializeDeleteRecord (String recordID, OMElement deleteMessage) throws Exception {

        final String  DEL_DIR  = "deleteDir";


        MessageContext mc =  MessageContext.getCurrentMessageContext();
        String outputDir = mc.getAxisService().getParameter(DEL_DIR).getValue().toString();

        File recordFile = createFileForSerialization(outputDir,recordID);


        StringWriter serializedRecord = new StringWriter();
        deleteMessage.serialize(serializedRecord);


        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream( recordFile),"UTF-8"));
        bw.write(serializedRecord.toString());

        bw.flush();
        bw.close();

    }


    private void  logMessages (String messageID, String actionText) {

        final String ACTIVE_MONGO_COLLECTION = "activeMongoCollection";
        final String LOG_MESSAGES  = "logMessages";


        try {
            MessageContext mc =  MessageContext.getCurrentMessageContext();
            //String test = mc.getAxisService().getParameter(LOG_MESSAGES).getValue().toString();
            if ( Boolean.valueOf (mc.getAxisService().getParameter(LOG_MESSAGES).getValue().toString())) {

                MongoCollection<Document> mongoCollection = (MongoCollection<Document>) mc.getAxisService().getParameter(ACTIVE_MONGO_COLLECTION).getValue();

                //SimpleDateFormat simpleFormat = new SimpleDateFormat("yyyy-mm-dd hh:mm:ss");
                SimpleDateFormat simpleFormatDay = new SimpleDateFormat("yyyy-MM-dd");
                //SimpleDateFormat exactHumanReadbleTime = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss");
                SimpleDateFormat exactHumanReadbleTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

                Date currentDate = new Date();

                // yyyy-mm-dd hh:mm:ss.fffffffff

                //String td = simpleFormatDay.format(currentDate);
                //String tmE = exactHumanReadbleTime.format(currentDate);

                //long currentTimestamp = currentDate.getTime();

                //Parameter delPattern = mc.getAxisService().getParameter(DEL_PATTERN);

                //Pattern p =  (Pattern) delPattern.getValue();

                /*
                actually we see the problem that our data-hub sends SRU messages containing a create or replace
                SRU message although the record is indicated as deleted in the leader tag.
                These records are going to be deleted on the SearchIndex (look at serializeRecord method) and the original action text is extended
                to mark such cases in the logs
                */
                //if (leaderCharPos6.equals("d") && ! p.matcher(actionText).find()) {
                //    actionText = actionText + ":sbdelete";
                //}


                Document doc = new Document().
                        append("id", messageID).
                        append("action", actionText).
                        append("updateDay", simpleFormatDay.format(currentDate)).
                        //append("marcStatus", leaderCharPos6).
                        append("timestamp", currentDate.getTime()).
                        append("readTime", exactHumanReadbleTime.format(currentDate));

                mongoCollection.insertOne(doc);


            }
        } catch (Throwable thr) {
            System.out.println("Exception  trying to write log message into Mongo DB for id: " + messageID + " action: " + actionText);
            System.out.print(thr.getMessage());
        }





    }


    private File createFileForSerialization(String baseDir, String recordID) {

        final String FILE_PREFIX = "filePrefix";
        final String FILE_SUFFIX = "fileSuffix";

        MessageContext mc =  MessageContext.getCurrentMessageContext();


        SimpleDateFormat exactHumanReadbleTime = new SimpleDateFormat("yyyy_MM_dd:HH_mm_ss.SS");
        //SimpleDateFormat exactHumanReadbleTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        Date currentDate = new Date();
        String timeFilePrefix = exactHumanReadbleTime.format(currentDate);

        File recordFile =  new File(baseDir + timeFilePrefix + "_" +
                mc.getAxisService().getParameter(FILE_PREFIX).getValue().toString() +
                recordID +
                mc.getAxisService().getParameter(FILE_SUFFIX).getValue().toString());


        return recordFile;
    }


    private String getRecordId(OMElement record) {
        return record.getFirstChildWithName(new QName(SRUNamespaces.zingSRWupdate.getValue(),"recordIdentifier")).getText();
    }

    private String getActionText(OMElement record) {
        return record.getFirstChildWithName(new QName(SRUNamespaces.zingSRWupdate.getValue(),"action")).getText();
    }


}
