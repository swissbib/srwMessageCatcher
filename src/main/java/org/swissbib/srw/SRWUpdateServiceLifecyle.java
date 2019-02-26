package org.swissbib.srw;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.apache.axis2.AxisFault;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.engine.ServiceLifeCycle;
import org.bson.Document;

import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;


/**
 * [...description of the type ...]
 * <p/>
 * <p/>
 * <p/>
 * Copyright (C) project swissbib, University Library Basel, Switzerland
 * http://www.swissbib.org  / http://www.swissbib.ch / http://www.ub.unibas.ch
 * <p/>
 * Date: 11/5/13
 * Time: 7:29 AM
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
public class SRWUpdateServiceLifecyle implements ServiceLifeCycle {


//debug Axis2
//http://amilamanoj.blogspot.ch/2011/09/running-debugging-apache-axis2-inside.html
//http://shameerarathnayaka.blogspot.ch/2011/09/remote-debugging-apache-axis2-with.html
//http://insightforfuture.blogspot.ch/2012/05/what-is-remote-debugging-java.html

    final String ACTIVE_MONGO_CLIENT = "activeMongoClient";



    @SuppressWarnings("deprecation")
    @Override
    public void startUp(ConfigurationContext configurationContext, AxisService axisService) {

        System.out.println("in startup");

        final String UPD_DIR = "updateDir";
        final String DEL_DIR  = "deleteDir";
        final String CREATE_DIR = "createDir";
        final String DEL_PATTERN = "deletePattern";
        final String FILE_PREFIX = "filePrefix";
        final String FILE_SUFFIX = "fileSuffix";
        final String RECORD_NS = "recordWithNamespaces";
        final String NORMALIZE_CHARS = "normalizeChars";
        final String RECORD_IN_RESPONSE  = "includeRecordInResponse";
        final String TRANSFORM_CLASSIC_RECORD =  "transformClassicRecord";
        final String TRANSFORM_RDF_RECORD =  "transformRdfRecord";

        final String MONGO_CLIENT  = "MONGO.CLIENT";
        final String MONGO_AUTHENTICATION  = "MONGO.AUTHENTICATION";
        final String MONGO_DB  = "MONGO.DB";
        final String ACTIVE_MONGO_COLLECTION = "activeMongoCollection";
        final String CHECK_LEADER_FOR_DELETE = "checkLeaderForDelete";
        final String TEMPLATE_CREATE_MARCXML =  "templateCreateMarcXml";


        try {
            Parameter updDir = axisService.getParameter(UPD_DIR);
            axisService.addParameter(updDir);


            Parameter createDir = axisService.getParameter(CREATE_DIR);
            axisService.addParameter(createDir);


            Parameter delDir = axisService.getParameter(DEL_DIR);
            axisService.addParameter(delDir);


            Parameter processedDelayedDir = axisService.getParameter(ApplicationConstants.PROCESSED_DELAYED_DIR.getValue());
            axisService.addParameter(processedDelayedDir);


            Parameter filePrefix = axisService.getParameter(FILE_PREFIX);
            axisService.addParameter(filePrefix);

            Parameter fileSuffix = axisService.getParameter(FILE_SUFFIX);
            axisService.addParameter(fileSuffix);

            Parameter recordNS = axisService.getParameter(RECORD_NS);
            axisService.addParameter(recordNS);

            Parameter checkLeaderForDelete = axisService.getParameter(CHECK_LEADER_FOR_DELETE);
            boolean checkLeader =   Boolean.valueOf(checkLeaderForDelete.getValue().toString());
            axisService.addParameter(CHECK_LEADER_FOR_DELETE,checkLeader);

            Parameter transformClassicRecord = axisService.getParameter(TRANSFORM_CLASSIC_RECORD);
            axisService.addParameter(TRANSFORM_CLASSIC_RECORD,Boolean.valueOf(transformClassicRecord.getValue().toString()));


            Parameter transformRdfRecord = axisService.getParameter(TRANSFORM_RDF_RECORD);
            axisService.addParameter(TRANSFORM_RDF_RECORD,Boolean.valueOf(transformRdfRecord.getValue().toString()));



            Parameter logCompleteRecordClassic = axisService.getParameter(ApplicationConstants.LOG_COMPLETE_RECORD_CLASSIC.getValue());
            axisService.addParameter(ApplicationConstants.LOG_COMPLETE_RECORD_CLASSIC.getValue(),
                    Boolean.valueOf(logCompleteRecordClassic.getValue().toString()));


            Parameter logCompleteRecordRDF = axisService.getParameter(ApplicationConstants.LOG_COMPLETE_RECORD_RDF.getValue());
            axisService.addParameter(ApplicationConstants.LOG_COMPLETE_RECORD_RDF.getValue(),
                    Boolean.valueOf(logCompleteRecordRDF.getValue().toString()));


            Parameter normalizeChars = axisService.getParameter(NORMALIZE_CHARS);
            axisService.addParameter(normalizeChars);


            Parameter recordInResponse = axisService.getParameter(RECORD_IN_RESPONSE);
            axisService.addParameter(recordInResponse);

            Parameter parseDelayedProcessing = axisService.getParameter(ApplicationConstants.PARSE_DELAYED_PROCESSING.getValue());
            String[] delayedProcessingParams =  parseDelayedProcessing.getValue().toString().split("###");
            if (delayedProcessingParams.length == 5) {
                HashMap<String,String> dParams = new HashMap<>();
                dParams.put("isDelayed",delayedProcessingParams[0]);
                dParams.put("fieldType",delayedProcessingParams[1]);
                dParams.put("tag",delayedProcessingParams[2]);
                dParams.put("code",delayedProcessingParams[3]);
                dParams.put("value",delayedProcessingParams[4]);
                axisService.addParameter(ApplicationConstants.PARSE_DELAYED_PROCESSING.getValue(),dParams);
            } else {
                HashMap<String,String> dParams = new HashMap<>();
                dParams.put("isDelayed","FALSE");
                dParams.put("fieldType","");
                dParams.put("tag","");
                dParams.put("code","");
                dParams.put("value","");
                axisService.addParameter(ApplicationConstants.PARSE_DELAYED_PROCESSING.getValue(),dParams);

            }


            String transformTemplate = axisService.getParameter(ApplicationConstants.TRANSFORM_CLASSIC_TEMPLATE.getValue()).
                    getValue().toString();
            InputStream stream = getClass().getClassLoader().getResourceAsStream(transformTemplate);
            StreamSource source = new StreamSource(stream);
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            //Transformer transform = transformerFactory.newTransformer(source);
            Templates recordTransformer = transformerFactory.newTemplates(source);
            axisService.addParameter(new Parameter(ApplicationConstants.TRANSFORM_CLASSIC_TEMPLATE.getValue(),recordTransformer));


            String transformRdfTemplate = axisService.getParameter(ApplicationConstants.TRANSFORM_RDF_TEMPLATE.getValue()).getValue().toString();
            InputStream streamRdfTemplate = getClass().getClassLoader().getResourceAsStream(transformRdfTemplate);
            StreamSource sourceRdf = new StreamSource(streamRdfTemplate);
            TransformerFactory transformerRDFFactory = TransformerFactory.newInstance();
            //Transformer transform = transformerFactory.newTransformer(sourceRdf);
            Templates recordRDFTransformer = transformerRDFFactory.newTemplates(sourceRdf);
            axisService.addParameter(new Parameter(ApplicationConstants.TRANSFORM_RDF_TEMPLATE.getValue(),recordRDFTransformer));


            String recordToLog = axisService.getParameter(TEMPLATE_CREATE_MARCXML).getValue().toString();

            InputStream streamRecord = getClass().getClassLoader().getResourceAsStream(recordToLog);

            StreamSource sourceRecord = new StreamSource(streamRecord);
            transformerFactory = TransformerFactory.newInstance();
            //Transformer transform = transformerFactory.newTransformer(source);
            Templates recordToLogTransformer = transformerFactory.newTemplates(sourceRecord);

            axisService.addParameter(new Parameter(TEMPLATE_CREATE_MARCXML,recordToLogTransformer));




            //logging of messages?
            Parameter loggingClassic = axisService.getParameter(ApplicationConstants.LOG_MESSAGES_CLASSIC.getValue());
            Parameter loggingLinked = axisService.getParameter(ApplicationConstants.LOG_MESSAGES_LINKED.getValue());

            axisService.addParameter(new Parameter(ApplicationConstants.LOG_MESSAGES_CLASSIC.getValue(),
                    Boolean.valueOf(loggingClassic.getValue().toString())));

            axisService.addParameter(new Parameter(ApplicationConstants.LOG_MESSAGES_LINKED.getValue(),
                    Boolean.valueOf(loggingLinked.getValue().toString())));


            boolean logActive =   Boolean.valueOf(loggingClassic.getValue().toString()) ||
                    Boolean.valueOf(loggingLinked.getValue().toString());

            if (logActive) {

                try {


                    //it is expected:
                    // <parameter name="MONGO.CLIENT">[host]###[port]</parameter>
                    String[] mongoClient = ((String)axisService.getParameter(MONGO_CLIENT).getValue()).split("###");

                    //it is expected that mongoAuthentication contains the values for:
                    //<parameter name="MONGO.AUTHENTICATION">[auth-db]###[user]###[password]</parameter>
                    String[] mongoAuthentication = ((String)axisService.getParameter(MONGO_AUTHENTICATION).getValue()).split("###");
                    //it is expected:
                    //<parameter name="MONGO.DB">[logging DB]###[collection]</parameter>
                    String[] mongoDB = ((String)axisService.getParameter(MONGO_DB).getValue()).split("###");

                    System.out.println("mongoHost: " + mongoClient[0]);
                    System.out.println("mongoPort: " + mongoClient[1]);

                    ServerAddress server = new ServerAddress(mongoClient[0], Integer.valueOf(mongoClient[1]));

                    MongoClient mClient = null;

                    if ( mongoAuthentication.length == 3 ) {
                        MongoCredential credential =
                                MongoCredential.createMongoCRCredential(mongoAuthentication[1], mongoAuthentication[0], mongoAuthentication[2].toCharArray());
                        mClient = new MongoClient(server, Arrays.asList(credential));
                    } else {
                        mClient = new MongoClient( server );
                    }

                    MongoDatabase messageDb = mClient.getDatabase(mongoDB[0]);
                    MongoCollection<Document> messageCollection =  messageDb.getCollection(mongoDB[1]);
                    axisService.addParameter(new Parameter(ACTIVE_MONGO_COLLECTION,messageCollection));
                    axisService.addParameter(new Parameter(ACTIVE_MONGO_CLIENT,mClient));


                    System.out.println("With Mongo DB connected");



                } catch (Exception exc) {

                    System.out.println("in Exception after trying to connect to Mongo DB");
                    exc.printStackTrace();

                    axisService.removeParameter(loggingClassic);
                    axisService.removeParameter(loggingLinked);
                    axisService.addParameter(new Parameter(ApplicationConstants.LOG_MESSAGES_CLASSIC.getValue(),
                            false));

                    axisService.addParameter(new Parameter(ApplicationConstants.LOG_MESSAGES_LINKED.getValue(),
                            false));

                }


            }


        } catch (AxisFault aF) {
            System.out.println("Fehler bei Speichern von UPD_DIR und DEL_DIR");
            aF.printStackTrace();

        } catch (TransformerConfigurationException configExcept) {
            configExcept.printStackTrace();
        }

    }

    @Override
    public void shutDown(ConfigurationContext configurationContext, AxisService axisService) {
        System.out.println("in shutdown");
        Parameter parameter = axisService.getParameter(ACTIVE_MONGO_CLIENT);
        Object o = parameter.getValue();
        if (null != o) {
            MongoClient client = (MongoClient) o;
            client.close();
            System.out.println("Mongo client was closed");
        }

    }

}
