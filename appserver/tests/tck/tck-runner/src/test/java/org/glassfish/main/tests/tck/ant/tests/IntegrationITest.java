/*
 * Copyright (c) 2022 Eclipse Foundation and/or its affiliates. All rights reserved.
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

package org.glassfish.main.tests.tck.ant.tests;

import jakarta.inject.Inject;

import java.nio.file.Path;

import org.glassfish.main.tests.tck.ant.TckRunner;
import org.glassfish.main.tests.tck.ant.junit.TckTestExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * @author David Matejcek
 */
@ExtendWith(TckTestExtension.class)
public class IntegrationITest {

    @Inject
    private TckRunner tck;

    /**
     * 18 tests, usual time: 7 minutes
     */
    @Test
    public void integration() throws Exception {
        tck.start(Path.of("integration"));
    }

    /**
     * 12 tests, usual time: 8 minutes
     */
    @Test
    public void samples() throws Exception {
        tck.startMailServer();
        tck.start(Path.of("samples"));
    }

    /**
     * 4 tests, usual time: 12 minutes
     */
    @Test
    public void signaturetest() throws Exception {
        tck.start(Path.of("signaturetest", "javaee"));
    }
}