package org.swissbib.srw;

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
public enum ApplicationConstants {

    LOG_COMPLETE_RECORD_CLASSIC("logCompleteRecordClassic"),
    LOG_COMPLETE_RECORD_RDF("logCompleteRecordRDF"),
    LOG_MESSAGES_CLASSIC("logMessagesClassic"),
    LOG_MESSAGES_LINKED("logMessagesLinked"),
    CHECK_LEADER_FOR_DELETE("checkLeaderForDelete"),
    FILE_PREFIX("filePrefix"),
    FILE_SUFFIX("fileSuffix"),
    TRANSFORM_CLASSIC_TEMPLATE("transformClassicTemplate"),
    TRANSFORM_RDF_TEMPLATE("transformRdfTemplate"),
    ACTIVE_MONGO_COLLECTION("activeMongoCollection");





    String applicationConstant;
    ApplicationConstants(String appConstant) {
        this.applicationConstant = appConstant;
    }

    public String getValue() {
        return this.applicationConstant;
    }


}
