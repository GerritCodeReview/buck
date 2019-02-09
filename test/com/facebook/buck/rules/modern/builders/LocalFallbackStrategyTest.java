/*
 * Copyright 2019-present Facebook, Inc.
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

package com.facebook.buck.rules.modern.builders;

import com.facebook.buck.artifact_cache.CacheResult;
import com.facebook.buck.core.build.engine.BuildResult;
import com.facebook.buck.core.build.engine.BuildRuleStatus;
import com.facebook.buck.core.build.engine.BuildRuleSuccessType;
import com.facebook.buck.core.build.engine.BuildStrategyContext;
import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.build.strategy.BuildRuleStrategy.StrategyBuildResult;
import com.facebook.buck.core.rules.impl.FakeBuildRule;
import com.facebook.buck.rules.modern.builders.LocalFallbackStrategy.FallbackStrategyBuildResult;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class LocalFallbackStrategyTest {
  private static final String RULE_NAME = "//topspin/rule";

  private StrategyBuildResult strategyBuildResult;
  private BuildStrategyContext buildStrategyContext;
  private ListeningExecutorService directExecutor;

  @Before
  public void setUp() {
    strategyBuildResult = EasyMock.createMock(StrategyBuildResult.class);
    buildStrategyContext = EasyMock.createMock(BuildStrategyContext.class);
    directExecutor = MoreExecutors.newDirectExecutorService();
  }

  @After
  public void tearDown() {}

  @Test
  public void testRemoteSuccess() throws ExecutionException, InterruptedException {
    BuildResult buildResult = successBuildResult("//finished:successfully");
    EasyMock.expect(strategyBuildResult.getBuildResult())
        .andReturn(Futures.immediateFuture(Optional.of(buildResult)))
        .times(2);

    EasyMock.replay(strategyBuildResult, buildStrategyContext);
    FallbackStrategyBuildResult fallbackStrategyBuildResult =
        new FallbackStrategyBuildResult(RULE_NAME, strategyBuildResult, buildStrategyContext);
    Assert.assertEquals(buildResult, fallbackStrategyBuildResult.getBuildResult().get().get());

    EasyMock.verify(strategyBuildResult, buildStrategyContext);
  }

  @Test
  public void testRemoteException() throws ExecutionException, InterruptedException {
    EasyMock.expect(strategyBuildResult.getBuildResult())
        .andReturn(Futures.immediateFailedFuture(new Exception("This did not go well...")))
        .times(2);
    BuildResult localResult = successBuildResult("//local/did:though");
    EasyMock.expect(buildStrategyContext.runWithDefaultBehavior())
        .andReturn(Futures.immediateFuture(Optional.of(localResult)))
        .once();
    EasyMock.expect(buildStrategyContext.getExecutorService()).andReturn(directExecutor).times(2);

    EasyMock.replay(strategyBuildResult, buildStrategyContext);
    FallbackStrategyBuildResult fallbackStrategyBuildResult =
        new FallbackStrategyBuildResult(RULE_NAME, strategyBuildResult, buildStrategyContext);
    Assert.assertEquals(localResult, fallbackStrategyBuildResult.getBuildResult().get().get());

    EasyMock.verify(strategyBuildResult, buildStrategyContext);
  }

  @Test
  public void testRemoteActionError() throws ExecutionException, InterruptedException {
    EasyMock.expect(strategyBuildResult.getBuildResult())
        .andReturn(Futures.immediateFuture(Optional.of(failedBuildResult("//super:cool"))))
        .times(2);
    BuildResult localResult = successBuildResult("//hurrah:weeeee");
    EasyMock.expect(buildStrategyContext.runWithDefaultBehavior())
        .andReturn(Futures.immediateFuture(Optional.of(localResult)))
        .once();
    EasyMock.expect(buildStrategyContext.getExecutorService()).andReturn(directExecutor).times(2);

    EasyMock.replay(strategyBuildResult, buildStrategyContext);
    FallbackStrategyBuildResult fallbackStrategyBuildResult =
        new FallbackStrategyBuildResult(RULE_NAME, strategyBuildResult, buildStrategyContext);
    Assert.assertEquals(localResult, fallbackStrategyBuildResult.getBuildResult().get().get());

    EasyMock.verify(strategyBuildResult, buildStrategyContext);
  }

  @Test
  public void testLocalError() throws ExecutionException, InterruptedException {
    EasyMock.expect(strategyBuildResult.getBuildResult())
        .andReturn(Futures.immediateFuture(Optional.of(failedBuildResult("//will/fail:remotely"))))
        .times(2);
    BuildResult localResult = failedBuildResult("//will/fail:locally");
    EasyMock.expect(buildStrategyContext.runWithDefaultBehavior())
        .andReturn(Futures.immediateFuture(Optional.of(localResult)))
        .once();
    EasyMock.expect(buildStrategyContext.getExecutorService()).andReturn(directExecutor).times(2);

    EasyMock.replay(strategyBuildResult, buildStrategyContext);
    FallbackStrategyBuildResult fallbackStrategyBuildResult =
        new FallbackStrategyBuildResult(RULE_NAME, strategyBuildResult, buildStrategyContext);
    Assert.assertEquals(localResult, fallbackStrategyBuildResult.getBuildResult().get().get());

    EasyMock.verify(strategyBuildResult, buildStrategyContext);
  }

  @Test
  public void testLocalException() throws ExecutionException, InterruptedException {
    EasyMock.expect(strategyBuildResult.getBuildResult())
        .andReturn(Futures.immediateFuture(Optional.of(failedBuildResult("//will/fail:remotely"))))
        .times(2);
    Exception exception = new Exception("local failed miserably.");
    EasyMock.expect(buildStrategyContext.runWithDefaultBehavior())
        .andReturn(Futures.immediateFailedFuture(exception))
        .once();
    EasyMock.expect(buildStrategyContext.getExecutorService()).andReturn(directExecutor).times(2);

    EasyMock.replay(strategyBuildResult, buildStrategyContext);
    FallbackStrategyBuildResult fallbackStrategyBuildResult =
        new FallbackStrategyBuildResult(RULE_NAME, strategyBuildResult, buildStrategyContext);
    Assert.assertTrue(fallbackStrategyBuildResult.getBuildResult().isDone());
    try {
      fallbackStrategyBuildResult.getBuildResult().get();
      Assert.fail("Should've thrown...");
    } catch (ExecutionException e) {
      Assert.assertEquals(exception.getMessage(), e.getCause().getMessage());
    }

    EasyMock.verify(strategyBuildResult, buildStrategyContext);
  }

  private static BuildRule buildRule(String name) {
    return new FakeBuildRule(BuildTargetFactory.newInstance(name));
  }

  private static BuildResult failedBuildResult(String buildRuleName) {
    return BuildResult.builder()
        .setStatus(BuildRuleStatus.FAIL)
        .setFailureOptional(new Exception(buildRuleName))
        .setRule(buildRule(buildRuleName))
        .setCacheResult(CacheResult.miss())
        .build();
  }

  private static BuildResult successBuildResult(String buildRuleName) {
    return BuildResult.builder()
        .setStatus(BuildRuleStatus.SUCCESS)
        .setRule(buildRule(buildRuleName))
        .setSuccessOptional(BuildRuleSuccessType.BUILT_LOCALLY)
        .setCacheResult(CacheResult.miss())
        .build();
  }
}
