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

package com.facebook.buck.rules;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.shell.Command;
import com.facebook.buck.shell.JarDirectoryCommand;
import com.facebook.buck.command.io.MakeCleanDirectoryCommand;
import com.facebook.buck.command.io.MkdirAndSymlinkFileCommand;
import com.facebook.buck.command.io.MkdirCommand;
import com.facebook.buck.util.BuckConstant;
import com.facebook.buck.util.DefaultDirectoryTraverser;
import com.facebook.buck.util.DirectoryTraverser;
import com.facebook.buck.util.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

public class JavaBinaryRule extends AbstractCachingBuildRule implements BinaryBuildRule,
    HasClasspathEntries {

  @Nullable
  private final String mainClass;

  @Nullable
  private final String manifestFile;

  @Nullable
  private final String metaInfDirectory;

  private final Supplier<ImmutableSet<String>> classpathEntriesSupplier;

  private final DirectoryTraverser directoryTraverser;

  JavaBinaryRule(
      BuildRuleParams buildRuleParams,
      @Nullable String mainClass,
      @Nullable String manifestFile,
      @Nullable String metaInfDirectory,
      DirectoryTraverser directoryTraverser) {
    super(buildRuleParams);
    this.mainClass = mainClass;
    this.manifestFile = manifestFile;
    this.metaInfDirectory = metaInfDirectory;

    this.classpathEntriesSupplier =
        Suppliers.memoize(new Supplier<ImmutableSet<String>>() {
          @Override
          public ImmutableSet<String> get() {
            return ImmutableSet.copyOf(getClasspathEntriesMap().values());
          }
        });
    this.directoryTraverser = Preconditions.checkNotNull(directoryTraverser);
  }

  private void addMetaInfContents(ImmutableSortedSet.Builder<String> files) {
    addInputsToSortedSet(metaInfDirectory, files, directoryTraverser);
  }

  @Override
  protected RuleKey.Builder ruleKeyBuilder() {
    ImmutableSortedSet.Builder<String> metaInfFiles = ImmutableSortedSet.naturalOrder();
    addMetaInfContents(metaInfFiles);

    return super.ruleKeyBuilder()
        .set("mainClass", mainClass)
        .set("manifestFile", manifestFile)
        .set("metaInfDirectory", metaInfFiles.build());
  }

  @Override
  public BuildRuleType getType() {
    return BuildRuleType.JAVA_BINARY;
  }

  @Override
  protected Iterable<String> getInputsToCompareToOutput(BuildContext context) {
    // Build a sorted set so that metaInfDirectory contents are listed in a canonical order.
    ImmutableSortedSet.Builder<String> builder = ImmutableSortedSet.naturalOrder();

    if (manifestFile != null) {
      builder.add(manifestFile);
    }

    addMetaInfContents(builder);

    return builder.build();
  }

  @Override
  protected List<Command> buildInternal(BuildContext context) throws IOException {
    ImmutableList.Builder<Command> commands = ImmutableList.builder();

    String outputDirectory = getOutputDirectory();
    Command mkdir = new MkdirCommand(outputDirectory);
    commands.add(mkdir);

    ImmutableSet<String> includePaths;
    if (metaInfDirectory != null) {
      String stagingRoot = outputDirectory + "/meta_inf_staging";
      String stagingTarget = stagingRoot + "/META-INF";

      MakeCleanDirectoryCommand createStagingRoot = new MakeCleanDirectoryCommand(stagingRoot);
      commands.add(createStagingRoot);

      MkdirAndSymlinkFileCommand link = new MkdirAndSymlinkFileCommand(
          metaInfDirectory, stagingTarget);
      commands.add(link);

      includePaths = ImmutableSet.<String>builder()
          .add(stagingRoot)
          .addAll(getClasspathEntries())
          .build();
    } else {
      includePaths = getClasspathEntries();
    }

    String outputFile = getOutputFile();
    Command jar = new JarDirectoryCommand(outputFile, includePaths, mainClass, manifestFile);
    commands.add(jar);

    return commands.build();
  }

  @Override
  public ImmutableSetMultimap<BuildRule, String> getClasspathEntriesMap() {
    return getClasspathEntriesForDeps();
  }

  @Override
  public ImmutableSet<String> getClasspathEntries() {
    return classpathEntriesSupplier.get();
  }

  private String getOutputDirectory() {
    return String.format("%s/%s", BuckConstant.GEN_DIR, getBuildTarget().getBasePath());
  }

  String getOutputFile() {
    return String.format("%s/%s.jar", getOutputDirectory(), getBuildTarget().getShortName());
  }

  public static Builder newJavaBinaryRuleBuilder() {
    return new Builder();
  }

  @Override
  public String getExecutableCommand() {
    return getExecutableCommand(Collections.<String>emptyList());
  }

  public String getExecutableCommand(List<String> jvmArgs) {
    Preconditions.checkState(mainClass != null,
        "Must specify a main class for %s in order to to run it.",
        getBuildTarget().getFullyQualifiedName());
    StringBuilder cmd = new StringBuilder();
    cmd.append("java");
    if (!jvmArgs.isEmpty()) {
      cmd.append(' ').append(Joiner.on(' ').join(jvmArgs));
    }
    return cmd.append(String.format(" -classpath %s %s",
        Joiner.on(':').join(Iterables.transform(
            getClasspathEntries(),
            Functions.RELATIVE_TO_ABSOLUTE_PATH)),
        mainClass)).toString();
  }

  public static class Builder extends AbstractBuildRuleBuilder {

    private String mainClass;
    private String manifestFile;
    private String metaInfDirectory;

    private Builder() {}

    @Override
    public JavaBinaryRule build(Map<String, BuildRule> buildRuleIndex) {
      return new JavaBinaryRule(createBuildRuleParams(buildRuleIndex),
          mainClass,
          manifestFile,
          metaInfDirectory,
          new DefaultDirectoryTraverser());
    }

    @Override
    public Builder setBuildTarget(BuildTarget buildTarget) {
      super.setBuildTarget(buildTarget);
      return this;
    }

    @Override
    public Builder addDep(String dep) {
      super.addDep(dep);
      return this;
    }

    public Builder setMainClass(String mainClass) {
      this.mainClass = mainClass;
      return this;
    }

    public Builder setManifest(String manifestFile) {
      this.manifestFile = manifestFile;
      return this;
    }

    public Builder setMetaInfDirectory(String metaInfDirectory) {
      this.metaInfDirectory = metaInfDirectory;
      return this;
    }
  }
}
