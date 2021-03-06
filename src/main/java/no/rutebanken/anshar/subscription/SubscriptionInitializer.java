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

package no.rutebanken.anshar.subscription;

import com.google.common.base.Preconditions;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.siri.*;
import no.rutebanken.anshar.routes.siri.adapters.Mapping;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.routes.siri.processor.CodespaceProcessor;
import no.rutebanken.anshar.routes.siri.processor.ReportTypeProcessor;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;

@Service
public class SubscriptionInitializer implements CamelContextAware, ApplicationContextAware {
    private final Logger logger = LoggerFactory.getLogger(SubscriptionInitializer.class);

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private SubscriptionConfig subscriptionConfig;

    @Autowired
    private SiriHandler handler;

    @Autowired
    private AnsharConfiguration configuration;

    private CamelContext camelContext;

    private ApplicationContext applicationContext;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @PostConstruct
    void createSubscriptions() {
        camelContext.setUseMDCLogging(true);


        final Map<String, Object> myFoos = applicationContext.getBeansWithAnnotation(Mapping.class);
        final Map<String, Class> mappingAdaptersById = new HashMap<>();
        for (final Object myFoo : myFoos.values()) {
            final Class<?> mappingAdapterClass = myFoo.getClass();
            final Mapping annotation = mappingAdapterClass.getAnnotation(Mapping.class);
            mappingAdaptersById.put(annotation.id(), mappingAdapterClass);
        }

        logger.info("Initializing subscriptions for environment: {}", configuration.getEnvironment());

        if (subscriptionConfig != null) {
            List<SubscriptionSetup> subscriptionSetups = subscriptionConfig.getSubscriptions();
            logger.info("Initializing {} subscriptions", subscriptionSetups.size());
            Set<String> subscriptionIds = new HashSet<>();

            List<SubscriptionSetup> actualSubscriptionSetups = new ArrayList<>();

            // Validation and consistency-verification
            for (SubscriptionSetup subscriptionSetup : subscriptionSetups) {
                if (subscriptionSetup.getOverrideHttps() && configuration.getInboundUrl().startsWith("https://")) {
                    subscriptionSetup.setAddress(configuration.getInboundUrl().replaceFirst("https:", "http:"));
                } else {
                    subscriptionSetup.setAddress(configuration.getInboundUrl());
                }

                if (!isValid(subscriptionSetup)) {
                    throw new ServiceConfigurationError("Configuration is not valid for subscription " + subscriptionSetup);
                }

                if (subscriptionIds.contains(subscriptionSetup.getSubscriptionId())) {
                    //Verify subscriptionId-uniqueness
                    throw new ServiceConfigurationError("SubscriptionIds are NOT unique for ID="+subscriptionSetup.getSubscriptionId());
                }


                if (mappingAdaptersById.containsKey(subscriptionSetup.getMappingAdapterId())) {
                    Class adapterClass = mappingAdaptersById.get(subscriptionSetup.getMappingAdapterId());
                    try {
                        List<ValueAdapter> valueAdapters = (List<ValueAdapter>) adapterClass.getMethod("getValueAdapters", SubscriptionSetup.class).invoke(adapterClass.newInstance(), subscriptionSetup);

                        //Is added to ALL subscriptions
                        valueAdapters.add(new CodespaceProcessor(subscriptionSetup.getDatasetId()));
                        valueAdapters.add(new ReportTypeProcessor());

                        subscriptionSetup.getMappingAdapters().addAll(valueAdapters);
                    } catch (Exception e) {
                        throw new ServiceConfigurationError("Invalid mappingAdapterId for subscription " + subscriptionSetup, e);
                    }
                }

                if (subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.FETCHED_DELIVERY |
                        subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.POLLING_FETCHED_DELIVERY) {

                    //Fetched delivery needs both subscribe-route and ServiceRequest-route
                    String url = subscriptionSetup.getUrlMap().get(RequestType.SUBSCRIBE);

                    subscriptionSetup.getUrlMap().putIfAbsent(RequestType.GET_ESTIMATED_TIMETABLE, url);
                    subscriptionSetup.getUrlMap().putIfAbsent(RequestType.GET_VEHICLE_MONITORING, url);
                    subscriptionSetup.getUrlMap().putIfAbsent(RequestType.GET_SITUATION_EXCHANGE, url);
                }

                SubscriptionSetup existingSubscription = subscriptionManager.getSubscriptionById(subscriptionSetup.getInternalId());

                if (existingSubscription != null) {
                    if (!existingSubscription.equals(subscriptionSetup)) {
                        logger.info("Subscription with internalId={} is updated - reinitializing. {}", subscriptionSetup.getInternalId(), subscriptionSetup);

                        subscriptionSetup.setSubscriptionId(existingSubscription.getSubscriptionId());

                        // Keeping subscription active/inactive
                        subscriptionSetup.setActive(existingSubscription.isActive());
                        subscriptionManager.addSubscription(existingSubscription.getSubscriptionId(), subscriptionSetup);

                        if (existingSubscription.isActive()) {
                            subscriptionManager.activatePendingSubscription(existingSubscription.getSubscriptionId());
                        }

                        actualSubscriptionSetups.add(subscriptionSetup);
                        subscriptionIds.add(subscriptionSetup.getSubscriptionId());
                    } else {
                        logger.info("Subscription with internalId={} already registered - keep existing. {}", subscriptionSetup.getInternalId(), subscriptionSetup);
                        actualSubscriptionSetups.add(existingSubscription);
                        subscriptionIds.add(existingSubscription.getSubscriptionId());
                    }
                } else {
                    actualSubscriptionSetups.add(subscriptionSetup);
                    subscriptionIds.add(subscriptionSetup.getSubscriptionId());
                }

            }

            for (SubscriptionSetup subscriptionSetup : actualSubscriptionSetups) {

                try {

                    List<RouteBuilder> routeBuilder = getRouteBuilders(subscriptionSetup);
                    //Adding all routes to current context
                    for (RouteBuilder builder : routeBuilder) {
                        camelContext.addRoutes(builder);
                    }

                } catch (Exception e) {
                    logger.warn("Could not add subscription", e);
                }
            }

            for (SubscriptionSetup subscriptionSetup : actualSubscriptionSetups) {
                if (!subscriptionManager.isSubscriptionRegistered(subscriptionSetup.getSubscriptionId())) {
                    subscriptionManager.addSubscription(subscriptionSetup.getSubscriptionId(), subscriptionSetup);
                }
            }
        } else {
            logger.error("Subscriptions not configured correctly - no subscriptions will be started");
        }

    }

    List<RouteBuilder> getRouteBuilders(SubscriptionSetup subscriptionSetup) {
        List<RouteBuilder> routeBuilders = new ArrayList<>();

        boolean isSubscription = subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.SUBSCRIBE;
        boolean isFetchedDelivery = subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.FETCHED_DELIVERY |
                                subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.POLLING_FETCHED_DELIVERY;
        boolean isSoap = subscriptionSetup.getServiceType() == SubscriptionSetup.ServiceType.SOAP;

        if (subscriptionSetup.getVersion().equals("1.4")) {
            if (isSoap) {
                if (isSubscription | isFetchedDelivery) {
                    routeBuilders.add(new Siri20ToSiriWS14Subscription(configuration, handler, subscriptionSetup, subscriptionManager));
                } else {
                    routeBuilders.add(new Siri20ToSiriWS14RequestResponse(configuration, subscriptionSetup, subscriptionManager));
                }
                if (isFetchedDelivery) {
                    routeBuilders.add(new Siri20ToSiriWS14RequestResponse(configuration, subscriptionSetup, subscriptionManager));
                }
            } else {
                routeBuilders.add(new Siri20ToSiriRS14Subscription(configuration, handler, subscriptionSetup, subscriptionManager));
            }
        } else {
            if (isSoap) {
                if (isSubscription | isFetchedDelivery) {
                    routeBuilders.add(new Siri20ToSiriWS20Subscription(configuration, handler, subscriptionSetup, subscriptionManager));

                    if (isFetchedDelivery | subscriptionSetup.isDataSupplyRequestForInitialDelivery()) {
                        routeBuilders.add(new Siri20ToSiriWS20RequestResponse(configuration, subscriptionSetup, subscriptionManager));
                    }
                } else {
                    routeBuilders.add(new Siri20ToSiriWS20RequestResponse(configuration, subscriptionSetup, subscriptionManager));
                }
            } else {
                if (isSubscription | isFetchedDelivery) {
                    routeBuilders.add(new Siri20ToSiriRS20Subscription(configuration, handler, subscriptionSetup, subscriptionManager));

                    if (isFetchedDelivery | subscriptionSetup.isDataSupplyRequestForInitialDelivery()) {
                        routeBuilders.add(new Siri20ToSiriRS20RequestResponse(configuration, subscriptionSetup, subscriptionManager));
                    }
                } else {
                    routeBuilders.add(new Siri20ToSiriRS20RequestResponse(configuration, subscriptionSetup, subscriptionManager));
                }
            }
        }
        return routeBuilders;
    }

    private boolean isValid(SubscriptionSetup s) {
        Preconditions.checkNotNull(s.getVendor(), "Vendor is not set");
        Preconditions.checkNotNull(s.getDatasetId(), "DatasetId is not set");
        Preconditions.checkNotNull(s.getServiceType(), "ServiceType is not set");
        Preconditions.checkNotNull(s.getSubscriptionType(), "SubscriptionType is not set");
        Preconditions.checkNotNull(s.getVersion(), "Version is not set");
        Preconditions.checkNotNull(s.getSubscriptionId(), "SubscriptionId is not set");
        Preconditions.checkNotNull(s.getRequestorRef(), "RequestorRef is not set");
        Preconditions.checkNotNull(s.getSubscriptionMode(), "SubscriptionMode is not set");
        Preconditions.checkNotNull(s.getContentType(), "ContentType is not set");

        Preconditions.checkNotNull(s.getDurationOfSubscription(), "Duration is not set");
        Preconditions.checkState(s.getDurationOfSubscription().toMillis() > 0, "Duration must be > 0");

        Preconditions.checkNotNull(s.getHeartbeatInterval(), "HeartbeatInterval is not set");
        Preconditions.checkState(s.getHeartbeatInterval().toMillis() > 0, "HeartbeatInterval must be > 0");

        Preconditions.checkNotNull(s.getUrlMap(), "UrlMap is not set");
        Map<RequestType, String> urlMap = s.getUrlMap();
        if (s.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE) {

            if (SiriDataType.SITUATION_EXCHANGE.equals(s.getSubscriptionType())) {
                Preconditions.checkNotNull(urlMap.get(RequestType.GET_SITUATION_EXCHANGE), "GET_SITUATION_EXCHANGE-url is missing. " + s);
            } else if (SiriDataType.VEHICLE_MONITORING.equals(s.getSubscriptionType())) {
                Preconditions.checkNotNull(urlMap.get(RequestType.GET_VEHICLE_MONITORING), "GET_VEHICLE_MONITORING-url is missing. " + s);
            } else if (SiriDataType.ESTIMATED_TIMETABLE.equals(s.getSubscriptionType())) {
                Preconditions.checkNotNull(urlMap.get(RequestType.GET_ESTIMATED_TIMETABLE), "GET_ESTIMATED_TIMETABLE-url is missing. " + s);
            } else {
                Preconditions.checkArgument(false, "URLs not configured correctly");
            }
        } else if (s.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.SUBSCRIBE) {

            //Type-specific requirements
            if (SiriDataType.ESTIMATED_TIMETABLE.equals(s.getSubscriptionType())) {
                Preconditions.checkNotNull(s.getPreviewInterval(), "PreviewInterval is not set");
            } else if (SiriDataType.SITUATION_EXCHANGE.equals(s.getSubscriptionType())) {
                Preconditions.checkNotNull(s.getPreviewInterval(), "PreviewInterval is not set");
            }

            Preconditions.checkNotNull(urlMap.get(RequestType.SUBSCRIBE), "SUBSCRIBE-url is missing. " + s);
            Preconditions.checkNotNull(urlMap.get(RequestType.DELETE_SUBSCRIPTION), "DELETE_SUBSCRIPTION-url is missing. " + s);
        }  else if (s.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.FETCHED_DELIVERY |
                s.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.POLLING_FETCHED_DELIVERY) {
            Preconditions.checkNotNull(urlMap.get(RequestType.SUBSCRIBE), "SUBSCRIBE-url is missing. " + s);
            Preconditions.checkNotNull(urlMap.get(RequestType.DELETE_SUBSCRIPTION), "DELETE_SUBSCRIPTION-url is missing. " + s);
        } else {
            Preconditions.checkArgument(false, "Subscription mode not configured");
        }

        return true;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
