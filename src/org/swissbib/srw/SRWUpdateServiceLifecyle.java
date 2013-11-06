package org.swissbib.srw;

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
import java.util.regex.Pattern;

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





    @Override
    public void startUp(ConfigurationContext configurationContext, AxisService axisService) {

        System.out.println("in startup");

        final  String UPD_DIR = "updateDir";
        final String  DEL_DIR  = "deleteDir";
        final String DEL_PATTERN = "deletePattern";
        final String TRANSFORM_TEMPLATE = "transformTemplate";
        final String FILE_PREFIX = "filePrefix";
        final String FILE_SUFFIX = "fileSuffix";
        final String RECORD_NS = "recordWithNamespaces";
        final String NORMALIZE_CHARS = "normalizeChars";
        final String RECORD_IN_RESPONSE  = "includeRecordInResponse";



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

            Parameter normalizeChars = axisService.getParameter(NORMALIZE_CHARS);
            axisService.addParameter(normalizeChars);


            Parameter recordInResponse = axisService.getParameter(RECORD_IN_RESPONSE);
            axisService.addParameter(recordInResponse);

            //info:srw/action/1/delete
            //info:srw/action/1/replace
            //info:srw/action/1/create
            Parameter delPattern = axisService.getParameter(DEL_PATTERN);
            String pattern = delPattern.getValue().toString();
            Pattern p = Pattern.compile(pattern,Pattern.CASE_INSENSITIVE);

            axisService.addParameter(new Parameter(DEL_PATTERN,p));

            String transformTemplate = axisService.getParameter(TRANSFORM_TEMPLATE).getValue().toString();

            InputStream stream = getClass().getClassLoader().getResourceAsStream(transformTemplate);

            StreamSource source = new StreamSource(stream);
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            //Transformer transform = transformerFactory.newTransformer(source);
            Templates recordTransformer = transformerFactory.newTemplates(source);

            axisService.addParameter(new Parameter(TRANSFORM_TEMPLATE,recordTransformer));




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
        //To change body of implemented methods use File | Settings | File Templates.
    }



}
