// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.terraform

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import org.jetbrains.annotations.ApiStatus.Internal

//todo the file has to be removed as soon as problems with missing progress indicators in GTD/quick docs/name your place are fixed
@Internal
fun <T> withGuaranteedProgressIndicator(action: () -> T): T {
  return if (ApplicationManager.getApplication().isDispatchThread || ProgressManager.getInstance().hasProgressIndicator())
    action()
  else {
    runBlockingCancellable {
      coroutineToIndicator {
        action()
      }
    }
  }
}
