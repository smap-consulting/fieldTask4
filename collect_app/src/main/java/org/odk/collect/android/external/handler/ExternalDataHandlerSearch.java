/*
 * Copyright (C) 2014 University of Washington
 *
 * Originally developed by Dobility, Inc. (as part of SurveyCTO)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.external.handler;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.xpath.expr.XPathFuncExpr;
import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.database.SqlFrag;
import org.odk.collect.android.database.SqlFragParam;
import org.odk.collect.android.exception.ExternalDataException;
import org.odk.collect.android.external.ExternalDataManager;
import org.odk.collect.android.external.ExternalDataUtil;
import org.odk.collect.android.external.ExternalSQLiteOpenHelper;
import org.odk.collect.android.external.ExternalSelectChoice;
import org.odk.collect.android.utilities.TranslationHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import timber.log.Timber;
import static org.odk.collect.android.external.handler.ExternalDataSearchType.IN;
import static org.odk.collect.android.external.handler.ExternalDataSearchType.NOT_IN;
import static org.odk.collect.android.external.handler.ExternalDataSearchType.EVAL;

/**
 * Author: Smap Consulting
 */
public class ExternalDataHandlerSearch extends ExternalDataHandlerBase {

    public static final String HANDLER_NAME = "search";

    private final String displayColumns;
    private final String valueColumn;
    private final String imageColumn;

    public ExternalDataHandlerSearch(ExternalDataManager externalDataManager, String displayColumns,
            String valueColumn, String imageColumn) {
        super(externalDataManager);
        this.displayColumns = displayColumns;
        this.valueColumn = valueColumn;
        this.imageColumn = imageColumn;
    }

    public String getDisplayColumns() {
        return displayColumns;
    }

    public String getValueColumn() {
        return valueColumn;
    }

    public String getImageColumn() {
        return imageColumn;
    }

    @Override
    public String getName() {
        return HANDLER_NAME;
    }

    @Override
    public List<Class[]> getPrototypes() {
        return new ArrayList<Class[]>();
    }

    @Override
    public boolean rawArgs() {
        return true;
    }

    @Override
    public boolean realTime() {
        return false;
    }

    @Override
    public Object eval(Object[] args, EvaluationContext ec) {
        if (args == null || (args.length != 1 && args.length != 4 && args.length != 6)) {
            // we should never get here since it is already handled in ExternalDataUtil
            // .getSearchXPathExpression(String appearance)
            throw new ExternalDataException(
                    TranslationHandler.getString(Collect.getInstance(), org.odk.collect.strings.R.string.ext_search_wrong_arguments_error));
        }

        String searchType = null;

        String queriedColumnsParam = null;
        List<String> queriedColumns = null;
        List<String> queriedValues = null;      // smap
        String queriedValue = null;
        if (args.length >= 4) {
            searchType = XPathFuncExpr.toString(args[1]);
            queriedColumnsParam = XPathFuncExpr.toString(args[2]);
            queriedValue = XPathFuncExpr.toString(args[3]);
        }

        ExternalDataSearchType externalDataSearchType = ExternalDataSearchType.getByKeyword(
                searchType, ExternalDataSearchType.CONTAINS);

        boolean searchRows = false;
        boolean useFilter = false;

        if (queriedColumnsParam != null && queriedColumnsParam.trim().length() > 0) {
            searchRows = true;
            queriedColumns = ExternalDataUtil.createListOfColumns(queriedColumnsParam);
        }

        // smap
        if(queriedValue != null) {
            queriedValues = ExternalDataUtil.createListOfValues(queriedValue, externalDataSearchType.getKeyword().trim());
        }

        String filterColumn = null;
        String filterValue = null;
        if (args.length == 6) {
            filterColumn = XPathFuncExpr.toString(args[4]);
            filterValue = XPathFuncExpr.toString(args[5]);
            useFilter = true;
        }

        // SCTO-545
        String dataSetName = normalize(XPathFuncExpr.toString(args[0]));

        Cursor c = null;
        try {
            ExternalSQLiteOpenHelper sqLiteOpenHelper = getExternalDataManager().getDatabase(
                    dataSetName, true);

            SQLiteDatabase db = sqLiteOpenHelper.getReadableDatabase();

            // Smap get the column list requested by user, ie no elimination of duplicates
            LinkedHashMap<String, String> selectColumnMap = new LinkedHashMap<String, String> ();
            List<String> columnsToFetch = new ArrayList<String> ();
            ExternalDataUtil.createMapWithDisplayingColumns(
                    selectColumnMap,
                    columnsToFetch,
                    getValueColumn(),
                    getDisplayColumns());

            String safeImageColumn = null;
            if (getImageColumn() != null && getImageColumn().trim().length() > 0) {
                safeImageColumn = ExternalDataUtil.toSafeColumnName(getImageColumn());
                columnsToFetch.add(safeImageColumn);
            }

            String[] sqlColumns = columnsToFetch.toArray(new String[columnsToFetch.size()]);

            String selection;
            String[] selectionArgs;

            String filter= null;
            SqlFrag filterFrag = null;
            if(externalDataSearchType.equals(EVAL)) {
                filter = ExternalDataUtil.evaluateExpressionNodes(XPathFuncExpr.toString(args[2]), ec);
                if(filter != null && filter.length() > 0) {
                    filterFrag = new SqlFrag();
                    try {
                        filterFrag.addSqlFragment(filter, false, null, 0);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            if(filterFrag != null) {
                selection = filterFrag.sql.toString();
                int paramSize = filterFrag.params.size();
                if(useFilter) {
                    selection += " AND " + ExternalDataUtil.toSafeColumnName(filterColumn) + "=? ";
                    paramSize++;
                }

                selectionArgs = new String[paramSize];
                int idx = 0;
                for(SqlFragParam param : filterFrag.params) {
                    selectionArgs[idx++] = SqlFrag.getParamValue(param);
                }
                if(useFilter) {
                    selectionArgs[selectionArgs.length - 1] = filterValue;
                }
            } else if (searchRows && useFilter) {
                // smap modify arguments for createLikeExpression
                selection = "( " + createLikeExpression(queriedColumns, queriedValues, externalDataSearchType) + " ) AND "
                        + ExternalDataUtil.toSafeColumnName(filterColumn) + "=? ";
                String[] likeArgs = externalDataSearchType.constructLikeArguments(queriedValues);    // smap queriedValues - remove column size
                selectionArgs = new String[likeArgs.length + 1];
                System.arraycopy(likeArgs, 0, selectionArgs, 0, likeArgs.length);
                selectionArgs[selectionArgs.length - 1] = filterValue;
            } else if (searchRows) {
                selection = createLikeExpression(queriedColumns, queriedValues, externalDataSearchType);    // smap
                selectionArgs = externalDataSearchType.constructLikeArguments(queriedValues);        // smap queriedValues - remove column size
            } else if (useFilter) {
                selection = ExternalDataUtil.toSafeColumnName(filterColumn) + "=? ";
                selectionArgs = new String[]{filterValue};
            } else {
                selection = null;
                selectionArgs = null;
            }

            try {
                c = db.query(ExternalDataUtil.EXTERNAL_DATA_TABLE_NAME, sqlColumns, selection,
                        selectionArgs, null, null, ExternalDataUtil.SORT_COLUMN_NAME);
            } catch (Exception e) {
                Timber.e(TranslationHandler.getString(Collect.getInstance(), org.odk.collect.strings.R.string.ext_import_csv_missing_error, dataSetName, dataSetName));
                try {  // smap
                    c = db.query(ExternalDataUtil.EXTERNAL_DATA_TABLE_NAME, sqlColumns, selection,
                            selectionArgs, null, null, null);
                } catch (Exception ex) {
                    // Not sure why we are trying again with no sort
                }
            }

            return createDynamicSelectChoices(c, selectColumnMap, safeImageColumn);
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    protected ArrayList<SelectChoice> createDynamicSelectChoices(Cursor c,
            LinkedHashMap<String, String> selectColumnMap, String safeImageColumn) {
        List<String> columnsToExcludeFromLabels = new ArrayList<>();
        if (safeImageColumn != null) {
            columnsToExcludeFromLabels.add(safeImageColumn);
        }

        ArrayList<SelectChoice> selectChoices = new ArrayList<>();
        if (c!= null && c.getCount() > 0) { // smap add null check
            c.moveToPosition(-1);
            int index = 0;
            Set<String> uniqueValues = new HashSet<>();
            while (c.moveToNext()) {

                // the value is always the first column
                String value = c.getString(0);
                if (value != null && value.trim().length() >0 && !uniqueValues.contains(value)) {   // smap make sure value is not null
                    String label = buildLabel(c, selectColumnMap, columnsToExcludeFromLabels);

                    ExternalSelectChoice selectChoice;
                    if (label.trim().length() == 0) {
                        selectChoice = new ExternalSelectChoice(value, value, false);
                    } else {
                        selectChoice = new ExternalSelectChoice(label, value, false);
                    }
                    selectChoice.setIndex(index);

                    if (safeImageColumn != null && safeImageColumn.trim().length() > 0) {
                        String image = c.getString(c.getColumnIndex(safeImageColumn));
                        if (image != null && image.trim().length() > 0) {
                            selectChoice.setImage(ExternalDataUtil.JR_IMAGES_PREFIX + image);
                        }
                    }

                    selectChoices.add(selectChoice);

                    index++;

                    uniqueValues.add(value);
                }
            }
        }
        return selectChoices;
    }

    protected String createLikeExpression(List<String> queriedColumns,
                                          List<String> queriedValues, ExternalDataSearchType type) {  // smap
        StringBuilder sb = new StringBuilder();
        if(type.equals(IN) && queriedColumns.size() > 0) {    // smap
            sb.append(queriedColumns.get(0)).append(" in (");
            int idx = 0;
            for (String queriedValue : queriedValues) {
                if (idx++ > 0) {
                    sb.append(", ");
                }
                sb.append("?");
            }
            sb.append(")");
        } else if(type.equals(NOT_IN) && queriedColumns.size() > 0) {    // smap
            sb.append(queriedColumns.get(0)).append(" not in (");
            int idx = 0;
            for (String queriedValue : queriedValues) {
                if (idx++ > 0) {
                    sb.append(", ");
                }
                sb.append("?");
            }
            sb.append(")");
        } else {
            for (String queriedColumn : queriedColumns) {
                if (sb.length() > 0) {
                    sb.append(" OR ");
                }
                sb.append(queriedColumn).append(" LIKE ? ");
            }
        }
        return sb.toString();
    }

    /**
     * So here are examples of labels with one, two, and three columns:
     * <p/>
     * col1value
     * col1value (col2name: col2value)
     * col1value (col2name: col2value) (col3name: col3value)
     */
    protected String buildLabel(Cursor c, LinkedHashMap<String, String> selectColumnMap,
            List<String> columnsToExcludeFromLabels) {
        StringBuilder sb = new StringBuilder();
        // we start at 1 since 0 is the "value" column
        for (int columnIndex = 1; columnIndex < c.getColumnCount(); columnIndex++) {
            String columnName = c.getColumnName(columnIndex);
            if (columnsToExcludeFromLabels.contains(columnName)) {
                continue;
            }

            String value = c.getString(columnIndex);

            if (columnIndex == 1) {
                sb.append(value);
                continue;
            }
            if (c.getColumnCount() - columnsToExcludeFromLabels.size() == 2) {
                break;
            }
            if (columnIndex > 1) {
                sb.append(' ');
            }
            sb.append('(');
            sb.append(selectColumnMap.get(columnName));
            sb.append(": ");
            sb.append(value);
            sb.append(')');
        }
        return sb.toString();
    }
}
