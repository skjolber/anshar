package no.rutebanken.anshar.routes.siri;

import org.apache.camel.builder.xml.StreamResultHandlerFactory;
import org.springframework.stereotype.Component;

@Component(value = "streamResultHandlerFactory")
public class SiriStreamResultHandlerFactory extends StreamResultHandlerFactory {

}
