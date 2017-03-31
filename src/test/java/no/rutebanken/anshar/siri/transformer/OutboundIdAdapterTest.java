package no.rutebanken.anshar.siri.transformer;

import no.rutebanken.anshar.routes.siri.handlers.IdMappingPolicy;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter;
import org.junit.Test;
import uk.org.siri.siri20.LineRef;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;

public class OutboundIdAdapterTest {

    @Test
    public void testEmptyString() throws Exception {
        OutboundIdAdapter adapter = new OutboundIdAdapter(LineRef.class, IdMappingPolicy.ORIGINAL_ID);
        assertEquals("", adapter.apply(""));
    }
    @Test
    public void testGetOriginalValueString() throws Exception {
        OutboundIdAdapter adapter = new OutboundIdAdapter(LineRef.class,IdMappingPolicy.ORIGINAL_ID);
        String originalId = "1234";
        String mappedId = "ATB:Line:1234";
        String completeValue = originalId + SiriValueTransformer.SEPARATOR + mappedId;
        assertEquals(originalId, adapter.apply(completeValue));
        assertEquals(originalId, OutboundIdAdapter.getOriginalId(completeValue));
    }

    @Test
    public void testGetMappedValueString() throws Exception {
        OutboundIdAdapter adapter = new OutboundIdAdapter(LineRef.class, IdMappingPolicy.DEFAULT);
        String originalId = "1234";
        String mappedId = "ATB:Line:1234";
        String completeValue = originalId + SiriValueTransformer.SEPARATOR + mappedId;
        assertEquals(mappedId, adapter.apply(completeValue));
        assertEquals(mappedId, OutboundIdAdapter.getMappedId(completeValue));
    }

    @Test
    public void testGetOtpFriendly() throws Exception {
        OutboundIdAdapter adapter = new OutboundIdAdapter(LineRef.class, IdMappingPolicy.OTP_FRIENDLY_ID);
        String originalId = "1234";
        String mappedId = "ATB:Line:1234";
        String otpSpecificId = mappedId.replaceAll(":", ".");

        String completeValue = originalId + SiriValueTransformer.SEPARATOR + mappedId;
        assertEquals(otpSpecificId, adapter.apply(completeValue));
        assertEquals(otpSpecificId, OutboundIdAdapter.getOtpFriendly(completeValue));
    }

    @Test
    public void testPrefixNullString() throws Exception {
        OutboundIdAdapter adapter = new OutboundIdAdapter(LineRef.class,IdMappingPolicy.DEFAULT);
        assertNull(adapter.apply(null));
    }
}
