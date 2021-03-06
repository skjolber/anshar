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

package no.rutebanken.anshar.routes.siri.processor;

import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import uk.org.siri.siri20.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri20.EstimatedVersionFrameStructure;
import uk.org.siri.siri20.Siri;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OperatorFilterPostProcessor extends ValueAdapter implements PostProcessor {
    private final List<String> operatorsToIgnore;

    /**
     *
     * @param operatorsToIgnore List of OperatorRef-values to remove
     * @param operatorOverrideMapping Defines Operator-override if original should not be used.
     */
    public OperatorFilterPostProcessor(List<String> operatorsToIgnore, Map<String, String> operatorOverrideMapping) {
        this.operatorsToIgnore = operatorsToIgnore;
        this.operatorOverrideMapping = operatorOverrideMapping;
    }

    private Map<String, String> operatorOverrideMapping = new HashMap<>();

    @Override
    protected String apply(String value) {
        return null;
    }

    @Override
    public void process(Siri siri) {
        if (siri != null && siri.getServiceDelivery() != null && siri.getServiceDelivery().getEstimatedTimetableDeliveries() != null) {
            List<EstimatedTimetableDeliveryStructure> estimatedTimetableDeliveries = siri.getServiceDelivery().getEstimatedTimetableDeliveries();
            for (EstimatedTimetableDeliveryStructure estimatedTimetableDelivery : estimatedTimetableDeliveries) {
                if (estimatedTimetableDeliveries != null) {
                    List<EstimatedVersionFrameStructure> estimatedJourneyVersionFrames = estimatedTimetableDelivery.getEstimatedJourneyVersionFrames();
                    if (estimatedJourneyVersionFrames != null) {
                        for (EstimatedVersionFrameStructure estimatedVersionFrameStructure : estimatedJourneyVersionFrames) {
                            if (estimatedVersionFrameStructure != null) {

                                if (operatorsToIgnore != null && !operatorsToIgnore.isEmpty()) {
                                    estimatedVersionFrameStructure.getEstimatedVehicleJourneies()
                                            .removeIf(et -> et.getOperatorRef() != null && operatorsToIgnore.contains(et.getOperatorRef().getValue()));
                                }

                                estimatedVersionFrameStructure.getEstimatedVehicleJourneies()
                                        .forEach(et -> {
                                            if (et.getLineRef() != null && et.getOperatorRef() != null) {
                                                String lineRef = et.getLineRef().getValue();
                                                if (lineRef != null) {
                                                    String operatorRef = et.getOperatorRef().getValue();

                                                    String updatedOperatorRef;
                                                    if (lineRef.contains(":Line:")) {
                                                        updatedOperatorRef = lineRef;
                                                    } else {
                                                        updatedOperatorRef = operatorOverrideMapping.getOrDefault(operatorRef, operatorRef) + ":Line:" + lineRef;
                                                    }

                                                    et.getLineRef().setValue(lineRef + SiriValueTransformer.SEPARATOR + updatedOperatorRef);
                                                }
                                            }
                                        });
                            }
                        }
                    }
                }
            }
        }
    }
}
