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

import java.util.Objects;

import org.apache.tinkerpop.gremlin.process.traversal.Step;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.Traverser;
import org.apache.tinkerpop.gremlin.process.traversal.step.filter.NoneStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.CountGlobalStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.PropertiesStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.sideEffect.AddPropertyStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.sideEffect.ProfileSideEffectStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.sideEffect.SideEffectCapStep;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.AbstractTraversalStrategy;
import org.umlg.sqlg.step.SqlgGraphStep;
import org.umlg.sqlg.step.barrier.SqlgDropStepBarrier;

import com.blackducksoftware.bdio2.tinkerpop.sqlg.step.SqlgGraphAddPropertyStep;
import com.blackducksoftware.bdio2.tinkerpop.sqlg.step.SqlgGraphCountStep;
import com.blackducksoftware.bdio2.tinkerpop.sqlg.step.SqlgGraphDropPropertyStep;

/**
 * Strategy to optimize a simple TinkerPop queries into efficient SQL queries.
 * <p>
 * The following "simple" queries are optimized:
 * <ul>
 * <li>Adding a property, e.g. {@code g.V().property("foo", "1").iterate()}</li>
 * <li>Dropping a property, e.g. {@code g.V().properties("foo").drop().iterate()}</li>
 * <li>Counting by label, e.g. {@code g.V().hasLabel("foo").count().next()}</li>
 * </ul>
 * <p>
 * <em>NOTE:</em> if the optimization for adding properties is applied, the step becomes a "side-effect/filter" step,
 * only the side-effects are evaluated and the resulting vertices are filtered out.
 * <p>
 * <em>NOTE:</em> the dropping of properties optimization is bypassed if the transaction is currently open due to issues
 * with keeping the vertex cache synchronized.
 * <p>
 * <em>WARNING!</em> This was written with exactly zero knowledge of how TinkerPop or Sqlg internals work. There is a
 * good chance that it is not safe for general use!
 *
 * @author jgustie
 */
@SuppressWarnings("ComparableType") // The super breaks the contract of Comparable
public class SqlgSimpleQueryStrategy extends AbstractTraversalStrategy<TraversalStrategy.ProviderOptimizationStrategy>
        implements TraversalStrategy.ProviderOptimizationStrategy {
    private static final long serialVersionUID = 1L;

    private static final SqlgSimpleQueryStrategy INSTANCE = new SqlgSimpleQueryStrategy();

    public static SqlgSimpleQueryStrategy instance() {
        return INSTANCE;
    }

    private SqlgSimpleQueryStrategy() {
    }

    @Override
    public void apply(Traversal.Admin<?, ?> traversal) {
        if (traversal.getStartStep() instanceof SqlgGraphStep<?, ?>) {
            SqlgGraphStep<?, ?> startStep = (SqlgGraphStep<?, ?>) traversal.getStartStep();
            Step<?, ?> replacementStep = null;
            Step<?, ?> step = startStep.getNextStep();
            if (step instanceof AddPropertyStep<?>) {
                // Normally AddProperty is a side effect, so you get the actual updated vertex after the fact; however
                // we want to optimize the case where there is nothing after add property; literally by flat mapping
                // into an empty traversal after we perform the UPDATE in the database
                while (step instanceof AddPropertyStep<?>) {
                    step = step.getNextStep();
                }
                if (isTerminalStep(step)) {
                    replacementStep = new SqlgGraphAddPropertyStep<>(traversal, startStep);
                }
            } else if (step instanceof PropertiesStep<?>) {
                // In this case we are specifically looking for a properties being dropped (in SQL, we update to NULL);
                // unlike the add property case, "drop" is naturally a filtering step so we won't be returning anything
                // out of the replacement step anyway
                step = step.getNextStep();
                if (step instanceof SqlgDropStepBarrier<?>) {
                    step = step.getNextStep();
                }
                if (isTerminalStep(step)) {
                    // We cannot reconcile the vertex cache in this case, skip the optimization
                    if (traversal.getGraph().filter(graph -> graph.tx().isOpen()).isPresent()) {
                        return;
                    }
                    replacementStep = new SqlgGraphDropPropertyStep<>(traversal, startStep);
                }
            } else if (step instanceof CountGlobalStep<?>) {
                // Currently we only handle counting a single table at a time
                if (startStep.parseForStrategy().size() == 1) {
                    replacementStep = new SqlgGraphCountStep(traversal, startStep);
                    step = step.getNextStep();
                }
            }

            // If we have a replacement step, remove everything up to and including the
            if (replacementStep != null) {
                while (!Objects.equals(traversal.getStartStep(), step)) {
                    traversal.removeStep(0);
                }
                traversal.addStep(0, replacementStep);
            }
        }
    }

    /**
     * Advances the supplied step to last acceptable step allowed, returning {@code null} if the supplied step is not a
     * valid terminal step for a simple query optimization.
     */
    private boolean isTerminalStep(Step<?, ?> step) {
        if (step instanceof NoneStep<?>) {
            // This is just the "iterate()" at the end of the query
            return true;
        } else if (step instanceof ProfileSideEffectStep<?>) {
            // Allow the query to be profiled for testing purposes
            Step<?, ?> nextStep = step.getNextStep();
            return nextStep instanceof SideEffectCapStep<?, ?> && nextStep.getNextStep().getId().equals(Traverser.Admin.HALT);
        } else {
            return false;
        }
    }

}
