/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.gradle.api.Buildable;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class ArtifactBackedArtifactSet implements ResolvedArtifactSet {
    private final AttributeContainer variant;
    private final ImmutableSet<ResolvedArtifact> artifacts;
    private final Map<ResolvedArtifact, Throwable> artifactFailures = Maps.newConcurrentMap();

    private ArtifactBackedArtifactSet(AttributeContainer variant, Collection<? extends ResolvedArtifact> artifacts) {
        this.variant = variant;
        this.artifacts = ImmutableSet.copyOf(artifacts);
    }

    public static ResolvedArtifactSet forVariant(AttributeContainer variantAttributes, Collection<? extends ResolvedArtifact> artifacts) {
        if (artifacts.isEmpty()) {
            return EMPTY;
        }
        if (artifacts.size() == 1) {
            return new SingletonSet(variantAttributes, artifacts.iterator().next());
        }
        return new ArtifactBackedArtifactSet(variantAttributes, artifacts);
    }

    @Override
    public void addPrepareActions(BuildOperationQueue<RunnableBuildOperation> actions, final ArtifactVisitor visitor) {
        if (visitor.requiresDownloadedArtifactFiles()) {
            for (ResolvedArtifact artifact : artifacts) {
                actions.add(new DownloadArtifactFile(artifact, artifactFailures));
            }
        }
    }

    @Override
    public Set<ResolvedArtifact> getArtifacts() {
        return artifacts;
    }

    @Override
    public void collectBuildDependencies(Collection<? super TaskDependency> dest) {
        for (ResolvedArtifact artifact : artifacts) {
            dest.add(((Buildable) artifact).getBuildDependencies());
        }
    }

    @Override
    public void visit(ArtifactVisitor visitor) {
        for (ResolvedArtifact artifact : artifacts) {
            if (artifactFailures.containsKey(artifact)) {
                visitor.visitFailure(artifactFailures.get(artifact));
            } else {
                visitor.visitArtifact(variant, artifact);
            }
        }
    }

    private static class SingletonSet implements ResolvedArtifactSet {
        private final AttributeContainer variantAttributes;
        private final ResolvedArtifact artifact;
        private final Map<ResolvedArtifact, Throwable> artifactFailures = Maps.newConcurrentMap();

        SingletonSet(AttributeContainer variantAttributes, ResolvedArtifact artifact) {
            this.variantAttributes = variantAttributes;
            this.artifact = artifact;
        }

        @Override
        public void addPrepareActions(BuildOperationQueue<RunnableBuildOperation> actions, final ArtifactVisitor visitor) {
            if (visitor.requiresDownloadedArtifactFiles()) {
                actions.add(new DownloadArtifactFile(artifact, artifactFailures));
            }
        }

        @Override
        public Set<ResolvedArtifact> getArtifacts() {
            return ImmutableSet.of(artifact);
        }

        @Override
        public void visit(ArtifactVisitor visitor) {
            if (artifactFailures.containsKey(artifact)) {
                visitor.visitFailure(artifactFailures.get(artifact));
            } else {
                visitor.visitArtifact(variantAttributes, artifact);
            }
        }

        @Override
        public void collectBuildDependencies(Collection<? super TaskDependency> dest) {
            dest.add(((Buildable) artifact).getBuildDependencies());
        }
    }

    private static class DownloadArtifactFile implements RunnableBuildOperation {
        private final ResolvedArtifact artifact;
        private final Map<ResolvedArtifact, Throwable> artifactFailures;

        DownloadArtifactFile(ResolvedArtifact artifact, Map<ResolvedArtifact, Throwable> artifactFailures) {
            this.artifact = artifact;
            this.artifactFailures = artifactFailures;
        }

        @Override
        public void run() {
            try {
                artifact.getFile();
            } catch (Throwable t) {
                artifactFailures.put(artifact, t);
            }
        }

        @Override
        public String getDescription() {
            return "Resolve artifact " + artifact;
        }
    }

}
