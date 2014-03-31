/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.executor.transport.task.elasticsearch;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.crate.Constants;
import io.crate.executor.Task;
import io.crate.planner.node.ddl.ESCreateTemplateNode;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.template.put.PutIndexTemplateRequest;
import org.elasticsearch.action.admin.indices.template.put.PutIndexTemplateResponse;
import org.elasticsearch.action.admin.indices.template.put.TransportPutIndexTemplateAction;

import java.util.Arrays;
import java.util.List;

public class ESCreateTemplateTask implements Task<Object[][]> {

    private static class CreateTemplateListener implements ActionListener<PutIndexTemplateResponse> {

        private final SettableFuture<Object[][]> future;

        private CreateTemplateListener(SettableFuture<Object[][]> future) {
            this.future = future;
        }

        @Override
        public void onResponse(PutIndexTemplateResponse putIndexTemplateResponse) {
            long count = putIndexTemplateResponse.isAcknowledged() ? 1L : 0L;
            future.set(new Object[][]{ new Object[] { count } });
        }

        @Override
        public void onFailure(Throwable e) {
            future.setException(e);
        }
    }

    private final List<ListenableFuture<Object[][]>> results;
    private final TransportPutIndexTemplateAction transport;
    private final PutIndexTemplateRequest request;
    private final CreateTemplateListener listener;

    public ESCreateTemplateTask(ESCreateTemplateNode node, TransportPutIndexTemplateAction transport) {
        this.transport = transport;
        SettableFuture<Object[][]> result = SettableFuture.create();
        this.results = Arrays.<ListenableFuture<Object[][]>>asList(result);
        this.listener = new CreateTemplateListener(result);
        this.request = buildRequest(node);
    }

    @Override
    public void start() {
        transport.execute(request, listener);
    }

    @Override
    public List<ListenableFuture<Object[][]>> result() {
        return results;
    }

    @Override
    public void upstreamResult(List<ListenableFuture<Object[][]>> result) {
        throw new UnsupportedOperationException();
    }

    private PutIndexTemplateRequest buildRequest(ESCreateTemplateNode node) {
        return new PutIndexTemplateRequest(node.templateName())
                .mapping(Constants.DEFAULT_MAPPING_TYPE, node.mapping())
                .create(true)
                .settings(node.indexSettings())
                .template(node.indexMatch());
    }
}