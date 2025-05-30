// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.plugins.jade.breadcrumbs;

import com.intellij.lang.Language;
import com.intellij.xml.breadcrumbs.XmlLanguageBreadcrumbsInfoProvider;
import com.jetbrains.plugins.jade.JadeLanguage;

public final class JadeBreadcrumbsInfoProvider extends XmlLanguageBreadcrumbsInfoProvider {
  @Override
  public Language[] getLanguages() {
    return new Language[]{JadeLanguage.INSTANCE};
  }
}
