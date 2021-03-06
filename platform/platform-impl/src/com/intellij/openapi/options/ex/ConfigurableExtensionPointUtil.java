/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.options.ex;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.options.ConfigurableProvider;
import com.intellij.openapi.options.OptionalConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
public class ConfigurableExtensionPointUtil {

  private final static Logger LOG = Logger.getInstance(ConfigurableExtensionPointUtil.class);

  private ConfigurableExtensionPointUtil() {
  }


  public static List<Configurable> buildConfigurablesList(final ConfigurableEP<Configurable>[] extensions,
                                                          final Configurable[] components,
                                                          @Nullable ConfigurableFilter filter,
                                                          ExtensionPointName<ConfigurableEP<Configurable>> configurablesExtensionPoint) {
    List<Configurable> result = new ArrayList<Configurable>();
    Map<String, ConfigurableWrapper> idToConfigurable = new HashMap<String, ConfigurableWrapper>();
    List<ConfigurableWrapper> orphans = new ArrayList<ConfigurableWrapper>();
    for (ConfigurableEP<Configurable> ep : extensions) {
      final Configurable configurable = ConfigurableWrapper.wrapConfigurable(ep);
      if (configurable instanceof ConfigurableWrapper) {
        ConfigurableWrapper wrapper = (ConfigurableWrapper)configurable;
        if (wrapper.getParentId() != null) {
          orphans.add(wrapper);
        } else {
          idToConfigurable.put(wrapper.getId(), wrapper);
        }
      }
      else {
//        dumpConfigurable(configurablesExtensionPoint, ep, configurable);
        ContainerUtil.addIfNotNull(configurable, result);
      }
    }
    ContainerUtil.addAll(result, components);

    for (ConfigurableWrapper orphan : orphans) {
      String parentId = orphan.getParentId();
      ConfigurableWrapper parent = idToConfigurable.get(parentId);
      LOG.assertTrue(parent != null, "Can't find parent for " + parentId + " (" + orphan + ")");
      idToConfigurable.put(parentId, parent.addChild(orphan));
    }

    ContainerUtil.addAll(result, idToConfigurable.values());

    final ListIterator<Configurable> iterator = result.listIterator();
    while (iterator.hasNext()) {
      Configurable each = iterator.next();
      if (each instanceof Configurable.Assistant
          || each instanceof OptionalConfigurable && !((OptionalConfigurable) each).needDisplay()
          || filter != null && !filter.isIncluded(each)) {
        iterator.remove();
      }
    }

    return result;
  }

  /*
  private static void dumpConfigurable(ExtensionPointName<ConfigurableEP<Configurable>> configurablesExtensionPoint,
                                       ConfigurableEP<Configurable> ep,
                                       Configurable configurable) {
    if (configurable != null && !(configurable instanceof ConfigurableGroup)) {
      if (ep.instanceClass != null && (configurable instanceof SearchableConfigurable) && (configurable instanceof Configurable.Composite)) {
        Element element = dump(ep, configurable, StringUtil.getShortName(configurablesExtensionPoint.getName()));
        final Configurable[] configurables = ((Configurable.Composite)configurable).getConfigurables();
        for (Configurable child : configurables) {
          final Element dump = dump(null, child, "configurable");
          element.addContent(dump);
        }
        final StringWriter out = new StringWriter();
        try {
          new XMLOutputter(Format.getPrettyFormat()).output(element, out);
        }
        catch (IOException e) {
        }
        System.out.println(out);
      }
    }
  }

  private static Element dump(@Nullable ConfigurableEP ep,
                              Configurable configurable, String name) {
    Element element = new Element(name);
    if (ep != null) {
      element.setAttribute("instance", ep.instanceClass);
      String id = ep.id == null ? ((SearchableConfigurable)configurable).getId() : ep.id;
      element.setAttribute("id", id);
    }
    else {
      element.setAttribute("instance", configurable.getClass().getName());
      if (configurable instanceof SearchableConfigurable) {
        element.setAttribute("id", ((SearchableConfigurable)configurable).getId());
      }
    }

    CommonBundle.lastKey = null;
    String displayName = configurable.getDisplayName();
    if (CommonBundle.lastKey != null) {
      element.setAttribute("key", CommonBundle.lastKey).setAttribute("bundle", CommonBundle.lastBundle);
    }
    else {
      element.setAttribute("displayName", displayName);
    }
    if (configurable instanceof NonDefaultProjectConfigurable) {
      element.setAttribute("nonDefaultProject", "true");
    }
    return element;
  }
  */

  /**
   * @deprecated create a new instance of configurable instead
   */
  @NotNull
  public static <T extends Configurable> T findProjectConfigurable(@NotNull Project project, @NotNull Class<T> configurableClass) {
    return findConfigurable(project.getExtensions(Configurable.PROJECT_CONFIGURABLE), configurableClass);
  }

  @NotNull
  public static <T extends Configurable> T findApplicationConfigurable(@NotNull Class<T> configurableClass) {
    return findConfigurable(Configurable.APPLICATION_CONFIGURABLE.getExtensions(), configurableClass);
  }

  @NotNull
  private static <T extends Configurable> T findConfigurable(ConfigurableEP<Configurable>[] extensions, Class<T> configurableClass) {
    for (ConfigurableEP<Configurable> extension : extensions) {
      if (extension.providerClass != null || extension.instanceClass != null || extension.implementationClass != null) {
        final Configurable configurable = extension.createConfigurable();
        if (configurableClass.isInstance(configurable)) {
          return configurableClass.cast(configurable);
        }
      }
    }
    throw new IllegalArgumentException("Cannot find configurable of " + configurableClass);
  }

  @Nullable
  public static Configurable createProjectConfigurableForProvider(@NotNull Project project, Class<? extends ConfigurableProvider> providerClass) {
    return createConfigurableForProvider(project.getExtensions(Configurable.PROJECT_CONFIGURABLE), providerClass);
  }

  @Nullable
  public static Configurable createApplicationConfigurableForProvider(Class<? extends ConfigurableProvider> providerClass) {
    return createConfigurableForProvider(Configurable.APPLICATION_CONFIGURABLE.getExtensions(), providerClass);
  }

  @Nullable
  private static Configurable createConfigurableForProvider(ConfigurableEP<Configurable>[] extensions, Class<? extends ConfigurableProvider> providerClass) {
    for (ConfigurableEP<Configurable> extension : extensions) {
      if (extension.providerClass != null) {
        final Class<Object> aClass = extension.findClassNoExceptions(extension.providerClass);
        if (aClass != null && providerClass.isAssignableFrom(aClass)) {
          return extension.createConfigurable();
        }
      }
    }
    return null;
  }
}
