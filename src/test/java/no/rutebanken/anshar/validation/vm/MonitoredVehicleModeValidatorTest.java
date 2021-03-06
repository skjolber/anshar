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

package no.rutebanken.anshar.validation.vm;

import no.rutebanken.anshar.routes.validation.validators.vm.MonitoredVehicleModeValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.BeforeClass;
import org.junit.Test;
import uk.org.siri.siri20.VehicleModesEnumeration;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;

public class MonitoredVehicleModeValidatorTest extends CustomValidatorTest {

    private static MonitoredVehicleModeValidator validator;
    private final String fieldName = "VehicleMode";

    @BeforeClass
    public static void init() {
        validator = new MonitoredVehicleModeValidator();
    }



    @Test
    public void testValidModes() throws Exception {
        assertNull(validator.isValid(createXmlNode(fieldName, VehicleModesEnumeration.AIR.value())));
        assertNull(validator.isValid(createXmlNode(fieldName, VehicleModesEnumeration.BUS.value())));
        assertNull(validator.isValid(createXmlNode(fieldName, VehicleModesEnumeration.COACH.value())));
        assertNull(validator.isValid(createXmlNode(fieldName, VehicleModesEnumeration.FERRY.value())));
        assertNull(validator.isValid(createXmlNode(fieldName, VehicleModesEnumeration.METRO.value())));
        assertNull(validator.isValid(createXmlNode(fieldName, VehicleModesEnumeration.RAIL.value())));
        assertNull(validator.isValid(createXmlNode(fieldName, VehicleModesEnumeration.TRAM.value())));
    }

    @Test
    public void testInvalidModes() throws Exception {
        assertNotNull(validator.isValid(createXmlNode(fieldName, VehicleModesEnumeration.UNDERGROUND.value())));
        assertNotNull("non-enum value flagged as valid", validator.isValid(createXmlNode(fieldName, "testing")));
    }
}
