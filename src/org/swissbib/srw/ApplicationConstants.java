package org.swissbib.srw;

/**
 * Created by swissbib on 6/20/16.
 */
public enum ApplicationConstants {

    LOG_COMPLETE_RECORD_CLASSIC("logCompleteRecordClassic"),
    LOG_COMPLETE_RECORD_RDF("logCompleteRecordRDF");


    String applicationConstant;
    ApplicationConstants(String appConstant) {
        this.applicationConstant = appConstant;
    }

    public String getValue() {
        return this.applicationConstant;
    }


}
