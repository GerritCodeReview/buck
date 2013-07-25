/*
 * Copyright 2012-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.android;

import com.facebook.buck.java.AnnotationProcessingParams;
import com.facebook.buck.java.JavaLibraryRule;
import com.facebook.buck.java.JavaTestRule;
import com.facebook.buck.java.JavacOptions;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.AbstractBuildRuleBuilderParams;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildRuleType;
import com.facebook.buck.rules.SourcePath;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.List;
import java.util.Set;

public class RobolectricTestRule extends JavaTestRule {

  protected RobolectricTestRule(BuildRuleParams buildRuleParams,
      Set<String> srcs,
      Set<SourcePath> resources,
      Set<String> labels,
      Optional<String> proguardConfig,
      JavacOptions javacOptions,
      List<String> vmArgs,
      ImmutableSet<JavaLibraryRule> sourceUnderTest,
      Function<String, String> relativeToAbsolutePathFunction) {
    super(buildRuleParams,
        srcs,
        resources,
        labels,
        proguardConfig,
        javacOptions,
        vmArgs,
        sourceUnderTest,
        relativeToAbsolutePathFunction);
  }

  @Override
  public BuildRuleType getType() {
    return BuildRuleType.ROBOLECTRIC_TEST;
  }

  @Override
  public boolean isAndroidRule() {
    return true;
  }

  @Override
  protected List<String> getInputsToCompareToOutput() {
    return super.getInputsToCompareToOutput();
  }

  public static Builder newRobolectricTestRuleBuilder(AbstractBuildRuleBuilderParams params) {
    return new Builder(params);
  }

  public static class Builder extends JavaTestRule.Builder {


    private Builder(AbstractBuildRuleBuilderParams params) {
      super(params);
    }

    @Override
    public RobolectricTestRule build(BuildRuleResolver ruleResolver) {
      ImmutableSet<JavaLibraryRule> sourceUnderTest = generateSourceUnderTest(sourcesUnderTest,
          ruleResolver);

      ImmutableList.Builder<String> allVmArgs = ImmutableList.builder();
      allVmArgs.addAll(vmArgs);

      AnnotationProcessingParams processingParams = getAnnotationProcessingBuilder().build(ruleResolver);
      javacOptions.setAnnotationProcessingData(processingParams);

      return new RobolectricTestRule(createBuildRuleParams(ruleResolver),
          srcs,
          resources,
          labels,
          proguardConfig,
          javacOptions.build(),
          allVmArgs.build(),
          sourceUnderTest,
          relativeToAbsolutePathFunction);
    }

    @Override
    public Builder setBuildTarget(BuildTarget buildTarget) {
      super.setBuildTarget(buildTarget);
      return this;
    }

  }
}
