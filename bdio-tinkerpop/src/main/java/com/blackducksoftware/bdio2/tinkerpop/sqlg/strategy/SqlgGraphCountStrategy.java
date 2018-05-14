/*
 * Copyright 2018 Synopsys, Inc.
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
package com.blackducksoftware.bdio2.tinkerpop.sqlg.strategy;

import java.util.List;

import org.apache.tinkerpop.gremlin.process.traversal.Step;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.CountGlobalStep;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.AbstractTraversalStrategy;
import org.umlg.sqlg.step.SqlgGraphStep;
import org.umlg.sqlg.structure.SqlgGraph;

import com.blackducksoftware.bdio2.tinkerpop.sqlg.step.SqlgGraphCountStep;

/**
 * Strategy to optimize a simple {@code g.V().hasLabel("foo").count().next()} query; it folds a {@code SqlgGraphStep}
 * and a {@code CountGlobalStep} into a single step, but only if the pair is at the start of the traversal.
 * <p>
 * <em>WARNING!</em> This was written with exactly zero knowledge of how TinkerPop or Sqlg internals work. There is a
 * good chance that it is not safe for general use!
 *
 * @author jgustie
 */
@SuppressWarnings("ComparableType") // The super breaks the contract of Comparable
public class SqlgGraphCountStrategy
        extends AbstractTraversalStrategy<TraversalStrategy.ProviderOptimizationStrategy>
        implements TraversalStrategy.ProviderOptimizationStrategy {
    private static final long serialVersionUID = 1L;

    private static final SqlgGraphCountStrategy INSTANCE = new SqlgGraphCountStrategy();

    public static SqlgGraphCountStrategy instance() {
        return INSTANCE;
    }

    private SqlgGraphCountStrategy() {
    }

    @Override
    public void apply(Traversal.Admin<?, ?> traversal) {
        if (!(traversal.getGraph().get() instanceof SqlgGraph)) {
            return;
        }

        // Only replace a simple [SqlgGraphStep, CountGlobalStep, ...] traversal
        @SuppressWarnings("rawtypes")
        List<Step> steps = traversal.getSteps();
        if (steps.size() >= 2
                && steps.get(0) instanceof SqlgGraphStep<?, ?>
                && steps.get(1) instanceof CountGlobalStep<?>) {
            SqlgGraphStep<?, ?> start = (SqlgGraphStep<?, ?>) traversal.getStartStep();
            traversal.removeStep(0).removeStep(0).addStep(0, new SqlgGraphCountStep(traversal, start));
        }
    }

}
