/*
 * Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.v3.server;

import com.sun.enterprise.config.serverbeans.Application;
import com.sun.enterprise.config.serverbeans.ApplicationRef;
import com.sun.enterprise.config.serverbeans.Applications;
import com.sun.enterprise.config.serverbeans.Cluster;
import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.config.serverbeans.Server;
import com.sun.enterprise.config.serverbeans.ServerTags;
import com.sun.enterprise.module.bootstrap.StartupContext;
import com.sun.enterprise.v3.common.HTMLActionReporter;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.beans.PropertyChangeEvent;
import java.util.Calendar;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.api.ActionReport;
import org.glassfish.api.admin.ServerEnvironment;
import org.glassfish.api.deployment.UndeployCommandParameters;
import org.glassfish.hk2.api.PostConstruct;
import org.glassfish.hk2.runlevel.RunLevel;
import org.glassfish.internal.api.PostStartupRunLevel;
import org.glassfish.internal.data.ApplicationInfo;
import org.glassfish.internal.data.ApplicationRegistry;
import org.glassfish.internal.deployment.Deployment;
import org.glassfish.kernel.KernelLoggerInfo;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.config.TransactionListener;
import org.jvnet.hk2.config.Transactions;
import org.jvnet.hk2.config.UnprocessedChangeEvents;

@Service
@RunLevel(PostStartupRunLevel.VAL)
public class ApplicationConfigListener implements TransactionListener, PostConstruct {

    final private Logger logger = KernelLoggerInfo.getLogger();

    @Inject
    Transactions transactions;

    @Inject
    Domain domain;

    @Inject
    Applications applications;

    @Inject
    ApplicationRegistry appRegistry;

    @Inject
    Deployment deployment;

    @Inject
    StartupContext startupContext;

    @Inject @Named( ServerEnvironment.DEFAULT_INSTANCE_NAME)
    Server server;

    private final static String UPGRADE_PARAM = "-upgrade";

    @Override
    public void transactionCommited( final List<PropertyChangeEvent> changes) {
        boolean isUpdatingAttribute = true;
        for (PropertyChangeEvent event : changes) {
            Object oldValue = event.getOldValue();
            Object newValue = event.getNewValue();
            if (event.getSource() instanceof Applications) {
                if (event.getPropertyName().equals(ServerTags.APPLICATION)) {
                    if (oldValue == null || newValue == null) {
                        // we are adding/removing application element here
                        // and updating existing attribute
                        isUpdatingAttribute = false;
                        break;
                    }
                }
            } else if (event.getSource() instanceof Server ||
                    event.getSource() instanceof Cluster) {
                if (event.getPropertyName().equals(
                        ServerTags.APPLICATION_REF)) {
                    if (oldValue == null || newValue == null) {
                        // we are adding/removing application-ref element here
                        // and updating existing attribute
                        isUpdatingAttribute = false;
                        break;
                    }
                }
            }
        }

        if (!isUpdatingAttribute) {
            // if we are not updating existing attribute, we should
            // skip the config listener
            return;
        }

        for (PropertyChangeEvent event : changes) {
            if (event.getSource() instanceof Application ||
                event.getSource() instanceof ApplicationRef) {
                Object oldValue = event.getOldValue();
                Object newValue = event.getNewValue();
                if (oldValue != null && newValue != null &&
                    oldValue instanceof String &&
                    newValue instanceof String &&
                    !((String)oldValue).equals(newValue)) {
                    // if it's an attribute change of the application
                    // element or application-ref element
                    Object parent = event.getSource();
                    String appName = null;
                    if (parent instanceof Application) {
                        appName = ((Application)parent).getName();
                    } else if (parent instanceof ApplicationRef) {
                        appName = ((ApplicationRef)parent).getRef();
                    }
                    // if it's not a user application, let's not do
                    // anything
                    if (applications.getApplication(appName) == null) {
                        return;
                    }
                    if (event.getPropertyName().equals(ServerTags.ENABLED)) {
                        // enable or disable application accordingly
                        handleAppEnableChange(event.getSource(),
                            appName, Boolean.valueOf((String)newValue));
                    } else if (event.getPropertyName().equals(ServerTags.CONTEXT_ROOT) || event.getPropertyName().equals(ServerTags.VIRTUAL_SERVERS) || event.getPropertyName().equals(ServerTags.AVAILABILITY_ENABLED)) {
                        // for other changes, reload the application
                        handleOtherAppConfigChanges(event.getSource(),
                            appName);
                    }
                }
            }
        }
    }

    @Override
    public void unprocessedTransactedEvents(
        List<UnprocessedChangeEvents> changes) {
    }

    @Override
    public void postConstruct() {
        Properties arguments = startupContext.getArguments();
        if (arguments != null) {
            boolean isUpgrade = Boolean.valueOf(
                arguments.getProperty(UPGRADE_PARAM));
            if (isUpgrade) {
                // we don't want to register this listener for the upgrade
                // start up
                return;
            }
        }
        transactions.addTransactionsListener(this);
    }

    private void handleAppEnableChange(Object parent,
        String appName, boolean enabled) {
        Application application = applications.getApplication(appName);
        if (application.isLifecycleModule()) {
            return;
        }
        if (enabled) {
            if (isCurrentInstanceMatchingTarget(parent)) {
                enableApplication(appName);
            }
        } else {
            if (isCurrentInstanceMatchingTarget(parent)) {
                disableApplication(appName);
            }
        }
    }

    private void handleOtherAppConfigChanges(Object parent, String appName) {
        Application application = applications.getApplication(appName);
        if (application.isLifecycleModule()) {
            return;
        }
        // reload the application for other application related
        // config changes if the application is in enabled state
        if (isCurrentInstanceMatchingTarget(parent) &&
            deployment.isAppEnabled(application)) {
            disableApplication(appName);
            enableApplication(appName);
        }
    }


    private boolean isCurrentInstanceMatchingTarget(Object parent) {
        // DAS receive all the events, so we need to figure out
        // whether we should take action on DAS depending on the event
        if (parent instanceof ApplicationRef) {
            Object grandparent = ((ApplicationRef)parent).getParent();
            if (grandparent instanceof Server) {
                Server gpServer = (Server)grandparent;
                if ( ! server.getName().equals(gpServer.getName())) {
                    return false;
                }
            } else if (grandparent instanceof Cluster) {
                if (server.isDas()) {
                    return false;
                }
            }
        }
        return true;
    }

    private void enableApplication(String appName) {
        Application app = applications.getApplication(appName);
        ApplicationRef appRef = domain.getApplicationRefInServer(server.getName(), appName);
        // if the application does not exist or application is not referenced
        // by the current server instance, do not load
        // if the application is not in enable state, do not load
        if (app == null || appRef == null || !deployment.isAppEnabled(app)) {
            return;
        }

        ApplicationInfo appInfo = appRegistry.get(appName);
        if (appInfo == null || appInfo.isLoaded()) {
            return;
        }

        long operationStartTime =
            Calendar.getInstance().getTimeInMillis();

        try {
            ActionReport report = new HTMLActionReporter();

            deployment.enable(server.getName(), app, appRef, report, logger);

            if (report.getActionExitCode().equals(ActionReport.ExitCode.SUCCESS)) {
                logger.log(Level.INFO, KernelLoggerInfo.loadingApplicationTime,
                        new Object[] { appName, (Calendar.getInstance().getTimeInMillis() - operationStartTime)});
            } else if (report.getActionExitCode().equals(ActionReport.ExitCode.WARNING)){
                logger.log(Level.WARNING, KernelLoggerInfo.loadingApplicationWarning,
                        new Object[] { appName, report.getMessage()});
            } else if (report.getActionExitCode().equals(ActionReport.ExitCode.FAILURE)) {
                throw new Exception(report.getMessage());
            }
        } catch(Exception e) {
            logger.log(Level.SEVERE, KernelLoggerInfo.loadingApplicationErrorEnable, e);
            throw new RuntimeException(e);
        }
    }

    private void disableApplication(String appName) {
        Application app = applications.getApplication(appName);
        ApplicationRef appRef = domain.getApplicationRefInServer(server.getName(), appName);
        // if the application does not exist or application is not referenced
        // by the current server instance, do not unload
        if (app == null || appRef == null) {
            return;
        }

        ApplicationInfo appInfo = appRegistry.get(appName);
        if (appInfo == null || !appInfo.isLoaded()) {
            return;
        }

        try {
            ActionReport report = new HTMLActionReporter();
            UndeployCommandParameters commandParams =
                new UndeployCommandParameters();
            commandParams.name = appName;
            commandParams.target = server.getName();
            commandParams.origin = UndeployCommandParameters.Origin.unload;
            commandParams.command = UndeployCommandParameters.Command.disable;
            deployment.disable(commandParams, app, appInfo, report, logger);

            if (report.getActionExitCode().equals(ActionReport.ExitCode.FAILURE)) {
                throw new Exception(report.getMessage());
            }
        } catch(Exception e) {
            logger.log(Level.SEVERE, KernelLoggerInfo.loadingApplicationErrorDisable, e);
            throw new RuntimeException(e);
        }
    }
}
