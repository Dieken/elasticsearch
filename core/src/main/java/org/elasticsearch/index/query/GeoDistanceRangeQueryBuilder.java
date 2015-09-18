/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.index.query;

import org.apache.lucene.search.Query;
import org.elasticsearch.Version;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.geo.GeoDistance;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.geo.GeoUtils;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.fielddata.IndexGeoPointFieldData;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.mapper.geo.GeoPointFieldMapper;
import org.elasticsearch.index.search.geo.GeoDistanceRangeQuery;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

public class GeoDistanceRangeQueryBuilder extends AbstractQueryBuilder<GeoDistanceRangeQueryBuilder> {

    public static final String NAME = "geo_distance_range";
    public static final boolean DEFAULT_INCLUDE_LOWER = true;
    public static final boolean DEFAULT_INCLUDE_UPPER = true;
    public static final GeoDistance DEFAULT_GEO_DISTANCE = GeoDistance.DEFAULT;
    public static final DistanceUnit DEFAULT_UNIT = DistanceUnit.DEFAULT;
    public static final String DEFAULT_OPTIMIZE_BBOX = "memory";
    public static final boolean DEFAULT_COERCE = false;
    public static final boolean DEFAULT_IGNORE_MALFORMED = false;

    private final String fieldName;

    private Object from;
    private Object to;
    private boolean includeLower = DEFAULT_INCLUDE_LOWER;
    private boolean includeUpper = DEFAULT_INCLUDE_UPPER;

    private final GeoPoint point;

    private GeoDistance geoDistance = DEFAULT_GEO_DISTANCE;

    private DistanceUnit unit = DEFAULT_UNIT;

    private String optimizeBbox = DEFAULT_OPTIMIZE_BBOX;

    private boolean coerce = DEFAULT_COERCE;

    private boolean ignoreMalformed = DEFAULT_IGNORE_MALFORMED;

    static final GeoDistanceRangeQueryBuilder PROTOTYPE = new GeoDistanceRangeQueryBuilder("_na_", new GeoPoint());

    public GeoDistanceRangeQueryBuilder(String fieldName, GeoPoint point) {
        if (Strings.isEmpty(fieldName)) {
            throw new IllegalArgumentException("fieldName must not be null");
        }
        if (point == null) {
            throw new IllegalArgumentException("point must not be null");
        }
        this.fieldName = fieldName;
        this.point = point;
    }

    public GeoDistanceRangeQueryBuilder(String fieldName, double lat, double lon) {
        this(fieldName, new GeoPoint(lat, lon));
    }

    public GeoDistanceRangeQueryBuilder(String fieldName, String geohash) {
        this(fieldName, geohash == null ? null : new GeoPoint().resetFromGeoHash(geohash));
    }

    public String fieldName() {
        return fieldName;
    }

    public GeoPoint point() {
        return point;
    }

    public GeoDistanceRangeQueryBuilder from(String from) {
        if (from == null) {
            throw new IllegalArgumentException("[from] must not be null");
        }
        this.from = from;
        return this;
    }

    public GeoDistanceRangeQueryBuilder from(Number from) {
        if (from == null) {
            throw new IllegalArgumentException("[from] must not be null");
        }
        this.from = from;
        return this;
    }

    public Object from() {
        return from;
    }

    public GeoDistanceRangeQueryBuilder to(String to) {
        if (to == null) {
            throw new IllegalArgumentException("[to] must not be null");
        }
        this.to = to;
        return this;
    }

    public GeoDistanceRangeQueryBuilder to(Number to) {
        if (to == null) {
            throw new IllegalArgumentException("[to] must not be null");
        }
        this.to = to;
        return this;
    }

    public Object to() {
        return to;
    }

    public GeoDistanceRangeQueryBuilder includeLower(boolean includeLower) {
        this.includeLower = includeLower;
        return this;
    }

    public boolean includeLower() {
        return includeLower;
    }

    public GeoDistanceRangeQueryBuilder includeUpper(boolean includeUpper) {
        this.includeUpper = includeUpper;
        return this;
    }

    public boolean includeUpper() {
        return includeUpper;
    }

    public GeoDistanceRangeQueryBuilder geoDistance(GeoDistance geoDistance) {
        if (geoDistance == null) {
            throw new IllegalArgumentException("geoDistance calculation mode must not be null");
        }
        this.geoDistance = geoDistance;
        return this;
    }

    public GeoDistance geoDistance() {
        return geoDistance;
    }

    public GeoDistanceRangeQueryBuilder unit(DistanceUnit unit) {
        if (unit == null) {
            throw new IllegalArgumentException("distance unit must not be null");
        }
        this.unit = unit;
        return this;
    }

    public DistanceUnit unit() {
        return unit;
    }

    public GeoDistanceRangeQueryBuilder optimizeBbox(String optimizeBbox) {
        if (optimizeBbox == null) {
            throw new IllegalArgumentException("optimizeBox must not be null");
        }
        switch (optimizeBbox) {
            case "none":
            case "memory":
            case "indexed":
                break;
            default:
                throw new IllegalArgumentException("optimizeBbox must be one of [none, memory, indexed]");
        }
        this.optimizeBbox = optimizeBbox;
        return this;
    }

    public String optimizeBbox() {
        return optimizeBbox;
    }

    public GeoDistanceRangeQueryBuilder coerce(boolean coerce) {
        if (coerce) {
            this.ignoreMalformed = true;
        }
        this.coerce = coerce;
        return this;
    }

    public boolean coerce() {
        return this.coerce;
    }

    public GeoDistanceRangeQueryBuilder ignoreMalformed(boolean ignoreMalformed) {
        if (coerce == false) {
            this.ignoreMalformed = ignoreMalformed;
        }
        return this;
    }

    public boolean ignoreMalformed() {
        return ignoreMalformed;
    }

    @Override
    protected Query doToQuery(QueryShardContext context) throws IOException {

        final boolean indexCreatedBeforeV2_0 = context.indexVersionCreated().before(Version.V_2_0_0);
        // validation was not available prior to 2.x, so to support bwc
        // percolation queries we only ignore_malformed on 2.x created indexes
        if (!indexCreatedBeforeV2_0 && !ignoreMalformed) {
            if (!GeoUtils.isValidLatitude(point.lat())) {
                throw new QueryShardException(context, "illegal latitude value [{}] for [{}]", point.lat(), NAME);
            }
            if (!GeoUtils.isValidLongitude(point.lon())) {
                throw new QueryShardException(context, "illegal longitude value [{}] for [{}]", point.lon(), NAME);
            }
        }

        if (coerce) {
            GeoUtils.normalizePoint(point, coerce, coerce);
        }

        Double fromValue = null;
        Double toValue = null;
        if (from != null) {
            if (from instanceof Number) {
                fromValue = unit.toMeters(((Number) from).doubleValue());
            } else {
                fromValue = DistanceUnit.parse((String) from, unit, DistanceUnit.DEFAULT);
            }
            fromValue = geoDistance.normalize(fromValue, DistanceUnit.DEFAULT);
        }
        if (to != null) {
            if (to instanceof Number) {
                toValue = unit.toMeters(((Number) to).doubleValue());
            } else {
                toValue = DistanceUnit.parse((String) to, unit, DistanceUnit.DEFAULT);
            }
            toValue = geoDistance.normalize(toValue, DistanceUnit.DEFAULT);
        }

        MappedFieldType fieldType = context.fieldMapper(fieldName);
        if (fieldType == null) {
            throw new QueryShardException(context, "failed to find geo_point field [" + fieldName + "]");
        }
        if (!(fieldType instanceof GeoPointFieldMapper.GeoPointFieldType)) {
            throw new QueryShardException(context, "field [" + fieldName + "] is not a geo_point field");
        }
        GeoPointFieldMapper.GeoPointFieldType geoFieldType = ((GeoPointFieldMapper.GeoPointFieldType) fieldType);

        IndexGeoPointFieldData indexFieldData = context.getForField(fieldType);
        return new GeoDistanceRangeQuery(point, fromValue, toValue, includeLower, includeUpper, geoDistance, geoFieldType,
                indexFieldData, optimizeBbox);
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME);
        builder.startArray(fieldName).value(point.lon()).value(point.lat()).endArray();
        builder.field(GeoDistanceRangeQueryParser.FROM_FIELD.getPreferredName(), from);
        builder.field(GeoDistanceRangeQueryParser.TO_FIELD.getPreferredName(), to);
        builder.field(GeoDistanceRangeQueryParser.INCLUDE_LOWER_FIELD.getPreferredName(), includeLower);
        builder.field(GeoDistanceRangeQueryParser.INCLUDE_UPPER_FIELD.getPreferredName(), includeUpper);
        builder.field(GeoDistanceRangeQueryParser.UNIT_FIELD.getPreferredName(), unit);
        builder.field(GeoDistanceRangeQueryParser.DISTANCE_TYPE_FIELD.getPreferredName(), geoDistance.name().toLowerCase(Locale.ROOT));
        builder.field(GeoDistanceRangeQueryParser.OPTIMIZE_BBOX_FIELD.getPreferredName(), optimizeBbox);
        builder.field(GeoDistanceRangeQueryParser.COERCE_FIELD.getPreferredName(), coerce);
        builder.field(GeoDistanceRangeQueryParser.IGNORE_MALFORMED_FIELD.getPreferredName(), ignoreMalformed);
        printBoostAndQueryName(builder);
        builder.endObject();
    }

    @Override
    protected GeoDistanceRangeQueryBuilder doReadFrom(StreamInput in) throws IOException {
        GeoDistanceRangeQueryBuilder queryBuilder = new GeoDistanceRangeQueryBuilder(in.readString(), GeoPoint.readGeoPointFrom(in));
        queryBuilder.from = in.readGenericValue();
        queryBuilder.to = in.readGenericValue();
        queryBuilder.includeLower = in.readBoolean();
        queryBuilder.includeUpper = in.readBoolean();
        queryBuilder.unit = DistanceUnit.valueOf(in.readString());
        queryBuilder.geoDistance = GeoDistance.readGeoDistanceFrom(in);
        queryBuilder.optimizeBbox = in.readString();
        queryBuilder.coerce = in.readBoolean();
        queryBuilder.ignoreMalformed = in.readBoolean();
        return queryBuilder;
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeString(fieldName);
        point.writeTo(out);
        out.writeGenericValue(from);
        out.writeGenericValue(to);
        out.writeBoolean(includeLower);
        out.writeBoolean(includeUpper);
        out.writeString(unit.name());
        geoDistance.writeTo(out);;
        out.writeString(optimizeBbox);
        out.writeBoolean(coerce);
        out.writeBoolean(ignoreMalformed);
    }

    @Override
    protected boolean doEquals(GeoDistanceRangeQueryBuilder other) {
        if (!Objects.equals(fieldName, other.fieldName)) {
            return false;
        }
        if (!Objects.equals(point, other.point)) {
            return false;
        }
        if (!Objects.equals(from, other.from)) {
            return false;
        }
        if (!Objects.equals(to, other.to)) {
            return false;
        }
        if (!Objects.equals(includeUpper, other.includeUpper)) {
            return false;
        }
        if (!Objects.equals(includeLower, other.includeLower)) {
            return false;
        }
        if (!Objects.equals(geoDistance, other.geoDistance)) {
            return false;
        }
        if (!Objects.equals(optimizeBbox, other.optimizeBbox)) {
            return false;
        }
        if (!Objects.equals(coerce, other.coerce)) {
            return false;
        }
        if (!Objects.equals(ignoreMalformed, other.ignoreMalformed)) {
            return false;
        }
        return true;
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(fieldName, point, from, to, includeUpper, includeLower, geoDistance, optimizeBbox, coerce,
                ignoreMalformed);
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }
}
