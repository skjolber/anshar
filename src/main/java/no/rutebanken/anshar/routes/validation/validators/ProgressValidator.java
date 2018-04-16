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

package no.rutebanken.anshar.routes.validation.validators;

import com.google.common.collect.Sets;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;
import uk.org.siri.siri20.WorkflowStatusEnumeration;

import javax.xml.bind.ValidationEvent;
import java.util.Set;

import static no.rutebanken.anshar.routes.validation.validators.Constants.PT_SITUATION_ELEMENT;

@Validator(profileName = "norway", targetType = SiriDataType.SITUATION_EXCHANGE)
@Component
public class ProgressValidator extends CustomValidator {


    private static final String FIELDNAME = "Progress";
    private static final String path = PT_SITUATION_ELEMENT + "/" + FIELDNAME;

    static Set<String> expectedValues = Sets.newHashSet(WorkflowStatusEnumeration.OPEN.value(), WorkflowStatusEnumeration.CLOSED.value());

    @Override
    public String getXpath() {
        return path;
    }

    @Override
    public ValidationEvent isValid(Node node) {
        String nodeValue = getNodeValue(node);

        if (nodeValue != null && !expectedValues.contains(nodeValue)) {
            return  createEvent(node, FIELDNAME, expectedValues, nodeValue, ValidationEvent.WARNING);
        }

        return null;
    }

}
