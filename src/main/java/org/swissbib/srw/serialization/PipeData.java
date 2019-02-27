package org.swissbib.srw.serialization;

public class PipeData {

    public enum Action {
        create,
        update,
        delete
    }

    public String body;

    public String id;

    public Action action;

}
