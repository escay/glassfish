/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 * Copyright (c) 2012, 2018 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.nucleus.admin.progress;

import java.util.List;

import org.glassfish.nucleus.test.tool.asadmin.Asadmin;
import org.glassfish.nucleus.test.tool.asadmin.AsadminResult;
import org.glassfish.nucleus.test.tool.asadmin.GlassFishTestEnvironment;
import org.junit.jupiter.api.Test;

import static org.glassfish.nucleus.test.tool.AsadminResultMatcher.asadminOK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author martinmares
 */
public class ProgressStatusFailITest {

    private static final Asadmin ASADMIN = GlassFishTestEnvironment.getAsadmin();

    @Test
    public void failDuringExecution() {
        AsadminResult result = ASADMIN.exec("progress-fail-in-half");
        assertThat(result, not(asadminOK()));
        List<ProgressMessage> prgs = ProgressMessage.grepProgressMessages(result.getStdOut());
        assertFalse(prgs.isEmpty());
        assertEquals(50, prgs.get(prgs.size() - 1).getValue());
    }

    @Test
    public void timeout() {
        AsadminResult result = ASADMIN.exec(6_000, false, "progress-custom", "3x1", "1x8", "2x1");
        assertThat(result, not(asadminOK()));
        List<ProgressMessage> prgs = ProgressMessage.grepProgressMessages(result.getStdOut());
        assertFalse(prgs.isEmpty());
        assertEquals(50, prgs.get(prgs.size() - 1).getValue());
    }

}
