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

package no.rutebanken.anshar.data.collections;

import no.rutebanken.anshar.config.AnsharConfiguration;
import org.rutebanken.hazelcasthelper.service.KubernetesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public final class ExtendedKubernetesService extends KubernetesService {
    private static final Logger log = LoggerFactory.getLogger(ExtendedKubernetesService.class);

    public ExtendedKubernetesService(@Autowired AnsharConfiguration cfg) {
        super(cfg.getKubernetesUrl(), cfg.getNamespace(), cfg.isKubernetesEnabled());
    }

}