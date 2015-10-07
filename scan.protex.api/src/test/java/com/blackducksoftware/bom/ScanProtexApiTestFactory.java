/*
 * Copyright (C) 2015 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
 */
package com.blackducksoftware.bom;

import org.testng.annotations.Factory;
import org.testng.annotations.Test;

import com.blackducksoftware.bom.io.BillOfMaterialsReaderTest;
import com.blackducksoftware.bom.io.BillOfMaterialsRoundTripTest;
import com.blackducksoftware.bom.io.BillOfMaterialsWriterTest;
import com.blackducksoftware.bom.io.LinkedDataContextTest;
import com.blackducksoftware.bom.model.AbstractModelTest;

public class ScanProtexApiTestFactory {
    @Factory
    @Test
    public Object[] tests() {
        return new Object[] {
                new NodeTest(),
                new TermTest(),
                new TypeTest(),
                new BillOfMaterialsReaderTest(),
                new BillOfMaterialsRoundTripTest(),
                new BillOfMaterialsWriterTest(),
                new LinkedDataContextTest(),
                new AbstractModelTest(),
        };
    }
}
