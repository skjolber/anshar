package no.rutebanken.anshar.routes.siri.adapters;

import no.rutebanken.anshar.routes.siri.processor.ReportTypeFilterPostProcessor;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.JbvCodeMapper;
import no.rutebanken.anshar.subscription.models.Subscription;
import uk.org.siri.siri20.StopPointRef;

import java.util.ArrayList;
import java.util.List;

@Mapping(id="nsbsx")
public class NsbSxValueAdapters extends MappingAdapter {


    @Override
    public List<ValueAdapter> getValueAdapters(Subscription subscription) {

        List<ValueAdapter> valueAdapters = new ArrayList<>();
        valueAdapters.add(new JbvCodeMapper(StopPointRef.class));
        valueAdapters.add(new ReportTypeFilterPostProcessor("incident"));

        return valueAdapters;
    }
}
