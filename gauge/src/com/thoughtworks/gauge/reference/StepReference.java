/*
 * Copyright (C) 2020 ThoughtWorks, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.thoughtworks.gauge.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.ArrayUtil;
import com.thoughtworks.gauge.language.psi.SpecStep;
import com.thoughtworks.gauge.util.StepUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.thoughtworks.gauge.util.GaugeUtil.moduleForPsiElement;

public class StepReference extends PsiReferenceBase<SpecStep> {

  public StepReference(@NotNull SpecStep element) {
    super(element);
  }

  @Override
  public @Nullable PsiElement resolve() {
    return StepUtil.findStepImpl(this.myElement, moduleForPsiElement(this.myElement));
  }

  @Override
  public Object @NotNull [] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public @NotNull TextRange getRangeInElement() {
    return new TextRange(0, myElement.getTextLength());
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    if (element instanceof PsiMethod method) {
      return StepUtil.isMatch(method, this.myElement.getStepValue().getStepText(), moduleForPsiElement(element));
    }
    else {
      return false;
    }
  }
}
