package org.swissbib.srw.serialization;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.nio.channels.Pipe;
import java.util.function.Consumer;


@FunctionalInterface
public interface CBSDataConsumer  extends Consumer<PipeData> {

    @Override
    public void accept(PipeData t);

}
