package org.swissbib.srw.serialization;

import org.apache.axis2.context.MessageContext;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.swissbib.linked.kafka.KafkaWriter;
import org.swissbib.srw.ApplicationConstants;

import java.util.HashMap;

public class KafkaConsumer implements CBSDataConsumer {

    private KafkaWriter<HashMap<String,String>> kw = null;
    private static KafkaConsumer kc = null;


    private KafkaConsumer(MessageContext mc) {
        kw = new KafkaWriter<>();
        kw.setKafkaTopic(mc.getAxisService().getParameter(ApplicationConstants.KAFKA_TOPIC.getValue()).getValue().toString());
        kw.setBootstrapServers(mc.getAxisService().getParameter(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG).getValue().toString());

    }

    public static KafkaConsumer createConsumer(MessageContext mc) {

        if (null == kc) {
            kc = new KafkaConsumer(mc);
        }
        return kc;
    }


    @Override
    public void accept(PipeData t) {

        HashMap<String,String> hm = new HashMap<>();
        hm.put("id", t.id);
        hm.put("body", t.body);
        hm.put("action", t.action.name());

        kw.process(hm);
    }
}
