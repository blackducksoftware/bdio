/*
 * Copyright 2017 Black Duck Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.blackducksoftware.bdio2.tool;

import static com.blackducksoftware.common.base.ExtraStrings.removeSuffix;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.List;

import com.blackducksoftware.bdio2.BdioMetadata;
import com.blackducksoftware.bdio2.model.License;
import com.blackducksoftware.common.value.ProductList;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Generates a BDIO document describing the standard SPDX licenses.
 *
 * @author jgustie
 */
public class SpdxDocumentTool extends Tool {

    public static void main(String[] args) {
        new SpdxDocumentTool(null).parseArgs(args).run();
    }

    /**
     * Object used to deserialize the SPDX license list.
     */
    private static class LicenseList {
        private static class SpdxLicense {
            public boolean isDeprecatedLicenseId;

            public String detailsUrl;

            public String name;

            public String licenseId;

            public List<String> seeAlso;

            public boolean isOsiApproved;
        }

        public String licenseListVersion;

        public List<SpdxLicense> licenses;
    }

    private final static String SPDX_LICENSE_LIST_URL = "https://raw.githubusercontent.com/spdx/license-list-data/master/json/licenses.json";

    private final OkHttpClient client;

    private final ObjectMapper mapper;

    private boolean useRemote;

    private boolean osiApproved;

    public SpdxDocumentTool(String name) {
        super(name);
        client = new OkHttpClient();
        mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public void setUseRemote(boolean useRemote) {
        this.useRemote = useRemote;
    }

    public void setOsiApproved(boolean osiApproved) {
        this.osiApproved = osiApproved;
    }

    @Override
    protected Tool parseArguments(String[] args) throws Exception {
        for (String arg : options(args)) {
            if (arg.equals("--osi")) {
                setOsiApproved(true);
                args = removeFirst(arg, args);
            } else if (arg.equals("--latest")) {
                setUseRemote(true);
                args = removeFirst(arg, args);
            }
        }

        return super.parseArguments(args);
    }

    @Override
    protected void execute() throws Exception {
        printJson(describeSpdx());
        printOutput("%n");
    }

    public Object describeSpdx() throws IOException {
        LicenseList licenseList = readLicenseList();

        // We cannot include an identifier on this document or it will cause metadata merge conflicts
        // when included in a BDIO document as a stand alone entry
        BdioMetadata metadata = new BdioMetadata()
                .creationDateTime(ZonedDateTime.now())
                .publisher(new ProductList.Builder()
                        .addProduct(getProduct().newBuilder()
                                .addCommentText("licenseListVersion %s", licenseList.licenseListVersion)
                                .build())
                        .build());

        // TODO This needs a Repository as a root object
        return metadata.asNamedGraph(licenseList.licenses.stream()
                .filter(this::includeLicense)
                .map(this::convertToBdio)
                .collect(toList()));
    }

    /**
     * Returns the license list to convert to BDIO.
     */
    private LicenseList readLicenseList() throws IOException {
        Reader reader;
        if (useRemote) {
            // Fetch the latest version of the license list
            Request request = new Request.Builder().url(SPDX_LICENSE_LIST_URL).build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    reader = response.body().charStream();
                } else {
                    throw new IOException("Unable to fetch SPDX license list: " + response);
                }
            }
        } else {
            // Use the local copy of the license list
            URL localLicenseList = Resources.getResource(SpdxDocumentTool.class, "spdx/licenses.json");
            if (localLicenseList != null) {
                reader = Resources.asCharSource(localLicenseList, UTF_8).openStream();
            } else {
                throw new IOException("Unable to locate the 'licenses.json' resource");
            }
        }
        return mapper.readValue(reader, LicenseList.class);

    }

    /**
     * Test to see if the specified SPDX license should be included.
     */
    private boolean includeLicense(LicenseList.SpdxLicense license) {
        if (license.isDeprecatedLicenseId) {
            return false;
        }

        if (osiApproved && !license.isOsiApproved) {
            return false;
        }

        return true;
    }

    /**
     * Converts a single SPDX license to a BDIO license.
     */
    private License convertToBdio(LicenseList.SpdxLicense spdx) {
        License license = new License(removeSuffix(spdx.detailsUrl, ".json"));
        license.namespace("spdx");
        license.identifier(spdx.licenseId);
        license.name(spdx.name);
        if (spdx.seeAlso != null) {
            spdx.seeAlso.forEach(license::homepage);
        }
        return license;
    }

}
