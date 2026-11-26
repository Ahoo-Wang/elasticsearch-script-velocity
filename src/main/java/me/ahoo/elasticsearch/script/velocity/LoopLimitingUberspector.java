/*
 *  Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *       http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package me.ahoo.elasticsearch.script.velocity;

import org.apache.velocity.util.introspection.Info;
import org.apache.velocity.util.introspection.SecureUberspector;

import java.util.Iterator;

/**
 * {@link SecureUberspector} that also caps the total number of {@code #foreach} iterations of one render.
 * <p>
 * Velocity's own {@code directive.foreach.max_loops} silently truncates each loop, which would quietly drop clauses
 * from a search request; this fails the render instead. The budget covers every loop of the render, nested ones
 * included (see {@link RenderScope#countLoopIteration()}).
 */
public class LoopLimitingUberspector extends SecureUberspector {

    @Override
    @SuppressWarnings("rawtypes")
    public Iterator getIterator(Object obj, Info i) {
        Iterator iterator = super.getIterator(obj, i);
        RenderScope scope = RenderScope.current();
        if (iterator == null || scope == null) {
            return iterator;
        }
        return new BudgetedIterator(iterator, scope);
    }

    @SuppressWarnings("rawtypes")
    private static final class BudgetedIterator implements Iterator {
        private final Iterator delegate;
        private final RenderScope scope;

        private BudgetedIterator(Iterator delegate, RenderScope scope) {
            this.delegate = delegate;
            this.scope = scope;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public Object next() {
            scope.countLoopIteration();
            return delegate.next();
        }

        @Override
        public void remove() {
            delegate.remove();
        }
    }
}
