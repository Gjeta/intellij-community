/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.DumbProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ReadTask;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;
import com.intellij.psi.codeStyle.autodetect.IndentOptionsAdjuster;
import com.intellij.psi.codeStyle.autodetect.IndentOptionsDetectorImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.progress.util.ProgressIndicatorUtils.scheduleWithWriteActionPriority;


class TimeStampedIndentOptions extends IndentOptions {
  private long myTimeStamp;

  public TimeStampedIndentOptions(IndentOptions toCopyFrom, long timeStamp) {
    copyFrom(toCopyFrom);
    myTimeStamp = timeStamp;
  }
  
  void setTimeStamp(long timeStamp) {
    myTimeStamp = timeStamp;
  }

  long getTimeStamp() {
    return myTimeStamp;
  }
  
}

class DetectAndAdjustIndentOptionsTask extends ReadTask {
  private final Document myDocument;
  private final Project myProject;
  private final IndentOptions myOptionsToAdjust;

  public DetectAndAdjustIndentOptionsTask(Project project, Document document, @NotNull IndentOptions toAdjust) {
    myProject = project;
    myDocument = document;
    myOptionsToAdjust = toAdjust;
  }
  
  private PsiFile getFile() {
    if (myProject.isDisposed()) {
      return null;
    }
    return PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument);
  }

  @Nullable
  @Override
  public Continuation performInReadAction(@NotNull ProgressIndicator indicator) throws ProcessCanceledException {
    PsiFile file = getFile();
    if (file == null) {
      return null;
    }
    
    IndentOptionsDetectorImpl detector = new IndentOptionsDetectorImpl(file, indicator);
    IndentOptionsAdjuster adjuster = detector.getIndentOptionsAdjuster();
    if (adjuster != null) {
      return new Continuation(() -> adjustOptions(adjuster));
    }
    
    return null;
  }
  
  private void adjustOptions(IndentOptionsAdjuster adjuster) {
    long stamp = myDocument.getModificationStamp();
    adjuster.adjust(myOptionsToAdjust);
    if (myOptionsToAdjust instanceof TimeStampedIndentOptions) {
      ((TimeStampedIndentOptions)myOptionsToAdjust).setTimeStamp(stamp);
    }
  }

  @Override
  public void onCanceled(@NotNull ProgressIndicator indicator) {
    scheduleInBackgroundForCommittedDocument();
  }

  public void scheduleInBackgroundForCommittedDocument() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      computeInReadAction(new DumbProgressIndicator());
    }
    else {
      PsiDocumentManager manager = PsiDocumentManager.getInstance(myProject);
      manager.performForCommittedDocument(myDocument, () -> scheduleWithWriteActionPriority(this));
    }
  }
  
}