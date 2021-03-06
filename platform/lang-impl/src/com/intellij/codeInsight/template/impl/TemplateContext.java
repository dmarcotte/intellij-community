/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInsight.template.impl;


import com.intellij.codeInsight.template.EverywhereContextType;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TemplateContext {
  private final Map<String, Boolean> myAdditionalContexts = ContainerUtil.newLinkedHashMap();

  public TemplateContext createCopy()  {
    TemplateContext cloneResult = new TemplateContext();
    cloneResult.myAdditionalContexts.putAll(myAdditionalContexts);
    return cloneResult;
  }

  Map<TemplateContextType, Boolean> getDifference(@Nullable TemplateContext defaultContext) {
    Map<TemplateContextType, Boolean> result = ContainerUtil.newHashMap();
    synchronized (myAdditionalContexts) {
      //noinspection NestedSynchronizedStatement
      synchronized (defaultContext == null ? myAdditionalContexts : defaultContext.myAdditionalContexts) {
        for (TemplateContextType contextType : TemplateManagerImpl.getAllContextTypes()) {
          String id = contextType.getContextId();
          Boolean mine = myAdditionalContexts.get(id);
          if (mine != null && differsFromDefault(defaultContext, id, mine)) {
            result.put(contextType, mine);
          }
        }
      }
    }
    return result;
  }

  private static boolean differsFromDefault(@Nullable TemplateContext defaultContext, String id, boolean mine) {
    Boolean defValue = defaultContext == null ? null : defaultContext.myAdditionalContexts.get(id);
    if (defValue == null) {
      return mine;
    }
    return mine != defValue;
  }

  public boolean isEnabled(TemplateContextType contextType) {
    Boolean storedValue = isEnabledBare(contextType);
    if (storedValue == null) {
      TemplateContextType baseContextType = contextType.getBaseContextType();
      if (baseContextType != null && !(baseContextType instanceof EverywhereContextType)) {
        return isEnabled(baseContextType);
      }
      return false;
    }
    return storedValue.booleanValue();
  }

  private Boolean isEnabledBare(TemplateContextType contextType) {
    synchronized (myAdditionalContexts) {
      return myAdditionalContexts.get(contextType.getContextId());
    }
  }

  public void setEnabled(TemplateContextType contextType, boolean value) {
    synchronized (myAdditionalContexts) {
      myAdditionalContexts.put(contextType.getContextId(), value);
    }
  }

  void setDefaultContext(@NotNull TemplateContext defContext) {
    HashMap<String, Boolean> copy = new HashMap<String, Boolean>(myAdditionalContexts);
    myAdditionalContexts.clear();
    myAdditionalContexts.putAll(defContext.myAdditionalContexts);
    myAdditionalContexts.putAll(copy);
  }

  void readTemplateContext(Element element) throws InvalidDataException {
    List options = element.getChildren("option");
    for (Object e : options) {
      if (e instanceof Element) {
        Element option = (Element)e;
        String name = option.getAttributeValue("name");
        String value = option.getAttributeValue("value");
        if (name != null && value != null) {
          myAdditionalContexts.put(name, Boolean.parseBoolean(value));
        }
      }
    }
  }

  void writeTemplateContext(Element element, @Nullable TemplateContext defaultContext) throws WriteExternalException {
    Map<TemplateContextType, Boolean> diff = getDifference(defaultContext);
    for (TemplateContextType type : diff.keySet()) {
      Element optionElement = new Element("option");
      optionElement.setAttribute("name", type.getContextId());
      optionElement.setAttribute("value", diff.get(type).toString());
      element.addContent(optionElement);
    }
  }
}
