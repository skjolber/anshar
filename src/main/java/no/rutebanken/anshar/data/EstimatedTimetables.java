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

package no.rutebanken.anshar.data;

import com.hazelcast.core.IMap;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.metrics.MetricsService;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.quartz.utils.counter.Counter;
import org.quartz.utils.counter.CounterImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import uk.org.siri.siri20.*;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer.SEPARATOR;

@Repository
public class EstimatedTimetables  implements SiriRepository<EstimatedVehicleJourney> {
    private final Logger logger = LoggerFactory.getLogger(EstimatedTimetables.class);

    @Autowired
    private IMap<String, EstimatedVehicleJourney> timetableDeliveries;

    @Autowired
    @Qualifier("getIdForPatternChangesMap")
    private IMap<String, String> idForPatternChanges;

    @Autowired
    @Qualifier("getIdStartTimeMap")
    private IMap<String, ZonedDateTime> idStartTimeMap;

    @Autowired
    @Qualifier("getEstimatedTimetableChangesMap")
    private IMap<String, Set<String>> changesMap;

    @Autowired
    @Qualifier("getLastEtUpdateRequest")
    private IMap<String, Instant> lastUpdateRequested;

    @Autowired
    private MetricsService metricsService;

    @Autowired
    private AnsharConfiguration configuration;

    /**
     * @return All ET-elements
     */
    public Collection<EstimatedVehicleJourney> getAll() {
        return timetableDeliveries.values();
    }

    public int getSize() {
        return timetableDeliveries.size();
    }

    public void clearAll() {
        logger.error("Deleting all data - should only be used in test!!!");
        timetableDeliveries.clear();
    }

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    public Siri createServiceDelivery(String lineRef) {
        SortedSet<EstimatedVehicleJourney> matchingEstimatedVehicleJourneys = new TreeSet<>((o1, o2) -> {
            ZonedDateTime o1_firstTimestamp = o1.getEstimatedCalls().getEstimatedCalls().get(0).getAimedDepartureTime();
            if (o1_firstTimestamp == null) {
                o1_firstTimestamp = o1.getEstimatedCalls().getEstimatedCalls().get(0).getAimedArrivalTime();
            }

            ZonedDateTime o2_firstTimestamp = o2.getEstimatedCalls().getEstimatedCalls().get(0).getAimedDepartureTime();
            if (o2_firstTimestamp == null) {
                o2_firstTimestamp = o2.getEstimatedCalls().getEstimatedCalls().get(0).getAimedArrivalTime();
            }

            if (o1.getRecordedCalls() != null && o1.getRecordedCalls().getRecordedCalls() != null) {
                if (o1.getRecordedCalls().getRecordedCalls().size() > 0) {
                    o1_firstTimestamp = o1.getRecordedCalls().getRecordedCalls().get(0).getAimedDepartureTime();
                }
            }

            if (o2.getRecordedCalls() != null && o2.getRecordedCalls().getRecordedCalls() != null) {
                if (o2.getRecordedCalls().getRecordedCalls().size() > 0) {
                    o2_firstTimestamp = o2.getRecordedCalls().getRecordedCalls().get(0).getAimedDepartureTime();
                }
            }

            return o1_firstTimestamp.compareTo(o2_firstTimestamp);
        });

        timetableDeliveries.keySet()
                .forEach(key -> {
                    EstimatedVehicleJourney vehicleJourney = timetableDeliveries.get(key);
                    if (vehicleJourney != null) { //Object may have expired
                        if (vehicleJourney.getLineRef() != null &&
                                (vehicleJourney.getLineRef().getValue().toLowerCase().startsWith(lineRef.toLowerCase() + SEPARATOR) |
                                vehicleJourney.getLineRef().getValue().toLowerCase().endsWith(SEPARATOR + lineRef.toLowerCase())|
                                vehicleJourney.getLineRef().getValue().equalsIgnoreCase(lineRef))
                                ) {
                            matchingEstimatedVehicleJourneys.add(vehicleJourney);
                        }
                    }
                });

        return siriObjectFactory.createETServiceDelivery(matchingEstimatedVehicleJourneys);
    }

    public Siri createServiceDelivery(String requestorId, String datasetId, int maxSize) {
        return createServiceDelivery(requestorId, datasetId, maxSize, -1);
    }


    public Siri createServiceDelivery(String requestorId, String datasetId, int maxSize, long previewInterval) {

        int trackingPeriodMinutes = configuration.getTrackingPeriodMinutes();

        boolean isAdHocRequest = false;

        if (requestorId == null) {
            requestorId = UUID.randomUUID().toString();
            trackingPeriodMinutes = configuration.getAdHocTrackingPeriodMinutes();
            isAdHocRequest = true;
        }

        // Get all relevant ids
        Set<String> allIds = new HashSet<>();
        Set<String> idSet = changesMap.getOrDefault(requestorId, allIds);

        if (idSet == allIds) {
            timetableDeliveries.keySet().stream()
                    .filter(key -> datasetId == null || key.startsWith(datasetId + ":"))
                    .forEach(idSet::add);
        }

        //Filter by datasetId
        Set<String> requestedIds = idSet.stream()
                .filter(key -> datasetId == null || key.startsWith(datasetId + ":"))
                .collect(Collectors.toSet());

        final ZonedDateTime previewExpiry = ZonedDateTime.now().plusSeconds(previewInterval / 1000);

        Set<String> startTimes = new HashSet<>();

        if (previewInterval >= 0) {
            long t1 = System.currentTimeMillis();
            startTimes.addAll(idStartTimeMap.keySet(entry -> ((ZonedDateTime)entry.getValue()).isBefore(previewExpiry)));
            logger.info("Found {} ids starting within {} ms in {} ms", startTimes.size(), previewInterval, (System.currentTimeMillis()-t1));
        }

        final AtomicInteger previewIntervalInclusionCounter = new AtomicInteger();
        final AtomicInteger previewIntervalExclusionCounter = new AtomicInteger();
        Predicate<? super String> previewIntervalFilter = (Predicate<String>) id -> {

            if (idForPatternChanges.containsKey(id) || startTimes.contains(id)) {
                // Is valid in requested previewInterval
                previewIntervalInclusionCounter.incrementAndGet();
                return true;
            }

            previewIntervalExclusionCounter.incrementAndGet();
            return false;
        };


        Set<String> sizeLimitedIds = requestedIds
                .stream()
                .filter(id -> previewInterval < 0 || previewIntervalFilter.test(id))
                .limit(maxSize)
                .collect(Collectors.toSet());

        //Remove collected objects
        sizeLimitedIds.forEach(idSet::remove);

        Boolean isMoreData = (previewIntervalExclusionCounter.get() + sizeLimitedIds.size()) < requestedIds.size();

        Collection<EstimatedVehicleJourney> values = timetableDeliveries.getAll(sizeLimitedIds).values();
        Siri siri = siriObjectFactory.createETServiceDelivery(values);

        siri.getServiceDelivery().setMoreData(isMoreData);

        if (isAdHocRequest) {
            logger.info("Returning {}, no requestorRef is set", sizeLimitedIds.size());
        } else {

            MessageRefStructure msgRef = new MessageRefStructure();
            msgRef.setValue(requestorId);
            siri.getServiceDelivery().setRequestMessageRef(msgRef);

            //Update change-tracker
            changesMap.set(requestorId, idSet);
            lastUpdateRequested.set(requestorId, Instant.now(), trackingPeriodMinutes, TimeUnit.MINUTES);

            logger.info("Returning {}, {} left for requestorRef {}", sizeLimitedIds.size(), idSet.size(), requestorId);
        }

        return siri;
    }

    /**
     * Returns true if EstimatedVehicleJourney has any cancellations
     * @param estimatedVehicleJourney
     * @return
     */
    private boolean hasPatternChanges(EstimatedVehicleJourney estimatedVehicleJourney) {
        if (estimatedVehicleJourney != null) {
            if (estimatedVehicleJourney.isCancellation() != null && estimatedVehicleJourney.isCancellation()) {
                return true;
            }
            if (estimatedVehicleJourney.isExtraJourney() != null && estimatedVehicleJourney.isExtraJourney()) {
                return true;
            }
            if (estimatedVehicleJourney.getEstimatedCalls() != null && estimatedVehicleJourney.getEstimatedCalls().getEstimatedCalls() != null) {
                List<EstimatedCall> estimatedCalls = estimatedVehicleJourney.getEstimatedCalls().getEstimatedCalls();
                for (EstimatedCall estimatedCall : estimatedCalls) {
                    if (estimatedCall.isCancellation() != null && estimatedCall.isCancellation()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public Collection<EstimatedVehicleJourney> getAllUpdates(String requestorId, String datasetId) {
        if (requestorId != null) {

            Set<String> idSet = changesMap.get(requestorId);
            lastUpdateRequested.set(requestorId, Instant.now(), configuration.getTrackingPeriodMinutes(), TimeUnit.MINUTES);

            if (idSet != null) {
                Set<String> datasetFilteredIdSet = new HashSet<>();

                if (datasetId != null) {
                    idSet.stream().filter(key -> key.startsWith(datasetId + ":")).forEach(datasetFilteredIdSet::add);
                } else {
                    datasetFilteredIdSet.addAll(idSet);
                }

                Collection<EstimatedVehicleJourney> changes = timetableDeliveries.getAll(datasetFilteredIdSet).values();

                Set<String> existingSet = changesMap.get(requestorId);
                if (existingSet == null) {
                    existingSet = new HashSet<>();
                }
                existingSet.removeAll(idSet);
                changesMap.set(requestorId, existingSet);


                logger.info("Returning {} changes to requestorRef {}", changes.size(), requestorId);
                return changes;
            } else {

                logger.info("Returning all to requestorRef {}", requestorId);
                changesMap.set(requestorId, new HashSet<>());
            }
        }

        return getAll(datasetId);
    }

    public Collection<EstimatedVehicleJourney> getAll(String datasetId) {
        if (datasetId == null || datasetId.isEmpty()) {
            return getAll();
        }
        return  timetableDeliveries.values(e -> ((String) e.getKey()).startsWith(datasetId + ":"));
    }

    private ZonedDateTime getFirstAimedTime(EstimatedVehicleJourney vehicleJourney) {

        if (vehicleJourney.getRecordedCalls() != null && !vehicleJourney.getRecordedCalls().getRecordedCalls().isEmpty()) {
            List<RecordedCall> recordedCalls = vehicleJourney.getRecordedCalls().getRecordedCalls();
            RecordedCall firstRecordedCall = recordedCalls.get(0);

            if (firstRecordedCall.getAimedDepartureTime() != null) {
                return firstRecordedCall.getAimedDepartureTime();
            }

            if (firstRecordedCall.getAimedArrivalTime() != null) {
                return firstRecordedCall.getAimedArrivalTime();
            }
        }

        if (vehicleJourney.getEstimatedCalls() != null && !vehicleJourney.getEstimatedCalls().getEstimatedCalls().isEmpty()) {
            List<EstimatedCall> estimatedCalls = vehicleJourney.getEstimatedCalls().getEstimatedCalls();
            EstimatedCall firstEstimatedCall = estimatedCalls.get(0);

            if (firstEstimatedCall.getAimedDepartureTime() != null) {
                return firstEstimatedCall.getAimedDepartureTime();
            }
            if (firstEstimatedCall.getAimedArrivalTime() != null) {
                return firstEstimatedCall.getAimedArrivalTime();
            }
        }

        logger.warn("Unable to find aimed time for VehicleJourney with key {}, returning 'now'", createKey(vehicleJourney.getDataSource(), vehicleJourney));

        return ZonedDateTime.now();
    }

    public long getExpiration(EstimatedVehicleJourney vehicleJourney) {
        ZonedDateTime expiryTimestamp = null;
        if (vehicleJourney != null) {
            if (vehicleJourney.getRecordedCalls() != null && !vehicleJourney.getRecordedCalls().getRecordedCalls().isEmpty()) {
                List<RecordedCall> recordedCalls = vehicleJourney.getRecordedCalls().getRecordedCalls();
                RecordedCall lastRecordedCall = recordedCalls.get(recordedCalls.size() - 1);

                if (lastRecordedCall.getAimedArrivalTime() != null) {
                    expiryTimestamp = lastRecordedCall.getAimedArrivalTime();
                }
                if (lastRecordedCall.getAimedDepartureTime() != null) {
                    expiryTimestamp = lastRecordedCall.getAimedDepartureTime();
                }
                if (lastRecordedCall.getExpectedArrivalTime() != null) {
                    expiryTimestamp = lastRecordedCall.getExpectedArrivalTime();
                }
                if (lastRecordedCall.getExpectedDepartureTime() != null) {
                    expiryTimestamp = lastRecordedCall.getExpectedDepartureTime();
                }

            }
            if (vehicleJourney.getEstimatedCalls() != null && !vehicleJourney.getEstimatedCalls().getEstimatedCalls().isEmpty()) {
                List<EstimatedCall> estimatedCalls = vehicleJourney.getEstimatedCalls().getEstimatedCalls();
                EstimatedCall lastEstimatedCall = estimatedCalls.get(estimatedCalls.size() - 1);

                if (lastEstimatedCall.getAimedArrivalTime() != null) {
                    expiryTimestamp = lastEstimatedCall.getAimedArrivalTime();
                }
                if (lastEstimatedCall.getAimedDepartureTime() != null) {
                    expiryTimestamp = lastEstimatedCall.getAimedDepartureTime();
                }
                if (lastEstimatedCall.getExpectedArrivalTime() != null) {
                    expiryTimestamp = lastEstimatedCall.getExpectedArrivalTime();
                }
                if (lastEstimatedCall.getExpectedDepartureTime() != null) {
                    expiryTimestamp = lastEstimatedCall.getExpectedDepartureTime();
                }
            }
        }

        if (expiryTimestamp != null) {
            return ZonedDateTime.now().until(expiryTimestamp.plus(configuration.getEtGraceperiodMinutes(), ChronoUnit.MINUTES), ChronoUnit.MILLIS);
        } else {
            return -1;
        }
    }


    public Collection<EstimatedVehicleJourney> addAll(String datasetId, List<EstimatedVehicleJourney> etList) {

        Set<String> changes = new HashSet<>();
        Set<EstimatedVehicleJourney> addedData = new HashSet<>();

        Counter outdatedCounter = new CounterImpl(0);

        etList.forEach(et -> {
            String key = createKey(datasetId, et);

            EstimatedVehicleJourney existing = null;
            if (!timetableDeliveries.containsKey(key)){
                mapFutureRecordedCallsToEstimatedCalls(et);
            } else {
                existing = timetableDeliveries.get(key);
            }

            boolean keep = false;

            if (existing != null &&
                    (et.getRecordedAtTime() != null && existing.getRecordedAtTime() != null)) {

                if (et.getRecordedAtTime().isAfter(existing.getRecordedAtTime())) {
                    keep = true;
                } else {
                    logger.info("Newer data has already been processed - ignoring ET-element");
                }
            } else {
                keep = true;
            }


            if (keep) {

                if (et.isIsCompleteStopSequence() != null && !et.isIsCompleteStopSequence()) {
                    //Not complete - merge partial update into existing
                    if (existing != null) {
                        EstimatedVehicleJourney.EstimatedCalls existingEstimatedCallWrapper = existing.getEstimatedCalls();
                        EstimatedVehicleJourney.EstimatedCalls updatedEstimatedCallWrapper = et.getEstimatedCalls();

                        EstimatedVehicleJourney.RecordedCalls existingRecordedCallWrapper = existing.getRecordedCalls();
                        if (existingRecordedCallWrapper == null) {
                            existingRecordedCallWrapper = new EstimatedVehicleJourney.RecordedCalls();
                            existing.setRecordedCalls(existingRecordedCallWrapper);
                        }
                        EstimatedVehicleJourney.RecordedCalls updatedRecordedCallWrapper = et.getRecordedCalls();

                        List<RecordedCall> recordedCallsList = new ArrayList<>();
                        List<EstimatedCall> estimatedCallsList = new ArrayList<>();

                        // Merge existing and updated RecordedCalls
                        if (existingRecordedCallWrapper.getRecordedCalls() != null ) {
                            recordedCallsList.addAll(existingRecordedCallWrapper.getRecordedCalls());
                        }
                        if (updatedRecordedCallWrapper != null && updatedRecordedCallWrapper.getRecordedCalls() != null ) {
                            recordedCallsList.addAll(updatedRecordedCallWrapper.getRecordedCalls());
                        }

                        //Keep estimatedCalls not in RecordedCalls
                        if (existingEstimatedCallWrapper != null && existingEstimatedCallWrapper.getEstimatedCalls() != null ) {
                            for (EstimatedCall call : existingEstimatedCallWrapper.getEstimatedCalls()) {
                                String originalId = getOriginalId(call.getStopPointRef().getValue());

                                if (recordedCallsList.stream().anyMatch(rc -> originalId.equals(getOriginalId(rc.getStopPointRef().getValue())))) {
                                    //EstimatedCall found in RecordedCalls - all previous EstimatedCalls should have been Recorded
                                    for (int i = 0; i < estimatedCallsList.size(); i++) {
                                        recordedCallsList.add(i, mapToRecordedCall(estimatedCallsList.get(i)));
                                    }
                                    estimatedCallsList.clear();
                                } else {
                                    estimatedCallsList.add(call);
                                }
                            }
                        }


                        LinkedHashMap<String, EstimatedCall> estimatedCallsMap = new LinkedHashMap<>();
                        for (EstimatedCall call : estimatedCallsList) {
                            estimatedCallsMap.put(getOriginalId(call.getStopPointRef().getValue()), call);
                        }

                        //Add or replace existing calls
                        if (updatedEstimatedCallWrapper != null && updatedEstimatedCallWrapper.getEstimatedCalls() != null ) {
                            for (EstimatedCall call : updatedEstimatedCallWrapper.getEstimatedCalls()) {
                                estimatedCallsMap.put(getOriginalId(call.getStopPointRef().getValue()), call);
                            }
                        }

                        EstimatedVehicleJourney.EstimatedCalls joinedEstimatedCalls = new EstimatedVehicleJourney.EstimatedCalls();
                        joinedEstimatedCalls.getEstimatedCalls().addAll(estimatedCallsMap.values());

                        EstimatedVehicleJourney.RecordedCalls joinedRecordedCalls = new EstimatedVehicleJourney.RecordedCalls();
                        joinedRecordedCalls.getRecordedCalls().addAll(recordedCallsList);

                        et.setEstimatedCalls(joinedEstimatedCalls);
                        et.setRecordedCalls(joinedRecordedCalls);
                    }
                }

                if (existing != null && existing.isIsCompleteStopSequence() != null) {
                    //If updates are merged in, the journey is still complete...
                    et.setIsCompleteStopSequence(existing.isIsCompleteStopSequence());
                }

                long expiration = getExpiration(et);
                if (expiration > 0) {
                    //Ignoring elements without EstimatedCalls
                    if (et.getEstimatedCalls() != null &&
                            et.getEstimatedCalls().getEstimatedCalls() != null &&
                            !et.getEstimatedCalls().getEstimatedCalls().isEmpty()) {
                        changes.add(key);
                        addedData.add(et);
                        timetableDeliveries.set(key, et, expiration, TimeUnit.MILLISECONDS);

                        if (hasPatternChanges(et)) {
                            // Keep track of all valid ET with pattern-changes
                            idForPatternChanges.set(key, key, expiration, TimeUnit.MILLISECONDS);
                        }

                        idStartTimeMap.set(key, getFirstAimedTime(et), expiration, TimeUnit.MILLISECONDS);
                    }
                } else {
                    outdatedCounter.increment();
                }

            }
        });

        logger.info("Updated {} (of {}), {} outdated", changes.size(), etList.size(), outdatedCounter.getValue());
        metricsService.registerIncomingData(SiriDataType.ESTIMATED_TIMETABLE, datasetId, timetableDeliveries);

        changesMap.keySet().forEach(requestor -> {
            if (lastUpdateRequested.get(requestor) != null) {
                Set<String> tmpChanges = changesMap.get(requestor);
                tmpChanges.addAll(changes);
                changesMap.set(requestor, tmpChanges);
            } else {
                changesMap.remove(requestor);
            }
        });

        return addedData;
    }

    RecordedCall mapToRecordedCall(EstimatedCall call) {
        RecordedCall recordedCall = new RecordedCall();

        recordedCall.setStopPointRef(call.getStopPointRef());
        recordedCall.getStopPointNames().addAll(call.getStopPointNames());

        recordedCall.setOrder(call.getOrder());
        recordedCall.setVisitNumber(call.getVisitNumber());
        recordedCall.setCancellation(call.isCancellation());
        recordedCall.setExtraCall(call.isExtraCall());
        recordedCall.setExtensions(call.getExtensions());

        recordedCall.setAimedArrivalTime(call.getAimedArrivalTime());
        recordedCall.setExpectedArrivalTime(call.getExpectedArrivalTime());
        if (recordedCall.getExpectedArrivalTime() != null) {
            //Setting actual arrival from expected
            recordedCall.setActualArrivalTime(call.getExpectedArrivalTime());
        }
        recordedCall.setArrivalPlatformName(call.getArrivalPlatformName());

        recordedCall.setAimedDepartureTime(call.getAimedDepartureTime());
        recordedCall.setExpectedDepartureTime(call.getExpectedDepartureTime());
        if (recordedCall.getExpectedDepartureTime() != null) {
            //Setting actual departure from expected
            recordedCall.setActualDepartureTime(call.getExpectedDepartureTime());
        }
        recordedCall.setDeparturePlatformName(call.getDeparturePlatformName());
        return recordedCall;
    }


    /**
     * Temporary fix to handle future VehicleJourneys where all stations are put in RecordedCalls
     *
     * This is necessary because train-operators need to flag trains as "arrived" to keep information-displays correct.
     *
     * Follow up is registered in NRP-2286
     *
     * @param et
     */
    private void mapFutureRecordedCallsToEstimatedCalls(EstimatedVehicleJourney et) {
        if (et.getRecordedCalls() != null && et.getRecordedCalls().getRecordedCalls() != null && et.getRecordedCalls().getRecordedCalls().size() > 0 &&
                (et.getEstimatedCalls() == null || (et.getEstimatedCalls().getEstimatedCalls() != null && et.getEstimatedCalls().getEstimatedCalls().size() == 0))) {

            List<RecordedCall> predictedRecordedCalls = et.getRecordedCalls().getRecordedCalls();
            List<RecordedCall> recordedCalls = new ArrayList<>();
            List<EstimatedCall> estimatedCalls = new ArrayList<>();
            boolean estimatedCallsFromHere = false;
            for (RecordedCall recordedCall : predictedRecordedCalls) {

                if (estimatedCallsFromHere ||
                        (recordedCall.getAimedDepartureTime() != null && recordedCall.getAimedDepartureTime().isAfter(ZonedDateTime.now())) ||
                        (recordedCall.getExpectedDepartureTime() != null && recordedCall.getExpectedDepartureTime().isAfter(ZonedDateTime.now())) ||
                        (recordedCall.getAimedArrivalTime() != null && recordedCall.getAimedArrivalTime().isAfter(ZonedDateTime.now())) ||
                        (recordedCall.getExpectedArrivalTime() != null && recordedCall.getExpectedArrivalTime().isAfter(ZonedDateTime.now()))
                        ) {

                    //When the first estimatedCall is discovered - all remaining calls should be added as estimated
                    estimatedCallsFromHere = true;

                    EstimatedCall call = new EstimatedCall();

                    call.setStopPointRef(recordedCall.getStopPointRef());
                    call.getStopPointNames().addAll(recordedCall.getStopPointNames());
                    call.setOrder(recordedCall.getOrder());
                    call.setVisitNumber(recordedCall.getVisitNumber());

                    call.setAimedArrivalTime(recordedCall.getAimedArrivalTime());
                    call.setExpectedArrivalTime(recordedCall.getExpectedArrivalTime());
                    if (call.getExpectedArrivalTime() == null) {
                        call.setExpectedArrivalTime(recordedCall.getActualArrivalTime());
                    }

                    call.setArrivalPlatformName(recordedCall.getArrivalPlatformName());

                    call.setAimedDepartureTime(recordedCall.getAimedDepartureTime());
                    call.setExpectedDepartureTime(recordedCall.getExpectedDepartureTime());
                    if (call.getExpectedDepartureTime() == null) {
                        call.setExpectedDepartureTime(recordedCall.getActualDepartureTime());
                    }
                    call.setDeparturePlatformName(recordedCall.getDeparturePlatformName());

                    estimatedCalls.add(call);
                } else {
                    recordedCalls.add(recordedCall);
                }
            }
            if (estimatedCalls.size() > 0) {
                logger.warn("Remapped {} RecordedCalls to {} RecordedCalls and {} EstimatedCalls for Line: {}, VehicleRef: {}",
                        predictedRecordedCalls.size(), recordedCalls.size(), estimatedCalls.size(),
                        (et.getLineRef() != null ? et.getLineRef().getValue():null), (et.getVehicleRef() != null ? et.getVehicleRef().getValue():null));
            }
            et.getRecordedCalls().getRecordedCalls().clear();
            et.getRecordedCalls().getRecordedCalls().addAll(recordedCalls);
            et.setEstimatedCalls(new EstimatedVehicleJourney.EstimatedCalls());
            et.getEstimatedCalls().getEstimatedCalls().addAll(estimatedCalls);
        }
    }

    private String getOriginalId(String stopPointRef) {
        if (stopPointRef != null && stopPointRef.indexOf(SEPARATOR) > 0) {
            return OutboundIdAdapter.getOriginalId(stopPointRef);
        }
        return stopPointRef;
    }


    public EstimatedVehicleJourney add(String datasetId, EstimatedVehicleJourney delivery) {
        if (delivery == null) {return null;}

        List<EstimatedVehicleJourney> deliveries = new ArrayList<>();
        deliveries.add(delivery);
        addAll(datasetId, deliveries);
        return timetableDeliveries.get(createKey(datasetId, delivery));
    }

    private static String createKey(String datasetId, EstimatedVehicleJourney element) {
        StringBuilder key = new StringBuilder();

        String datedVehicleJourney = element.getDatedVehicleJourneyRef() != null ? element.getDatedVehicleJourneyRef().getValue() : null;
        if (datedVehicleJourney == null && element.getFramedVehicleJourneyRef() != null) {
            String dataFrameRef = element.getFramedVehicleJourneyRef().getDataFrameRef() != null ? element.getFramedVehicleJourneyRef().getDataFrameRef().getValue():"null";

            key.append(datasetId).append(":")
                    .append(dataFrameRef)
                    .append(":")
                    .append(element.getFramedVehicleJourneyRef().getDatedVehicleJourneyRef());
        } else {
            key.append(datasetId).append(":")
                    .append((element.getOperatorRef() != null ? element.getOperatorRef().getValue() : "null"))
                    .append(":")
                    .append((element.getLineRef() != null ? element.getLineRef().getValue() : "null"))
                    .append(":")
                    .append((element.getVehicleRef() != null ? element.getVehicleRef().getValue() : "null"))
                    .append(":")
                    .append((element.getDirectionRef() != null ? element.getDirectionRef().getValue() : "null"))
                    .append(":")
                    .append(datedVehicleJourney);
        }

        return key.toString();
    }
}
