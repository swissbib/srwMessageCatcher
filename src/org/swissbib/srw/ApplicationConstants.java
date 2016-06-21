package org.swissbib.srw;

/**
 * Created by swissbib on 6/20/16.
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
