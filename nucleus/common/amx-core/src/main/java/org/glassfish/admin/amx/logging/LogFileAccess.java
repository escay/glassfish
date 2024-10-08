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

package org.glassfish.admin.amx.logging;

import javax.management.MBeanOperationInfo;

import org.glassfish.admin.amx.annotation.ManagedAttribute;
import org.glassfish.admin.amx.annotation.ManagedOperation;
import org.glassfish.admin.amx.annotation.Param;
import org.glassfish.external.arc.Stability;
import org.glassfish.external.arc.Taxonomy;


/**
        Provides access to log files.

        @since AS 9.0
 */
@Taxonomy(stability = Stability.EXPERIMENTAL)
public interface LogFileAccess
{
    /**
        Value meaning the most current version of the log file.
     */
    public static final String  MOST_RECENT_NAME = "MostRecentLogFileName";

        /**
                Key designating the server.log log files.
                <i>Not necessarily the same as the file name of the log file</i>.
         */
    public static final String        SERVER_KEY        = "server";

        /**
                Key designating the access.log log file.
                <i>Not necessarily the same as the file name of the log file</i>.
         */
    public static final String        ACCESS_KEY        = "access";

    /**
            Keys which may be used to specify which log file to access.
            Legal values include:
            <ul>
            <li>{@link #SERVER_KEY}</li>
            <li>{@link #ACCESS_KEY} <b>is not supported</b></li>
            </ul>
            @return a String[] of the legal keys which designate log files
     */
    @ManagedAttribute
    public String[]        getLogFileKeys();


        /**
                The names returned are <i>not</i> full paths but the simple file
                names of the log files.
                The last name in the resulting String[] will always be that of the
                current log file.  In other words if the String[] las length 3,
                then result[2] will be the name of the current log file.
                <p>
                Note that it is possible for log file rotation to occur after making
                this call, thus rendering the list incomplete.
                <p>
                The resulting names may be passed to {@link #getLogFile} to retrieve
                the contents.
                <p>
                The only legal key supported is {@link #SERVER_KEY}.

                @param key        a key specifying the type of log file
                @return String[] of all log filenames
         */
    @ManagedOperation(impact=MBeanOperationInfo.INFO)
        public String[] getLogFileNames( @Param(name="key") final String key );

        /**
                The entire specified log file is read, converted into a String, and returned.
                The resulting String may be quite large.
                <p>
                The only legal key supported is {@link #SERVER_KEY}.

                @param key a legal key designating a log file
                @param fileName        a log file name as returned by {@link #getLogFileNames} or
                {@link #MOST_RECENT_NAME} if current log file is desired.
                @return the contents of the specified log file in a String
                @see #getLogFileKeys
         */
    @ManagedOperation(impact=MBeanOperationInfo.INFO)
        public String getLogFile(
        @Param(name="key") final String key,
        @Param(name="fileName") final String fileName );

    /**
                Rotate all log files as soon as possible.  May return
                prior to the rotation occuring.
     */
    @ManagedOperation(impact=MBeanOperationInfo.ACTION)
    public void rotateAllLogFiles();


    /**
            Rotate the log file of the specified type.  Legal values are those
            returned by {@link #getLogFileKeys}.
            Legal values include:
            <ul>
            <li>{@link #SERVER_KEY}</li>
            <li>{@link #ACCESS_KEY} <b>is not supported</b></li>
            </ul>
            @param key
     */
    @ManagedOperation(impact=MBeanOperationInfo.ACTION)
    public void rotateLogFile( @Param(name="key") String key );
}














