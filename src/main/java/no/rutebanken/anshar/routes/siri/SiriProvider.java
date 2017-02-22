package no.rutebanken.anshar.routes.siri;

import no.rutebanken.anshar.messages.EstimatedTimetables;
import no.rutebanken.anshar.messages.ProductionTimetables;
import no.rutebanken.anshar.messages.Situations;
import no.rutebanken.anshar.messages.VehicleActivities;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.MappingAdapterPresets;
import org.apache.camel.builder.RouteBuilder;
import org.apache.http.HttpHeaders;
import org.rutebanken.siri20.util.SiriJson;
import org.rutebanken.siri20.util.SiriXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.Siri;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

@Service
@Configuration
public class SiriProvider extends RouteBuilder {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${anshar.incoming.port}")
    private String inboundPort;

    @Value("${anshar.inbound.pattern}")
    private String incomingPathPattern = "/foo/bar/rest";

    @Value("${anshar.incoming.logdirectory}")
    private String incomingLogDirectory = "/tmp";

    @Autowired
    private Situations situations;

    @Autowired
    private VehicleActivities vehicleActivities;

    @Autowired
    private EstimatedTimetables estimatedTimetables;

    @Autowired
    private ProductionTimetables productionTimetables;
    
    @Autowired
    private SiriObjectFactory siriObjectFactory;

    @Autowired
    private MappingAdapterPresets mappingAdapterPresets;

    @Override
    public void configure() throws Exception {


        // Dataproviders
        from("jetty:http://0.0.0.0:" + inboundPort + "/anshar/rest/sx?httpMethodRestrict=GET")
                .log("RequestTracer - Incoming request (SX)")
                .process(p -> {
                    p.getOut().setHeaders(p.getIn().getHeaders());

                    HttpServletRequest request = p.getIn().getBody(HttpServletRequest.class);
                    String datasetId = request.getParameter("datasetId");
                    String requestorId = request.getParameter("requestorId");
                    String originalId = request.getParameter("useOriginalId");

                    Siri response = siriObjectFactory.createSXServiceDelivery(situations.getAllUpdates(requestorId, datasetId));

                    boolean useMappedId = useMappedId(originalId);
                    List<ValueAdapter> outboundAdapters = mappingAdapterPresets.getOutboundAdapters(useMappedId);
                    if ("test".equals(originalId)) {
                        outboundAdapters = null;
                    }
                    response = SiriValueTransformer.transform(response, outboundAdapters);

                    HttpServletResponse out = p.getIn().getBody(HttpServletResponse.class);

                    if ("application/json".equals(p.getIn().getHeader(HttpHeaders.CONTENT_TYPE))) {
                        out.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
                        SiriJson.toJson(response, out.getOutputStream());
                    } else {
                        SiriXml.toXml(response, null, out.getOutputStream());
                    }
                })
                .log("RequestTracer - Request done (SX)")
        ;

        from("jetty:http://0.0.0.0:" + inboundPort + "/anshar/rest/vm?httpMethodRestrict=GET")
                .log("RequestTracer - Incoming request (VM)")
                .process(p -> {
                    p.getOut().setHeaders(p.getIn().getHeaders());

                    HttpServletRequest request = p.getIn().getBody(HttpServletRequest.class);
                    String datasetId = request.getParameter("datasetId");
                    String requestorId = request.getParameter("requestorId");
                    String originalId = request.getParameter("useOriginalId");

                    Siri response = siriObjectFactory.createVMServiceDelivery(vehicleActivities.getAllUpdates(requestorId, datasetId));

                    boolean useMappedId = useMappedId(originalId);

                    List<ValueAdapter> outboundAdapters = mappingAdapterPresets.getOutboundAdapters(useMappedId);
                    if ("test".equals(originalId)) {
                        outboundAdapters = null;
                    }
                    response = SiriValueTransformer.transform(response, outboundAdapters);

                    HttpServletResponse out = p.getIn().getBody(HttpServletResponse.class);

                    if ("application/json".equals(p.getIn().getHeader(HttpHeaders.CONTENT_TYPE))) {
                        out.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
                        SiriJson.toJson(response, out.getOutputStream());
                    } else {
                        SiriXml.toXml(response, null, out.getOutputStream());
                    }
                })
                .log("RequestTracer - Request done (VM)")
        ;


        from("jetty:http://0.0.0.0:" + inboundPort + "/anshar/rest/et?httpMethodRestrict=GET")
                .log("RequestTracer - Incoming request (ET)")
                .process(p -> {
                    p.getOut().setHeaders(p.getIn().getHeaders());

                    HttpServletRequest request = p.getIn().getBody(HttpServletRequest.class);
                    String datasetId = request.getParameter("datasetId");
                    String requestorId = request.getParameter("requestorId");
                    String originalId = request.getParameter("useOriginalId");

                    Siri response = siriObjectFactory.createETServiceDelivery(estimatedTimetables.getAllUpdates(requestorId, datasetId));

                    boolean useMappedId = useMappedId(originalId);
                    List<ValueAdapter> outboundAdapters = mappingAdapterPresets.getOutboundAdapters(useMappedId);
                    if ("test".equals(originalId)) {
                        outboundAdapters = null;
                    }
                    response = SiriValueTransformer.transform(response, outboundAdapters);

                    HttpServletResponse out = p.getIn().getBody(HttpServletResponse.class);

                    if ("application/json".equals(p.getIn().getHeader(HttpHeaders.CONTENT_TYPE))) {
                        out.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
                        SiriJson.toJson(response, out.getOutputStream());
                    } else {
                        SiriXml.toXml(response, null, out.getOutputStream());
                    }
                })
                .log("RequestTracer - Request done (ET)")
        ;


        from("jetty:http://0.0.0.0:" + inboundPort + "/anshar/rest/pt?httpMethodRestrict=GET")
                .log("RequestTracer - Incoming request (PT)")
                .process(p -> {
                    p.getOut().setHeaders(p.getIn().getHeaders());

                    HttpServletRequest request = p.getIn().getBody(HttpServletRequest.class);
                    String datasetId = request.getParameter("datasetId");
                    String requestorId = request.getParameter("requestorId");
                    String originalId = request.getParameter("useOriginalId");

                    Siri response = siriObjectFactory.createPTServiceDelivery(productionTimetables.getAllUpdates(requestorId, datasetId));

                    boolean useMappedId = useMappedId(originalId);
                    List<ValueAdapter> outboundAdapters = mappingAdapterPresets.getOutboundAdapters(useMappedId);
                    if ("test".equals(originalId)) {
                        outboundAdapters = null;
                    }
                    response = SiriValueTransformer.transform(response, outboundAdapters);

                    HttpServletResponse out = p.getIn().getBody(HttpServletResponse.class);

                    if ("application/json".equals(p.getIn().getHeader(HttpHeaders.CONTENT_TYPE))) {
                        out.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
                        SiriJson.toJson(response, out.getOutputStream());
                    } else {
                        SiriXml.toXml(response, null, out.getOutputStream());
                    }
                })
                .log("RequestTracer - Request done (PT)")
        ;

    }

    // Mapped ID is default, but original ID may be returned by using optional parameter
    private boolean useMappedId(String originalId) {
        boolean useMappedId = true;
        if (originalId != null && Boolean.valueOf(originalId)) {
            useMappedId = !Boolean.valueOf(originalId);
        }
        logger.info("Requested {} ids...", (useMappedId ? "mapped": "original"));
        return useMappedId;
    }
}
