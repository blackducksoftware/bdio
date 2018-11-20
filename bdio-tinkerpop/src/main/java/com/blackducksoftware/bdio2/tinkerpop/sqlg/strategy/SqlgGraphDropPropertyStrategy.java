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

import org.apache.tinkerpop.gremlin.process.traversal.Step;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.Traverser;
import org.apache.tinkerpop.gremlin.process.traversal.step.filter.NoneStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.PropertiesStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.sideEffect.ProfileSideEffectStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.sideEffect.SideEffectCapStep;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.AbstractTraversalStrategy;
import org.umlg.sqlg.step.SqlgGraphStep;
import org.umlg.sqlg.step.barrier.SqlgDropStepBarrier;
import org.umlg.sqlg.structure.SqlgGraph;

import com.blackducksoftware.bdio2.tinkerpop.sqlg.step.SqlgGraphDropPropertyStep;

/**
 * Strategy to optimize a simple {@code g.V().properties("foo").drop().iterate()} query; it folds a
 * {@code SqlgGraphStep}, {@code PropertiesStep} and {@code SqlgDropStepBarrier} into a single step, but only if that is
 * the entire query and there are no open transaction.
 * <p>
 * <em>WARNING!</em> This was written with exactly zero knowledge of how TinkerPop or Sqlg internals work. There is a
 * good chance that it is not safe for general use!
 *
 * @author jgustie
 */
@SuppressWarnings("ComparableType") // The super breaks the contract of Comparable
public class SqlgGraphDropPropertyStrategy extends AbstractTraversalStrategy<TraversalStrategy.ProviderOptimizationStrategy>
        implements TraversalStrategy.ProviderOptimizationStrategy {
    private static final long serialVersionUID = 1L;

    private static final SqlgGraphDropPropertyStrategy INSTANCE = new SqlgGraphDropPropertyStrategy();

    public static SqlgGraphDropPropertyStrategy instance() {
        return INSTANCE;
    }

    private SqlgGraphDropPropertyStrategy() {
    }

    @Override
    public void apply(Traversal.Admin<?, ?> traversal) {
        if (!(traversal.getGraph().get() instanceof SqlgGraph)) {
            return;
        }

        if (traversal.getGraph().get().tx().isOpen()) {
            return;
        }

        if (traversal.getStartStep() instanceof SqlgGraphStep<?, ?>) {
            SqlgGraphStep<?, ?> startStep = (SqlgGraphStep<?, ?>) traversal.getStartStep();
            Step<?, ?> step = startStep.getNextStep();
            if (step instanceof PropertiesStep<?>) {
                step = step.getNextStep();
                if (step instanceof SqlgDropStepBarrier<?>) {
                    step = step.getNextStep();
                    if (allowEndStep(step)) {
                        traversal.removeStep(0);
                        step = traversal.getStartStep();
                        while (step instanceof PropertiesStep<?> || step instanceof SqlgDropStepBarrier<?>) {
                            traversal.removeStep(0);
                            step = traversal.getStartStep();
                        }
                        traversal.addStep(0, new SqlgGraphDropPropertyStep<>(traversal, startStep));
                    }
                }
            }

        }
    }

    private boolean allowEndStep(Step<?, ?> step) {
        if (step instanceof NoneStep<?>) {
            return true; // e.g. ".iterate()"
        } else if (step instanceof ProfileSideEffectStep<?>) {
            Step<?, ?> nextStep = step.getNextStep();
            return nextStep instanceof SideEffectCapStep<?, ?> && nextStep.getNextStep().getId().equals(Traverser.Admin.HALT);
        }
        return false;
    }

}
