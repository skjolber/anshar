/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.siri.processor;

import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.processor.RuterDatedVehicleRefPostProcessor;
import no.rutebanken.anshar.routes.siri.processor.RuterOutboundDatedVehicleRefAdapter;
import org.junit.Test;
import uk.org.siri.siri20.*;

import java.math.BigInteger;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import static junit.framework.TestCase.assertEquals;
import static no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer.SEPARATOR;

public class TestRuterDatedVehicleRefPostProcessor {


    private final String originalDatedVehicleRef = "250:125:9-12510";
    private final String targetVehicleRef = "RUT:ServiceJourney:250-125";

    private final String completeDatedVehicleRef = originalDatedVehicleRef + SEPARATOR + targetVehicleRef;

    @Test
    public void testConvertDatedVehicleRef() {

        Siri siri = createEtServiceDelivery(originalDatedVehicleRef);

        new RuterDatedVehicleRefPostProcessor().process(siri);

        assertEquals(completeDatedVehicleRef, siri.getServiceDelivery().getEstimatedTimetableDeliveries().get(0)
                .getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().get(0)
                .getFramedVehicleJourneyRef().getDatedVehicleJourneyRef());

    }

    @Test
    public void testConvertDatedVehicleRefOriginal() {

        Siri originalSiri = createEtServiceDelivery(originalDatedVehicleRef);

        new RuterDatedVehicleRefPostProcessor().process(originalSiri);
        new RuterOutboundDatedVehicleRefAdapter(this.getClass(), OutboundIdMappingPolicy.ORIGINAL_ID).process(originalSiri);

        assertEquals(originalDatedVehicleRef, originalSiri.getServiceDelivery().getEstimatedTimetableDeliveries().get(0)
                .getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().get(0)
                .getFramedVehicleJourneyRef().getDatedVehicleJourneyRef());

    }


    @Test
    public void testConvertDatedVehicleRefDefault() {

        Siri originalSiri = createEtServiceDelivery(originalDatedVehicleRef);

        new RuterDatedVehicleRefPostProcessor().process(originalSiri);
        new RuterOutboundDatedVehicleRefAdapter(this.getClass(), OutboundIdMappingPolicy.DEFAULT).process(originalSiri);

        assertEquals(targetVehicleRef, originalSiri.getServiceDelivery().getEstimatedTimetableDeliveries().get(0)
                .getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().get(0)
                .getFramedVehicleJourneyRef().getDatedVehicleJourneyRef());

    }

    private Siri createEtServiceDelivery(String datedVehicleRef) {
        return new SiriObjectFactory(Instant.now()).createETServiceDelivery(createEstimatedVehicleJourney(datedVehicleRef));
    }

    private List<EstimatedVehicleJourney> createEstimatedVehicleJourney(String datedVehicleRef) {
        EstimatedVehicleJourney element = new EstimatedVehicleJourney();
        LineRef lineRef = new LineRef();
        lineRef.setValue("");
        element.setLineRef(lineRef);
        VehicleRef vehicleRef = new VehicleRef();
        vehicleRef.setValue("");
        element.setVehicleRef(vehicleRef);

        FramedVehicleJourneyRefStructure framedVehicleJourneyRefStructure = new FramedVehicleJourneyRefStructure();
        framedVehicleJourneyRefStructure.setDatedVehicleJourneyRef(datedVehicleRef);
        element.setFramedVehicleJourneyRef(framedVehicleJourneyRefStructure);

        EstimatedVehicleJourney.EstimatedCalls estimatedCalls = new EstimatedVehicleJourney.EstimatedCalls();
        for (int i = 0; i < 3; i++) {

            StopPointRef stopPointRef = new StopPointRef();
            stopPointRef.setValue("NSR:TEST:"+i);
            EstimatedCall call = new EstimatedCall();
            call.setStopPointRef(stopPointRef);
            call.setOrder(BigInteger.valueOf(i));
            estimatedCalls.getEstimatedCalls().add(call);
        }

        element.setEstimatedCalls(estimatedCalls);
        element.setRecordedAtTime(ZonedDateTime.now());

        return Arrays.asList(element);
    }
}
