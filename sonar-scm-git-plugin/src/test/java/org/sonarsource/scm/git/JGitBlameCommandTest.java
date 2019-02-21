/*
 * SonarQube :: Plugins :: SCM :: Git
 * Copyright (C) 2014-2019 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.scm.git;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultFileSystem;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.scm.BlameCommand.BlameInput;
import org.sonar.api.batch.scm.BlameCommand.BlameOutput;
import org.sonar.api.batch.scm.BlameLine;
import org.sonar.api.notifications.AnalysisWarnings;
import org.sonar.api.scan.filesystem.PathResolver;
import org.sonar.api.utils.DateUtils;
import org.sonar.api.utils.MessageException;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.log.LogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.sonarsource.scm.git.Utils.javaUnzip;

public class JGitBlameCommandTest {

  private static final String DUMMY_JAVA = "src/main/java/org/dummy/Dummy.java";

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public LogTester logTester = new LogTester();

  private final BlameInput input = mock(BlameInput.class);

  @Test
  public void testBlame() throws IOException {
    File projectDir = temp.newFolder();
    javaUnzip(new File("test-repos/dummy-git.zip"), projectDir);

    JGitBlameCommand jGitBlameCommand = newJGitBlameCommand();

    File baseDir = new File(projectDir, "dummy-git");
    DefaultFileSystem fs = new DefaultFileSystem(baseDir);
    when(input.fileSystem()).thenReturn(fs);
    DefaultInputFile inputFile = new TestInputFileBuilder("foo", DUMMY_JAVA)
      .setModuleBaseDir(baseDir.toPath())
      .build();
    fs.add(inputFile);

    BlameOutput blameResult = mock(BlameOutput.class);
    when(input.filesToBlame()).thenReturn(Arrays.<InputFile>asList(inputFile));
    jGitBlameCommand.blame(input, blameResult);

    Date revisionDate1 = DateUtils.parseDateTime("2012-07-17T16:12:48+0200");
    String revision1 = "6b3aab35a3ea32c1636fee56f996e677653c48ea";
    String author1 = "david@gageot.net";

    // second commit, which has a commit date different than the author date
    Date revisionDate2 = DateUtils.parseDateTime("2015-05-19T13:31:09+0200");
    String revision2 = "0d269c1acfb8e6d4d33f3c43041eb87e0df0f5e7";
    String author2 = "duarte.meneses@sonarsource.com";

    List<BlameLine> expectedBlame = new LinkedList<>();
    for (int i = 0; i < 25; i++) {
      expectedBlame.add(new BlameLine().revision(revision1).date(revisionDate1).author(author1));
    }
    for (int i = 0; i < 3; i++) {
      expectedBlame.add(new BlameLine().revision(revision2).date(revisionDate2).author(author2));
    }
    for (int i = 0; i < 1; i++) {
      expectedBlame.add(new BlameLine().revision(revision1).date(revisionDate1).author(author1));
    }

    verify(blameResult).blameResult(inputFile, expectedBlame);
  }

  @Test
  public void properFailureIfNotAGitProject() throws IOException {
    File projectDir = temp.newFolder();
    javaUnzip(new File("test-repos/dummy-git.zip"), projectDir);

    JGitBlameCommand jGitBlameCommand = newJGitBlameCommand();

    File baseDir = new File(projectDir, "dummy-git");

    // Delete .git
    FileUtils.forceDelete(new File(baseDir, ".git"));

    DefaultFileSystem fs = new DefaultFileSystem(baseDir);
    when(input.fileSystem()).thenReturn(fs);
    DefaultInputFile inputFile = new TestInputFileBuilder("foo", DUMMY_JAVA).build();
    fs.add(inputFile);

    BlameOutput blameResult = mock(BlameOutput.class);
    when(input.filesToBlame()).thenReturn(Arrays.<InputFile>asList(inputFile));

    thrown.expect(MessageException.class);
    thrown.expectMessage("Not inside a Git work tree: ");

    jGitBlameCommand.blame(input, blameResult);
  }

  @Test
  public void testBlameOnNestedModule() throws IOException {
    File projectDir = temp.newFolder();
    javaUnzip(new File("test-repos/dummy-git-nested.zip"), projectDir);

    JGitBlameCommand jGitBlameCommand = newJGitBlameCommand();

    File baseDir = new File(projectDir, "dummy-git-nested/dummy-project");
    DefaultFileSystem fs = new DefaultFileSystem(baseDir);
    when(input.fileSystem()).thenReturn(fs);
    DefaultInputFile inputFile = new TestInputFileBuilder("foo", DUMMY_JAVA)
      .setModuleBaseDir(baseDir.toPath())
      .build();
    fs.add(inputFile);

    BlameOutput blameResult = mock(BlameOutput.class);
    when(input.filesToBlame()).thenReturn(Arrays.<InputFile>asList(inputFile));
    jGitBlameCommand.blame(input, blameResult);

    Date revisionDate = DateUtils.parseDateTime("2012-07-17T16:12:48+0200");
    String revision = "6b3aab35a3ea32c1636fee56f996e677653c48ea";
    String author = "david@gageot.net";
    verify(blameResult).blameResult(inputFile,
      Arrays.asList(
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author)));
  }

  @Test
  public void dontFailOnModifiedFile() throws IOException {
    File projectDir = temp.newFolder();
    javaUnzip(new File("test-repos/dummy-git.zip"), projectDir);

    JGitBlameCommand jGitBlameCommand = newJGitBlameCommand();

    File baseDir = new File(projectDir, "dummy-git");
    DefaultFileSystem fs = new DefaultFileSystem(baseDir);
    when(input.fileSystem()).thenReturn(fs);
    String relativePath = DUMMY_JAVA;
    DefaultInputFile inputFile = new TestInputFileBuilder("foo", relativePath).build();
    fs.add(inputFile);

    // Emulate a modification
    Files.write(baseDir.toPath().resolve(relativePath), "modification and \n some new line".getBytes());

    BlameOutput blameResult = mock(BlameOutput.class);

    when(input.filesToBlame()).thenReturn(Arrays.<InputFile>asList(inputFile));
    jGitBlameCommand.blame(input, blameResult);
  }

  @Test
  public void dontFailOnNewFile() throws IOException {
    File projectDir = temp.newFolder();
    javaUnzip(new File("test-repos/dummy-git.zip"), projectDir);

    JGitBlameCommand jGitBlameCommand = newJGitBlameCommand();

    File baseDir = new File(projectDir, "dummy-git");
    DefaultFileSystem fs = new DefaultFileSystem(baseDir);
    when(input.fileSystem()).thenReturn(fs);
    String relativePath = DUMMY_JAVA;
    String relativePath2 = "src/main/java/org/dummy/Dummy2.java";
    DefaultInputFile inputFile = new TestInputFileBuilder("foo", relativePath).build();
    fs.add(inputFile);
    DefaultInputFile inputFile2 = new TestInputFileBuilder("foo", relativePath2).build();
    fs.add(inputFile2);

    // Emulate a new file
    FileUtils.copyFile(new File(baseDir, relativePath), new File(baseDir, relativePath2));

    BlameOutput blameResult = mock(BlameOutput.class);

    when(input.filesToBlame()).thenReturn(Arrays.<InputFile>asList(inputFile, inputFile2));
    jGitBlameCommand.blame(input, blameResult);
  }

  @Test
  public void dontFailOnSymlink() throws IOException {
    assumeTrue(!System2.INSTANCE.isOsWindows());
    File projectDir = temp.newFolder();
    javaUnzip(new File("test-repos/dummy-git.zip"), projectDir);

    JGitBlameCommand jGitBlameCommand = newJGitBlameCommand();

    File baseDir = new File(projectDir, "dummy-git");
    DefaultFileSystem fs = new DefaultFileSystem(baseDir);
    when(input.fileSystem()).thenReturn(fs);
    String relativePath = DUMMY_JAVA;
    String relativePath2 = "src/main/java/org/dummy/Dummy2.java";
    DefaultInputFile inputFile = new TestInputFileBuilder("foo", relativePath)
      .setModuleBaseDir(baseDir.toPath())
      .build();
    fs.add(inputFile);
    DefaultInputFile inputFile2 = new TestInputFileBuilder("foo", relativePath2)
      .setModuleBaseDir(baseDir.toPath())
      .build();
    fs.add(inputFile2);

    // Create symlink
    Files.createSymbolicLink(inputFile2.file().toPath(), inputFile.file().toPath());

    BlameOutput blameResult = mock(BlameOutput.class);

    when(input.filesToBlame()).thenReturn(Arrays.<InputFile>asList(inputFile, inputFile2));
    jGitBlameCommand.blame(input, blameResult);
  }

  @Test
  public void return_early_when_shallow_clone_detected() throws IOException {
    File projectDir = temp.newFolder();
    javaUnzip(new File("test-repos/shallow-git.zip"), projectDir);

    File baseDir = new File(projectDir, "shallow-git");

    DefaultFileSystem fs = new DefaultFileSystem(baseDir);
    when(input.fileSystem()).thenReturn(fs);

    DefaultInputFile inputFile = new TestInputFileBuilder("foo", DUMMY_JAVA).build();
    when(input.filesToBlame()).thenReturn(Collections.singleton(inputFile));

    // register warning with default wrapper
    AnalysisWarnings analysisWarnings = mock(AnalysisWarnings.class);
    AnalysisWarningsWrapper analysisWarningsWrapper = new DefaultAnalysisWarningsWrapper(analysisWarnings);
    JGitBlameCommand jGitBlameCommand = new JGitBlameCommand(new PathResolver(), analysisWarningsWrapper);
    BlameOutput output = mock(BlameOutput.class);
    jGitBlameCommand.blame(input, output);

    assertThat(logTester.logs()).first()
      .matches(s -> s.contains("Shallow clone detected, no blame information will be provided."));
    verifyZeroInteractions(output);

    verify(analysisWarnings).addUnique(startsWith("Shallow clone detected"));

    // do not register warning with noop wrapper
    jGitBlameCommand = new JGitBlameCommand(new PathResolver(), new NoOpAnalysisWarningsWrapper());
    jGitBlameCommand.blame(input, output);

    verifyNoMoreInteractions(analysisWarnings);
  }

  @Test
  public void testBlameSubmodules() throws IOException {
    File projectDir = temp.newFolder();
    javaUnzip(new File("test-repos/submodule-git.zip"), projectDir);

    JGitBlameCommand jGitBlameCommand = newJGitBlameCommand();

    File baseDir = new File(projectDir, "submodule-git");
    DefaultFileSystem fs = new DefaultFileSystem(baseDir);
    when(input.fileSystem()).thenReturn(fs);
    DefaultInputFile inputFile = new TestInputFileBuilder("foo", "lib/file")
      .setModuleBaseDir(baseDir.toPath())
      .build();
    fs.add(inputFile);

    BlameOutput blameResult = mock(BlameOutput.class);
    when(input.filesToBlame()).thenReturn(Arrays.<InputFile>asList(inputFile));
    jGitBlameCommand.blame(input, blameResult);

    String author = "email@example.com";
    Date revisionDate = DateUtils.parseDateTime("2019-02-20T15:41:11+0000");
    String revision = "42de86a491cad6540f3edbb668fc2bc6383f10d8";

    List<BlameLine> expectedBlame = new LinkedList<>();
    expectedBlame.add(new BlameLine().revision(revision).date(revisionDate).author(author));

    verify(blameResult).blameResult(inputFile, expectedBlame);
  }

  private JGitBlameCommand newJGitBlameCommand() {
    return new JGitBlameCommand(new PathResolver(), mock(AnalysisWarningsWrapper.class));
  }
}