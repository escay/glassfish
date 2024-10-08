/*
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package com.sun.ejb.base.container.util;

import com.sun.enterprise.config.serverbeans.Config;
import com.sun.logging.LogDomains;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.glassfish.api.admin.ServerEnvironment;
import org.glassfish.ejb.config.EjbContainer;
import org.glassfish.ejb.deployment.descriptor.EjbDescriptor;
import org.glassfish.ejb.deployment.descriptor.EjbSessionDescriptor;
import org.glassfish.ejb.deployment.descriptor.runtime.BeanCacheDescriptor;
import org.glassfish.ejb.deployment.descriptor.runtime.IASEjbExtraDescriptors;
import org.glassfish.hk2.api.PostConstruct;
import org.jvnet.hk2.annotations.Service;

/**
 * A util class to read the bean cache related entries from
 * domain.xml and sun-ejb-jar.xml
 *
 * @author Mahesh Kannan
 */
@Service
public class CacheProperties implements PostConstruct {

    protected static final Logger _logger =
            LogDomains.getLogger(CacheProperties.class, LogDomains.EJB_LOGGER);

    private int maxCacheSize;
    private int numberOfVictimsToSelect;
    private int cacheIdleTimeoutInSeconds;
    private int removalTimeoutInSeconds;

    private String victimSelectionPolicy;

    @Inject @Named(ServerEnvironment.DEFAULT_INSTANCE_NAME)
    private Config serverConfig;

    EjbContainer ejbContainer;

    public CacheProperties() {
    }

    public void postConstruct() {
        ejbContainer = serverConfig.getExtensionByType(EjbContainer.class);
    }

    public void init(EjbDescriptor desc) {

        BeanCacheDescriptor beanCacheDes = null;

        IASEjbExtraDescriptors iased = desc.getIASEjbExtraDescriptors();
        if (iased != null) {
            beanCacheDes = iased.getBeanCache();
        }

        loadProperties(ejbContainer, desc, beanCacheDes);
        //container.setMonitorOn(ejbContainer.isMonitoringEnabled());

    }

    public int getMaxCacheSize() {
        return this.maxCacheSize;
    }

    public int getNumberOfVictimsToSelect() {
        return this.numberOfVictimsToSelect;
    }

    public int getCacheIdleTimeoutInSeconds() {
        return this.cacheIdleTimeoutInSeconds;
    }

    public int getRemovalTimeoutInSeconds() {
        return this.removalTimeoutInSeconds;
    }

    public String getVictimSelectionPolicy() {
        return this.victimSelectionPolicy;
    }

    public String getPassivationStorePath() {
        return ejbContainer.getSessionStore();
    }

    public String toString() {
        StringBuffer sbuf = new StringBuffer();
        sbuf.append("maxSize: ").append(maxCacheSize)
                .append("; victims: ").append(numberOfVictimsToSelect)
                .append("; idleTimeout: ").append(cacheIdleTimeoutInSeconds)
                .append("; removalTimeout: ").append(removalTimeoutInSeconds)
                .append("; policy: ").append(victimSelectionPolicy);

        return sbuf.toString();
    }

    private void loadProperties(EjbContainer ejbContainer,
                                EjbDescriptor ejbDesc,
                                BeanCacheDescriptor beanCacheDes) {
        numberOfVictimsToSelect =
                Integer.parseInt(ejbContainer.getCacheResizeQuantity());

        maxCacheSize =
                Integer.parseInt(ejbContainer.getMaxCacheSize());

        cacheIdleTimeoutInSeconds = Integer.parseInt(
                ejbContainer.getCacheIdleTimeoutInSeconds());

        removalTimeoutInSeconds =
                Integer.parseInt(ejbContainer.getRemovalTimeoutInSeconds());

        victimSelectionPolicy = ejbContainer.getVictimSelectionPolicy();

        // If portable @StatefulTimeout is specified, it takes precedence over
        // any default value in domain.xml.  However, if a removal timeout is
        // specified in sun-ejb-jar.xml, that has highest precedence.
        if( ejbDesc instanceof EjbSessionDescriptor ) {

            EjbSessionDescriptor sessionDesc = (EjbSessionDescriptor) ejbDesc;
            if( sessionDesc.hasStatefulTimeout() ) {

                long value = sessionDesc.getStatefulTimeoutValue();
                TimeUnit unit = sessionDesc.getStatefulTimeoutUnit();

                value = TimeUnit.SECONDS.convert(value, unit);
        if (value < 0) {
                    this.removalTimeoutInSeconds = -1;
                    this.cacheIdleTimeoutInSeconds = -1;
        } else if (value == 0) {
                    this.removalTimeoutInSeconds = 1;
                    this.cacheIdleTimeoutInSeconds = 2;
        } else {
                    this.removalTimeoutInSeconds = (int) value;
                    this.cacheIdleTimeoutInSeconds = (int) (value + 1);
        }

            }

            /* lifespan of an idle sfsb is time-in-cache + time-on-disk, with cacheIdleTimeoutInSeconds setting
               for the 1st interval and removalTimeoutInSeconds for the 2nd. So if you add them, the sfsb will stay
               in the cache to the max possible interval, and because it'll be never written to disk,
               there will be nothing to remove from there.

               set cacheIdleTimeoutInSeconds and maxCacheSize will cause cache never overflow, and sfsb just be
               removed from cache when cacheIdleTimeoutInSeconds arrives */
            if (sessionDesc.isStateful() && !sessionDesc.isPassivationCapable()) {
                cacheIdleTimeoutInSeconds = cacheIdleTimeoutInSeconds + removalTimeoutInSeconds;
                maxCacheSize = -1;
            }
        }

        if (beanCacheDes != null) {
            int temp = 0;
            if ((temp = beanCacheDes.getResizeQuantity()) != -1) {
                this.numberOfVictimsToSelect = temp;
            }
            if ((temp = beanCacheDes.getMaxCacheSize()) != -1) {
                this.maxCacheSize = temp;
            }
            if ((temp = beanCacheDes.getCacheIdleTimeoutInSeconds()) != -1) {
                this.cacheIdleTimeoutInSeconds = temp;
            }
            if ((temp = beanCacheDes.getRemovalTimeoutInSeconds()) != -1) {
                this.removalTimeoutInSeconds = temp;
            }
            if ((beanCacheDes.getVictimSelectionPolicy()) != null) {
                this.victimSelectionPolicy =
                        beanCacheDes.getVictimSelectionPolicy();
            }
        }

    }

}

