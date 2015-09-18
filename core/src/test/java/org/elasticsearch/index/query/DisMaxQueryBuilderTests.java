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

import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.Query;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.hamcrest.CoreMatchers.*;

public class DisMaxQueryBuilderTests extends AbstractQueryTestCase<DisMaxQueryBuilder> {

    /**
     * @return a {@link DisMaxQueryBuilder} with random inner queries
     */
    @Override
    protected DisMaxQueryBuilder doCreateTestQueryBuilder() {
        DisMaxQueryBuilder dismax = new DisMaxQueryBuilder();
        int clauses = randomIntBetween(1, 5);
        for (int i = 0; i < clauses; i++) {
            dismax.add(RandomQueryBuilder.createQuery(random()));
        }
        if (randomBoolean()) {
            dismax.tieBreaker(2.0f / randomIntBetween(1, 20));
        }
        return dismax;
    }

    @Override
    protected void doAssertLuceneQuery(DisMaxQueryBuilder queryBuilder, Query query, QueryShardContext context) throws IOException {
        Collection<Query> queries = AbstractQueryBuilder.toQueries(queryBuilder.innerQueries(), context);
        if (queries.isEmpty()) {
            assertThat(query, nullValue());
        } else {
            assertThat(query, instanceOf(DisjunctionMaxQuery.class));
            DisjunctionMaxQuery disjunctionMaxQuery = (DisjunctionMaxQuery) query;
            assertThat(disjunctionMaxQuery.getTieBreakerMultiplier(), equalTo(queryBuilder.tieBreaker()));
            assertThat(disjunctionMaxQuery.getDisjuncts().size(), equalTo(queries.size()));
            Iterator<Query> queryIterator = queries.iterator();
            for (int i = 0; i < disjunctionMaxQuery.getDisjuncts().size(); i++) {
                assertThat(disjunctionMaxQuery.getDisjuncts().get(i), equalTo(queryIterator.next()));
            }
        }
    }

    @Override
    protected Map<String, DisMaxQueryBuilder> getAlternateVersions() {
        Map<String, DisMaxQueryBuilder> alternateVersions = new HashMap<>();
        QueryBuilder innerQuery = createTestQueryBuilder().innerQueries().get(0);
        DisMaxQueryBuilder expectedQuery = new DisMaxQueryBuilder();
        expectedQuery.add(innerQuery);
        String contentString = "{\n" +
                "    \"dis_max\" : {\n" +
                "        \"queries\" : " + innerQuery.toString() +
                "    }\n" +
                "}";
        alternateVersions.put(contentString, expectedQuery);
        return alternateVersions;
    }

    /**
     * test `null`return value for missing inner queries
     * @throws IOException
     */
    @Test
    public void testNoInnerQueries() throws IOException {
        DisMaxQueryBuilder disMaxBuilder = new DisMaxQueryBuilder();
        assertNull(disMaxBuilder.toQuery(createShardContext()));
    }

    /**
     * Test inner query parsing to null. Current DSL allows inner filter element to parse to <tt>null</tt>.
     * Those should be ignored upstream. To test this, we use inner {@link ConstantScoreQueryBuilder}
     * with empty inner filter.
     */
    @Test
    public void testInnerQueryReturnsNull() throws IOException {
        String queryString = "{ \"" + ConstantScoreQueryBuilder.NAME + "\" : { \"filter\" : { } } }";
        QueryBuilder<?> innerQueryBuilder = parseQuery(queryString);
        DisMaxQueryBuilder disMaxBuilder = new DisMaxQueryBuilder().add(innerQueryBuilder);
        assertNull(disMaxBuilder.toQuery(createShardContext()));
    }

    @Test
    public void testIllegalArguments() {
        DisMaxQueryBuilder disMaxQuery = new DisMaxQueryBuilder();
        try {
            disMaxQuery.add(null);
            fail("cannot be null");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }
}
