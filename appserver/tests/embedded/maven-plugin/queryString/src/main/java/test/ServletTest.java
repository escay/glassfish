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

package test;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

public class ServletTest extends HttpServlet {
    private static boolean isRedirected = false;
    private ServletContext context;

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        System.out.println("[Servlet.init]");
        context = config.getServletContext();
        System.out.println("[Servlet.init] " + context.getMajorVersion());

    }

    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        System.out.println("[Servlet.doGet]");
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        System.out.println("[Servlet.doPost]");

        response.setContentType("text/html");
        PrintWriter out = response.getWriter();

        System.out.println("requestUri: " + request.getRequestURI());

        if (!isRedirected){
            String url = request.getParameter("url") + "?TEST=PASS";
            System.out.println("[URL] " + url);
            response.sendRedirect(url);
            isRedirected = true;
            out.println("TEST:FAIL");
            out.flush();
            return;
        }

        out.println("TEST:" + request.getParameter("TEST"));
        out.flush();
    }

}



