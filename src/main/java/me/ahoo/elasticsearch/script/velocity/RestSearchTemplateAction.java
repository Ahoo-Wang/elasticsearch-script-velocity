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

package me.ahoo.elasticsearch.script.velocity;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.RestStatusToXContentListener;
import org.elasticsearch.rest.action.search.RestSearchAction;

import java.io.IOException;
import java.util.*;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import static org.elasticsearch.rest.RestRequest.Method.GET;
import static org.elasticsearch.rest.RestRequest.Method.POST;

public class RestSearchTemplateAction extends BaseRestHandler {
    private static final Set<String> RESPONSE_PARAMS;

    static {
        final Set<String> responseParams = new HashSet<>(Arrays.asList(RestSearchAction.TYPED_KEYS_PARAM, RestSearchAction.TOTAL_HITS_AS_INT_PARAM));
        RESPONSE_PARAMS = Collections.unmodifiableSet(responseParams);
    }

    @Override
    public List<Route> routes() {
        return unmodifiableList(asList(
                new Route(GET, "/_search/velocity_template"),
                new Route(POST, "/_search/velocity_template"),
                new Route(GET, "/{index}/_search/velocity_template"),
                new Route(POST, "/{index}/_search/velocity_template"),
                // Deprecated typed endpoints.
                new Route(GET, "/{index}/{type}/_search/velocity_template"),
                new Route(POST, "/{index}/{type}/_search/velocity_template")));
    }

    @Override
    public String getName() {
        return "search_velocity_template_action";
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        // Creates the search request with all required params
        SearchRequest searchRequest = new SearchRequest();
        RestSearchAction.parseSearchRequest(searchRequest, request, null, client.getNamedWriteableRegistry(), size -> searchRequest.source().size(size));

        // Creates the search template request
        SearchTemplateRequest searchTemplateRequest;
        try (XContentParser parser = request.contentOrSourceParamParser()) {
            searchTemplateRequest = SearchTemplateRequest.fromXContent(parser);
        }
        searchTemplateRequest.setRequest(searchRequest);
        RestSearchAction.checkRestTotalHits(request, searchRequest);

        return channel -> client.execute(SearchTemplateAction.INSTANCE, searchTemplateRequest, new RestStatusToXContentListener<>(channel));
    }

    @Override
    protected Set<String> responseParams() {
        return RESPONSE_PARAMS;
    }
}
