package org.swissbib.srw;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;


import org.apache.axis2.AxisFault;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.engine.ServiceLifeCycle;

import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.util.Arrays;

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
 * @link https://github.com/swissbib/xml2SearchDoc
 */
public class SRWUpdateServiceLifecyle implements ServiceLifeCycle {


//debug Axis2
//http://amilamanoj.blogspot.ch/2011/09/running-debugging-apache-axis2-inside.html
//http://shameerarathnayaka.blogspot.ch/2011/09/remote-debugging-apache-axis2-with.html
//http://insightforfuture.blogspot.ch/2012/05/what-is-remote-debugging-java.html

    final String ACTIVE_MONGO_CLIENT = "activeMongoClient";



    @Override
    public void startUp(ConfigurationContext configurationContext, AxisService axisService) {

        System.out.println("in startup");

        final String UPD_DIR = "updateDir";
        final String DEL_DIR  = "deleteDir";
        final String CREATE_DIR = "createDir";
        final String DEL_PATTERN = "deletePattern";
        final String TRANSFORM_TEMPLATE = "transformTemplate";
        final String FILE_PREFIX = "filePrefix";
        final String FILE_SUFFIX = "fileSuffix";
        final String RECORD_NS = "recordWithNamespaces";
        final String NORMALIZE_CHARS = "normalizeChars";
        final String RECORD_IN_RESPONSE  = "includeRecordInResponse";

        final String LOG_MESSAGES  = "logMessages";
        final String MONGO_CLIENT  = "MONGO.CLIENT";
        final String MONGO_AUTHENTICATION  = "MONGO.AUTHENTICATION";
        final String MONGO_DB  = "MONGO.DB";
        final String ACTIVE_MONGO_COLLECTION = "activeMongoCollection";
        final String CHECK_LEADER_FOR_DELETE = "checkLeaderForDelete";



        try {
            Parameter updDir = axisService.getParameter(UPD_DIR);
            axisService.addParameter(updDir);

            Parameter delDir = axisService.getParameter(DEL_DIR);
            axisService.addParameter(delDir);

            Parameter filePrefix = axisService.getParameter(FILE_PREFIX);
            axisService.addParameter(filePrefix);

            Parameter fileSuffix = axisService.getParameter(FILE_SUFFIX);
            axisService.addParameter(fileSuffix);

            Parameter recordNS = axisService.getParameter(RECORD_NS);
            axisService.addParameter(recordNS);

            Parameter checkLeaderForDelete = axisService.getParameter(CHECK_LEADER_FOR_DELETE);
            boolean checkLeader =   Boolean.valueOf(checkLeaderForDelete.getValue().toString());
            axisService.addParameter(CHECK_LEADER_FOR_DELETE,checkLeader);


            Parameter normalizeChars = axisService.getParameter(NORMALIZE_CHARS);
            axisService.addParameter(normalizeChars);


            Parameter recordInResponse = axisService.getParameter(RECORD_IN_RESPONSE);
            axisService.addParameter(recordInResponse);

            String transformTemplate = axisService.getParameter(TRANSFORM_TEMPLATE).getValue().toString();

            InputStream stream = getClass().getClassLoader().getResourceAsStream(transformTemplate);

            StreamSource source = new StreamSource(stream);
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            //Transformer transform = transformerFactory.newTransformer(source);
            Templates recordTransformer = transformerFactory.newTemplates(source);

            axisService.addParameter(new Parameter(TRANSFORM_TEMPLATE,recordTransformer));

            //logging of messages?
            Parameter logging = axisService.getParameter(LOG_MESSAGES);

            boolean logActive =   Boolean.valueOf(logging.getValue().toString());
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
                        MongoCredential credential = MongoCredential.createMongoCRCredential(mongoAuthentication[1], mongoAuthentication[0], mongoAuthentication[2].toCharArray());
                        mClient = new MongoClient(server, Arrays.asList(credential));
                    } else {
                        mClient = new MongoClient( server );
                    }

                    MongoDatabase messageDb = mClient.getDatabase(mongoDB[0]);
                    MongoCollection<Document> messageCollection =  messageDb.getCollection(mongoDB[1]);
                    axisService.addParameter(new Parameter(ACTIVE_MONGO_COLLECTION,messageCollection));
                    axisService.addParameter(new Parameter(LOG_MESSAGES,"true"));
                    axisService.addParameter(new Parameter(ACTIVE_MONGO_CLIENT,mClient));

                    axisService.addParameter(new Parameter(ACTIVE_MONGO_COLLECTION,messageCollection));
                    axisService.addParameter(new Parameter(LOG_MESSAGES,"true"));

                    System.out.println("With MOngo DB connected");



                } catch (Exception exc) {

                    System.out.println("in Exception");

                    axisService.addParameter(new Parameter(LOG_MESSAGES,"false"));
                    exc.printStackTrace();
                }


            } else {
                axisService.addParameter(new Parameter(LOG_MESSAGES,"false"));
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
