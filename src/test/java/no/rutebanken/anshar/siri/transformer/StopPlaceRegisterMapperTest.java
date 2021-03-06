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

package no.rutebanken.anshar.siri.transformer;

import no.rutebanken.anshar.App;
import no.rutebanken.anshar.routes.health.HealthManager;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.routes.siri.transformer.impl.StopPlaceRegisterMapper;
import no.rutebanken.anshar.routes.siri.transformer.impl.StopPlaceUpdaterService;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import uk.org.siri.siri20.JourneyPlaceRefStructure;

import java.util.*;

import static junit.framework.TestCase.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(webEnvironment= SpringBootTest.WebEnvironment.MOCK, classes = App.class)
public class StopPlaceRegisterMapperTest {

    private Map<String, String> stopPlaceMap;

    @Before
    public void setUp() throws Exception {

        stopPlaceMap = new HashMap<>();
        stopPlaceMap.put("1234", "NSR:QUAY:11223344");
        stopPlaceMap.put("ABC:Quay:1234", "NSR:QUAY:11223344");
        stopPlaceMap.put("ABC:Quay:2345", "NSR:QUAY:22334455");
        stopPlaceMap.put("ABC:Quay:3456", "NSR:QUAY:33445566");
        stopPlaceMap.put("ABC:Quay:4567", "NSR:QUAY:44556677");
        stopPlaceMap.put("ABC:Quay:5678", "NSR:QUAY:55667788");
        stopPlaceMap.put("XYZ:Quay:5555", "NSR:QUAY:44444444");

        StopPlaceUpdaterService stopPlaceService = ApplicationContextHolder.getContext().getBean(StopPlaceUpdaterService.class);

        //Manually adding custom mapping to Spring context
        stopPlaceService.addStopPlaceMappings(stopPlaceMap);
    }

    @Test
    public void testNoPrefixMapping() {

        List<String> prefixes = new ArrayList<>();

        StopPlaceRegisterMapper mapper = new StopPlaceRegisterMapper(SiriDataType.VEHICLE_MONITORING, "TST",JourneyPlaceRefStructure.class, prefixes);

        assertEquals("NSR:QUAY:11223344", mapper.apply("1234"));
    }

    @Test
    public void testSimpleMapping() {

        List<String> prefixes = new ArrayList<>();
        prefixes.add("ABC");

        StopPlaceUpdaterService stopPlaceService = ApplicationContextHolder.getContext().getBean(StopPlaceUpdaterService.class);
        stopPlaceService.addStopPlaceMappings(stopPlaceMap);

        StopPlaceRegisterMapper mapper = new StopPlaceRegisterMapper(SiriDataType.VEHICLE_MONITORING, "TST",JourneyPlaceRefStructure.class, prefixes);

        assertEquals("NSR:QUAY:11223344", mapper.apply("1234"));
    }

    @Test
    public void testMultiplePrefixes() {

        List<String> prefixes = new ArrayList<>();
        prefixes.add("ABC");
        prefixes.add("XYZ");

        StopPlaceUpdaterService stopPlaceService = ApplicationContextHolder.getContext().getBean(StopPlaceUpdaterService.class);
        stopPlaceService.addStopPlaceMappings(stopPlaceMap);

        StopPlaceRegisterMapper mapper = new StopPlaceRegisterMapper(SiriDataType.VEHICLE_MONITORING, "TST",JourneyPlaceRefStructure.class, prefixes);

        assertEquals("NSR:QUAY:44444444", mapper.apply("5555"));
    }


    @Test
    public void testDuplicatePrefixMapping() {

        List<String> prefixes = new ArrayList<>();

        StopPlaceRegisterMapper mapper = new StopPlaceRegisterMapper(SiriDataType.VEHICLE_MONITORING, "TST",JourneyPlaceRefStructure.class, prefixes);

        assertEquals("NSR:QUAY:11223344", mapper.apply("NSR:QUAY:11223344"));
    }

    @Test
    public void testUnmappedThenMapped() {

        stopPlaceMap = new HashMap<>();

        StopPlaceUpdaterService stopPlaceService = ApplicationContextHolder.getContext().getBean(StopPlaceUpdaterService.class);

        //Manually adding custom mapping to Spring context
        stopPlaceService.addStopPlaceMappings(stopPlaceMap);


        HealthManager healthManager = ApplicationContextHolder.getContext().getBean(HealthManager.class);

        List<String> prefixes = new ArrayList<>();

        String datasetId = "TST_" + System.currentTimeMillis();
        String originalId = "4321";
        String mappedId = "NSR:QUAY:44332211";

        StopPlaceRegisterMapper mapper = new StopPlaceRegisterMapper(SiriDataType.VEHICLE_MONITORING, datasetId,JourneyPlaceRefStructure.class, prefixes);

        assertEquals(originalId, mapper.apply(originalId));

        Map<SiriDataType, Set<String>> unmappedIds = healthManager.getUnmappedIds(datasetId);

        assertEquals(1, unmappedIds.size());

        Set<String> ids = unmappedIds.get(SiriDataType.VEHICLE_MONITORING);
        assertEquals(1, ids.size());
        assertEquals(originalId, ids.iterator().next());


        //Add new mapping-value
        stopPlaceMap.put(originalId, mappedId);
        stopPlaceService.addStopPlaceMappings(stopPlaceMap);

        assertEquals(mappedId, mapper.apply(originalId));

        unmappedIds = healthManager.getUnmappedIds(datasetId);
        assertEquals(1, unmappedIds.size());
        ids = unmappedIds.get(SiriDataType.VEHICLE_MONITORING);
        assertEquals(0, ids.size());

    }
}