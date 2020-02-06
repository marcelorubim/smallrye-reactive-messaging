package io.smallrye.reactive.messaging.extension;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.enterprise.inject.spi.Bean;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

import io.smallrye.reactive.messaging.DefaultMediatorConfiguration;
import io.smallrye.reactive.messaging.MediatorConfiguration;
import io.smallrye.reactive.messaging.annotations.Incomings;

class CollectedMediatorMetadata {

    private final List<MediatorConfiguration> mediators = new ArrayList<>();
    private final boolean strict;

    CollectedMediatorMetadata(boolean strict) {
        this.strict = strict;
    }

    void add(Method method, Bean<?> bean) {
        mediators.add(createMediatorConfiguration(method, bean));
    }

    private MediatorConfiguration createMediatorConfiguration(Method met, Bean<?> bean) {
        DefaultMediatorConfiguration configuration = new DefaultMediatorConfiguration(met, bean, strict);

        Incomings incomings = met.getAnnotation(Incomings.class);
        Incoming incoming = met.getAnnotation(Incoming.class);
        Outgoing outgoing = met.getAnnotation(Outgoing.class);
        if (incomings != null) {
            configuration.compute(incomings, outgoing);
        } else if (incoming != null) {
            configuration.compute(Collections.singletonList(incoming), outgoing);
        } else {
            configuration.compute(Collections.emptyList(), outgoing);
        }
        return configuration;
    }

    void addAll(Collection<? extends MediatorConfiguration> mediators) {
        this.mediators.addAll(mediators);
    }

    List<MediatorConfiguration> mediators() {
        return mediators;
    }
}
