/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
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

package org.elasticsearch.index.search.stats;

import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Streamable;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentBuilderString;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 */
public class SearchStats implements Streamable, ToXContent {

    public static class Stats implements Streamable, ToXContent {

        private long queryCount;
        private long queryTimeInMillis;

        private long fetchCount;
        private long fetchTimeInMillis;

        Stats() {

        }

        public Stats(long queryCount, long queryTimeInMillis, long fetchCount, long fetchTimeInMillis) {
            this.queryCount = queryCount;
            this.queryTimeInMillis = queryTimeInMillis;
            this.fetchCount = fetchCount;
            this.fetchTimeInMillis = fetchTimeInMillis;
        }

        public void add(Stats stats) {
            queryCount += stats.queryCount;
            queryTimeInMillis += stats.queryTimeInMillis;

            fetchCount += stats.fetchCount;
            fetchTimeInMillis += stats.fetchTimeInMillis;
        }

        public long queryCount() {
            return queryCount;
        }

        public long getQueryCount() {
            return queryCount;
        }

        public TimeValue queryTime() {
            return new TimeValue(queryTimeInMillis);
        }

        public long queryTimeInMillis() {
            return queryTimeInMillis;
        }

        public long getQueryTimeInMillis() {
            return queryTimeInMillis;
        }

        public long fetchCount() {
            return fetchCount;
        }

        public long getFetchCount() {
            return fetchCount;
        }

        public TimeValue fetchTime() {
            return new TimeValue(fetchTimeInMillis);
        }

        public long fetchTimeInMillis() {
            return fetchTimeInMillis;
        }

        public long getFetchTimeInMillis() {
            return fetchTimeInMillis;
        }

        public static Stats readStats(StreamInput in) throws IOException {
            Stats stats = new Stats();
            stats.readFrom(in);
            return stats;
        }

        @Override public void readFrom(StreamInput in) throws IOException {
            queryCount = in.readVLong();
            queryTimeInMillis = in.readVLong();

            fetchCount = in.readVLong();
            fetchTimeInMillis = in.readVLong();
        }

        @Override public void writeTo(StreamOutput out) throws IOException {
            out.writeVLong(queryCount);
            out.writeVLong(queryTimeInMillis);

            out.writeVLong(fetchCount);
            out.writeVLong(fetchTimeInMillis);
        }

        @Override public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.field(Fields.QUERY_TOTAL, queryCount);
            builder.field(Fields.QUERY_TIME, queryTime().toString());
            builder.field(Fields.QUERY_TIME_IN_MILLIS, queryTimeInMillis);

            builder.field(Fields.FETCH_TOTAL, fetchCount);
            builder.field(Fields.FETCH_TIME, fetchTime().toString());
            builder.field(Fields.FETCH_TIME_IN_MILLIS, fetchTimeInMillis);

            return builder;
        }
    }

    private Stats totalStats;

    @Nullable Map<String, Stats> groupStats;

    public SearchStats() {
        totalStats = new Stats();
    }

    public SearchStats(Stats totalStats, @Nullable Map<String, Stats> groupStats) {
        this.totalStats = totalStats;
        this.groupStats = groupStats;
    }

    public void add(SearchStats searchStats) {
        add(searchStats, true);
    }

    public void add(SearchStats searchStats, boolean includeTypes) {
        if (searchStats == null) {
            return;
        }
        totalStats.add(searchStats.totalStats);
        if (includeTypes && searchStats.groupStats != null && !searchStats.groupStats.isEmpty()) {
            if (groupStats == null) {
                groupStats = new HashMap<String, Stats>(searchStats.groupStats.size());
            }
            for (Map.Entry<String, Stats> entry : searchStats.groupStats.entrySet()) {
                Stats stats = groupStats.get(entry.getKey());
                if (stats == null) {
                    groupStats.put(entry.getKey(), entry.getValue());
                } else {
                    stats.add(entry.getValue());
                }
            }
        }
    }

    public Stats total() {
        return this.totalStats;
    }

    @Nullable public Map<String, Stats> groupStats() {
        return this.groupStats;
    }

    @Override public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject(Fields.SEARCH);
        totalStats.toXContent(builder, params);
        if (groupStats != null && !groupStats.isEmpty()) {
            builder.startObject(Fields.GROUPS);
            for (Map.Entry<String, Stats> entry : groupStats.entrySet()) {
                builder.startObject(entry.getKey(), XContentBuilder.FieldCaseConversion.NONE);
                entry.getValue().toXContent(builder, params);
                builder.endObject();
            }
            builder.endObject();
        }
        builder.endObject();
        return builder;
    }

    static final class Fields {
        static final XContentBuilderString SEARCH = new XContentBuilderString("search");
        static final XContentBuilderString GROUPS = new XContentBuilderString("groups");
        static final XContentBuilderString QUERY_TOTAL = new XContentBuilderString("query_total");
        static final XContentBuilderString QUERY_TIME = new XContentBuilderString("query_time");
        static final XContentBuilderString QUERY_TIME_IN_MILLIS = new XContentBuilderString("query_time_in_millis");
        static final XContentBuilderString FETCH_TOTAL = new XContentBuilderString("fetch_total");
        static final XContentBuilderString FETCH_TIME = new XContentBuilderString("fetch_time");
        static final XContentBuilderString FETCH_TIME_IN_MILLIS = new XContentBuilderString("fetch_time_in_millis");
    }

    public static SearchStats readSearchStats(StreamInput in) throws IOException {
        SearchStats searchStats = new SearchStats();
        searchStats.readFrom(in);
        return searchStats;
    }

    @Override public void readFrom(StreamInput in) throws IOException {
        totalStats = Stats.readStats(in);
        if (in.readBoolean()) {
            int size = in.readVInt();
            groupStats = new HashMap<String, Stats>(size);
            for (int i = 0; i < size; i++) {
                groupStats.put(in.readUTF(), Stats.readStats(in));
            }
        }
    }

    @Override public void writeTo(StreamOutput out) throws IOException {
        totalStats.writeTo(out);
        if (groupStats == null || groupStats.isEmpty()) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            out.writeVInt(groupStats.size());
            for (Map.Entry<String, Stats> entry : groupStats.entrySet()) {
                out.writeUTF(entry.getKey());
                entry.getValue().writeTo(out);
            }
        }
    }
}
