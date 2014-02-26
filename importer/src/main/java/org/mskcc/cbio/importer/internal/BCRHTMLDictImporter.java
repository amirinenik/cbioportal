/*
 * Copyright (c) 2012 Memorial Sloan-Kettering Cancer Center.
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation; either version 2.1 of the License, or
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY, WITHOUT EVEN THE IMPLIED WARRANTY OF
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE.  The software and
 * documentation provided hereunder is on an "as is" basis, and
 * Memorial Sloan-Kettering Cancer Center
 * has no obligations to provide maintenance, support,
 * updates, enhancements or modifications.  In no event shall
 * Memorial Sloan-Kettering Cancer Center
 * be liable to any party for direct, indirect, special,
 * incidental or consequential damages, including lost profits, arising
 * out of the use of this software and its documentation, even if
 * Memorial Sloan-Kettering Cancer Center
 * has been advised of the possibility of such damage.  See
 * the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
 */

package org.mskcc.cbio.importer.internal;

import org.mskcc.cbio.importer.*;
import org.mskcc.cbio.importer.model.*;

import org.apache.commons.logging.*;
import org.apache.commons.lang.StringUtils;

import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;

import java.io.*;
import java.util.*;
import java.util.regex.*;

public class BCRHTMLDictImporter extends ImporterBaseImpl implements Importer
{
    private static final String DISPLAY_AND_COLUMN_NAME_HEADER = "CDE Name";
    private static final String DEFINITION_HEADER = "Definition";
    private static final String DISEASE_TYPE_HEADER = "Disease Type";
    private static final String TUMOR_TYPES_DELIMITER = " \\| ";
    private static final Pattern COLUMN_NAME_REGEX = Pattern.compile("^.*xmlTag: (.*)$");
    private static final Pattern DISPLAY_NAME_REGEX = Pattern.compile("^(.*) xmlTag:.*$");

    private static final Log LOG = LogFactory.getLog(BCRHTMLDictImporter.class);

    private Config config;
    private FileUtils fileUtils;
    private DatabaseUtils databaseUtils;
    
    private int displayAndColumnNameIndex;
    private int definitionIndex;
    private int tumorTypeIndex;

    public BCRHTMLDictImporter(Config config, FileUtils fileUtils, DatabaseUtils databaseUtils)
    {
        this.config = config;
        this.fileUtils = fileUtils;
        this.databaseUtils = databaseUtils;
    }

    @Override
    public void importData(String portal, Boolean initPortalDatabase,
                           Boolean initTumorTypes, Boolean importReferenceData) throws Exception
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void importCancerStudy(String cancerStudyDirectoryName, boolean skip, boolean force) throws Exception
    {
		throw new UnsupportedOperationException();
    }

    @Override
    public void importReferenceData(ReferenceMetadata referenceMetadata) throws Exception
    {
        String bcrDictionaryFilename = referenceMetadata.getImporterArgs().get(0);

        if (!bcrDictionaryFilename.isEmpty()) {
            logMessage(LOG, "importReferenceData, processing Biospecimen Core Resource dictionary: " + bcrDictionaryFilename);
            config.importBCRClinicalAttributes(getBCRDictEntries(bcrDictionaryFilename));
        }
        else {
            logMessage(LOG, "importReferenceData, missing Biospecimen Core Resource dictionary filename.");
        }
    }

    private List<BCRDictEntry> getBCRDictEntries(String bcrDictionaryFilename) throws Exception
    {
        Document doc = Jsoup.parse(new File(bcrDictionaryFilename), null);
        return getBCRDictEntriesFromTable(doc.select("table").first());
    }

    private List<BCRDictEntry> getBCRDictEntriesFromTable(Element table)
    {
        List<BCRDictEntry> bcrs = new ArrayList<BCRDictEntry>();

        setColumnIndices(table.select("thead").first());
        for (Element row : table.select("tbody").first().select("tr")) {
            bcrs.add(getBCRDictEntryFromRow(row));
        }

        if (bcrs.isEmpty()) {
            fatal();
        }

        return bcrs;
    }

    private void setColumnIndices(Element htmlTableHeader)
    {
        displayAndColumnNameIndex = getColumnIndex(htmlTableHeader, DISPLAY_AND_COLUMN_NAME_HEADER);
        definitionIndex = getColumnIndex(htmlTableHeader, DEFINITION_HEADER);
        tumorTypeIndex = getColumnIndex(htmlTableHeader, DISEASE_TYPE_HEADER);
        if (displayAndColumnNameIndex < 0 || definitionIndex < 0 || tumorTypeIndex < 0) {
            fatal();
        }
    }

    private int getColumnIndex(Element htmlTableHeader, String attributeName)
    {
        Elements columns = htmlTableHeader.select("th");
        for (int colIndex = 0; colIndex < columns.size(); colIndex++) {
            Element column = columns.get(colIndex);
            if (column.text().contains(attributeName)) {
                return colIndex;
            }
        }
        return -1;
    }

    private BCRDictEntry getBCRDictEntryFromRow(Element row)
    {
            BCRDictEntry bcr = new BCRDictEntry();
            bcr.id = getNameFromRow(row, COLUMN_NAME_REGEX);
            bcr.displayName = getNameFromRow(row, DISPLAY_NAME_REGEX);
            bcr.description = row.select("td").get(definitionIndex).text();
            bcr.cancerStudy = "";
            bcr.tumorType = getTumorTypesFromRow(row);

            return bcr;
    }

    private String getNameFromRow(Element row, Pattern p)
    {
        String value = row.select("td").get(displayAndColumnNameIndex).text();
        Matcher nameMatcher = p.matcher(value);
        return (nameMatcher.find()) ? nameMatcher.group(1) : value;
    }

    private String getTumorTypesFromRow(Element row)
    {
        return StringUtils.join(row.select("td").get(tumorTypeIndex).text().split(TUMOR_TYPES_DELIMITER), ",").toLowerCase();
    }

    private void fatal()
    {
        throw new IllegalArgumentException("BCR HTML Dictionary format has changed, aborting...");
    }
}
