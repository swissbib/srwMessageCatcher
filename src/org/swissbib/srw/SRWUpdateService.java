package org.swissbib.srw;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import org.apache.axiom.om.*;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.description.Parameter;
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




    public OMElement update (OMElement record) {

        final String nsURIupdReq = "http://www.loc.gov/zing/srw/update/";
        final String nsURImxSLIM = "http://www.loc.gov/MARC21/slim";
        final String nsURIsrw = "http://www.loc.gov/zing/srw/";


        OMElement responseElement = null;

        try {

            final String LOG_MESSAGES  = "logMessages";

            OMElement actionEl = record.getFirstChildWithName(new QName(nsURIupdReq,"action"));
            OMElement idEl = record.getFirstChildWithName(new QName(nsURIupdReq,"recordIdentifier"));

            String idText = idEl.getText();
            String actionText = actionEl.getText();


            OMElement srwRecord = record.getFirstChildWithName(new QName(nsURIsrw,"record"));
            if (null != srwRecord) {
                OMElement srwRecordData = srwRecord.getFirstChildWithName(new QName(nsURIsrw,"recordData"));
                OMElement packagingOmElement= srwRecord.getFirstChildWithName(new QName(nsURIsrw,"recordPacking"));
                OMElement schemaOmElement= srwRecord.getFirstChildWithName(new QName(nsURIsrw,"recordSchema"));

                String packaging = packagingOmElement.getText();
                String schema = schemaOmElement.getText();


                if (null != srwRecordData) {

                    OMElement completeRecordOmElement= srwRecordData.getFirstChildWithName(new QName(nsURImxSLIM, "record"));
                    OMElement leaderOME =  completeRecordOmElement.getFirstChildWithName(new QName(nsURImxSLIM,"leader"));


                    String leaderChar = leaderOME != null ? leaderOME.getText().substring(5,6): "";

                    serializeRecord(actionText,idText,completeRecordOmElement);
                    responseElement = createResponse(idText, packaging,schema, completeRecordOmElement);

                    logMessages(idText,actionText, leaderChar);


                }
            }
        } catch (Exception except) {

            except.printStackTrace();

        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        return responseElement;

    }


    /**
     *
     * @param identifier of the record
     * @param packaging  most of the cases xml
     * @param schema  most of the cases info:srw/schema/1/marcxml-v1.1
     * @param completeRecord content of the SRW request
     * @return    content of the SOAP message (child tag of soapenv:Body)
     */
    private OMElement createResponse (String identifier, String packaging, String schema, OMNode completeRecord) {

        final String RECORD_IN_RESPONSE  = "includeRecordInResponse";


        //method creates the content which is sent back to the client and child of soapenv:Body tag

        OMFactory fac = OMAbstractFactory.getOMFactory();

        OMNamespace nsUpdate = fac.createOMNamespace( "http://www.loc.gov/zing/srw/update/","ns4");
        OMNamespace nsDiagnostic = fac.createOMNamespace("http://www.loc.gov/zing/srw/diagnostic/","ns3");
        OMNamespace nsSRW = fac.createOMNamespace("http://www.loc.gov/zing/srw/","ns5");
        OMNamespace nsXSI = fac.createOMNamespace("http://www.w3.org/2001/XMLSchema-instance","xsi");

        OMElement responseElement = fac.createOMElement("updateResponse", nsUpdate);
        responseElement.declareNamespace(nsDiagnostic);
        responseElement.declareNamespace(nsSRW);

        fac.createOMElement("version",nsSRW,responseElement).setText("1.0");
        fac.createOMElement("operationStatus",nsSRW,responseElement).setText("success");
        fac.createOMElement("recordIdentifier",nsSRW,responseElement).setText(identifier);
        OMElement versionsElement = fac.createOMElement("recordVersions",nsXSI,responseElement);
        versionsElement.addAttribute("nil","true",nsXSI);
        OMElement recordElement = fac.createOMElement("record",nsSRW,responseElement);


        fac.createOMElement("recordSchema",nsSRW,recordElement).setText(schema);
        fac.createOMElement("recordPacking",nsSRW,recordElement).setText(packaging);
        OMElement recordData = fac.createOMElement("recordData",nsSRW,recordElement);

        if (Boolean.valueOf(MessageContext.getCurrentMessageContext().getAxisService().getParameter(RECORD_IN_RESPONSE).getValue().toString())) {
            recordData.addChild(completeRecord);
        }

        return responseElement;
    }


    private void serializeRecord (String actionType, String recordID, OMElement completeRecord ) throws Exception {

        final  String UPD_DIR = "updateDir";
        final String  DEL_DIR  = "deleteDir";
        final String DEL_PATTERN = "deletePattern";
        final String TRANSFORM_TEMPLATE = "transformTemplate";
        final String FILE_PREFIX = "filePrefix";
        final String FILE_SUFFIX = "fileSuffix";

        final String RECORD_NS = "recordWithNamespaces";
        final String NORMALIZE_CHARS = "normalizeChars";



        MessageContext mc =  MessageContext.getCurrentMessageContext();
        Parameter delPattern = mc.getAxisService().getParameter(DEL_PATTERN);
        String outputDir = null;

        Pattern p =     (Pattern) delPattern.getValue();
        if (p.matcher(actionType).find())  {

            outputDir = mc.getAxisService().getParameter(DEL_DIR).getValue().toString();
        } else {
            outputDir = mc.getAxisService().getParameter(UPD_DIR).getValue().toString();
        }

        File recordFile =  new File(outputDir +
                mc.getAxisService().getParameter(FILE_PREFIX).getValue().toString() +
                recordID +
                mc.getAxisService().getParameter(FILE_SUFFIX).getValue().toString());



        StringWriter serializedRecord = new StringWriter();
        completeRecord.serialize(serializedRecord);


        //do we want the target record without namespaces?
        if (! Boolean.valueOf(mc.getAxisService().getParameter(RECORD_NS).getValue().toString())) {

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


    private void  logMessages (String messageID, String actionText, String leaderCharPos6) {

        final String ACTIVE_MONGO_COLLECTION = "activeMongoCollection";
        final String LOG_MESSAGES  = "logMessages";


        try {
            MessageContext mc =  MessageContext.getCurrentMessageContext();
            String test = mc.getAxisService().getParameter(LOG_MESSAGES).getValue().toString();
            if ( Boolean.valueOf (mc.getAxisService().getParameter(LOG_MESSAGES).getValue().toString())) {

                DBCollection mongoCollection = (DBCollection) mc.getAxisService().getParameter(ACTIVE_MONGO_COLLECTION).getValue();

                //SimpleDateFormat simpleFormat = new SimpleDateFormat("yyyy-mm-dd hh:mm:ss");
                SimpleDateFormat simpleFormatDay = new SimpleDateFormat("yyyy-MM-dd");
                //SimpleDateFormat exactHumanReadbleTime = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss");
                SimpleDateFormat exactHumanReadbleTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

                Date currentDate = new Date();

                // yyyy-mm-dd hh:mm:ss.fffffffff

                //String td = simpleFormatDay.format(currentDate);
                //String tmE = exactHumanReadbleTime.format(currentDate);

                //long currentTimestamp = currentDate.getTime();

                BasicDBObject doc = new BasicDBObject("id", messageID).
                        append("action", actionText).
                        append("updateDay", simpleFormatDay.format(currentDate)).
                        append("marcStatus", leaderCharPos6).
                        append("timestamp", currentDate.getTime()).
                        append("readTime", exactHumanReadbleTime.format(currentDate));

                mongoCollection.insert(doc);


            }
        } catch (Throwable thr) {
            System.out.println("Exception  trying to write log message into Mongo DB for id: " + messageID + " action: " + actionText);
            System.out.print(thr.getMessage());
        }





    }



}
