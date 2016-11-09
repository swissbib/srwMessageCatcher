package org.swissbib.srw;

import org.apache.axiom.om.*;

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
 * @link https://github.com/swissbib/srwMessageCatcher
 */


public class SRWUpdateService {


    enum UpdateType {
        linked_swissbib,
        swissbib_classic
    }

    enum SRUActions {
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


    enum ResponseRecordMetaInfo {
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


    enum SRUNamespaces {
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

    // TODO: 6/21/16 generell
    /*
    - vernünftiges logging
    - was passiert wenn eine Exception geworfen wird? ich brauche in diesem Fall immer eine ungültige response
    - kann es sein, dass dies der Grund ist, dass deletes nicht ordentlich behandelt worden sind?
    - werdentlich ordentliche (wie bisher) responses generiert?
     - bisher gab es serialze delete record. Braucht es diese wirklis nicht mehr? was wird von delete aktuell zurückgeschickt? IN Produktion
     ausprobieren
     */
    public OMElement updateRDF (OMElement record) {

        OMElement responseElement = null;

        try {

            UpdateImplementation updateImplementation = new UpdateImplementation(record, UpdateType.linked_swissbib);
            responseElement = updateImplementation.processMessage();

        } catch (Exception except) {

            except.printStackTrace();

        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        return responseElement;

    }


    public OMElement update (OMElement record) {


        OMElement responseElement = null;

        try {

            UpdateImplementation updateImplementation = new UpdateImplementation(record, UpdateType.swissbib_classic);
            responseElement = updateImplementation.processMessage();

        } catch (Exception except) {

            except.printStackTrace();

        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        return responseElement;

    }
















}
