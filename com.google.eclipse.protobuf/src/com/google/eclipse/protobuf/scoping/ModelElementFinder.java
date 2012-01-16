/*
 * Copyright (c) 2011 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.scoping;

import static com.google.common.collect.Sets.newHashSet;
import static java.util.Collections.*;
import static org.eclipse.emf.ecore.util.EcoreUtil.getAllContents;

import com.google.eclipse.protobuf.model.util.*;
import com.google.eclipse.protobuf.protobuf.*;
import com.google.eclipse.protobuf.protobuf.Package;
import com.google.inject.Inject;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.*;
import org.eclipse.xtext.resource.IEObjectDescription;

import java.util.*;

/**
 * @author alruiz@google.com (Alex Ruiz)
 */
class ModelElementFinder {
  @Inject private ModelFinder modelFinder;
  @Inject private Imports imports;
  @Inject private Packages packages;
  @Inject private Protobufs protobufs;
  @Inject private Resources resources;

  Collection<IEObjectDescription> find(EObject start, ModelElementFinderDelegate finder, Object criteria) {
    Set<IEObjectDescription> descriptions = newHashSet();
    EObject current = start.eContainer();
    while (current != null) {
      descriptions.addAll(local(current, finder, criteria));
      current = current.eContainer();
    }
    Protobuf root = modelFinder.rootOf(start);
    descriptions.addAll(imported(root, finder, criteria));
    return unmodifiableSet(descriptions);
  }

  Collection<IEObjectDescription> find(Protobuf start, ModelElementFinderDelegate finder, Object criteria) {
    Set<IEObjectDescription> descriptions = newHashSet();
    descriptions.addAll(local(start, finder, criteria));
    descriptions.addAll(imported(start, finder, criteria));
    return unmodifiableSet(descriptions);
  }

  private Collection<IEObjectDescription> local(EObject start, ModelElementFinderDelegate finder, Object criteria) {
    return local(start, finder, criteria, 0);
  }

  private Collection<IEObjectDescription> local(EObject start, ModelElementFinderDelegate finder, Object criteria,
      int level) {
    Set<IEObjectDescription> descriptions = newHashSet();
    for (EObject element : start.eContents()) {
      descriptions.addAll(finder.local(element, criteria, level));
      if (element instanceof Message || element instanceof Group) {
        descriptions.addAll(local(element, finder, criteria, level + 1));
      }
    }
    return descriptions;
  }

  private Collection<IEObjectDescription> imported(Protobuf start, ModelElementFinderDelegate finder, Object criteria) {
    List<Import> allImports = modelFinder.importsIn(start);
    if (allImports.isEmpty()) {
      return emptyList();
    }
    ResourceSet resourceSet = start.eResource().getResourceSet();
    return imported(allImports, modelFinder.packageOf(start), resourceSet, finder, criteria);
  }

  private Collection<IEObjectDescription> imported(List<Import> allImports, Package fromImporter,
      ResourceSet resourceSet, ModelElementFinderDelegate finder, Object criteria) {
    Set<IEObjectDescription> descriptions = newHashSet();
    for (Import anImport : allImports) {
      if (imports.isImportingDescriptor(anImport)) {
        descriptions.addAll(finder.inDescriptor(anImport, criteria));
        continue;
      }
      Resource imported = resources.importedResource(anImport, resourceSet);
      if (imported == null) {
        continue;
      }
      Protobuf rootOfImported = modelFinder.rootOf(imported);
      if (!protobufs.isProto2(rootOfImported)) {
        continue;
      }
      if (rootOfImported != null) {
        descriptions.addAll(publicImported(rootOfImported, finder, criteria));
        if (arePackagesRelated(fromImporter, rootOfImported)) {
          descriptions.addAll(local(rootOfImported, finder, criteria));
          continue;
        }
        Package packageOfImported = modelFinder.packageOf(rootOfImported);
        descriptions.addAll(imported(fromImporter, packageOfImported, imported, finder, criteria));
      }
    }
    return descriptions;
  }

  private Collection<IEObjectDescription> publicImported(Protobuf start, ModelElementFinderDelegate finder,
      Object criteria) {
    if (!protobufs.isProto2(start)) {
      return emptySet();
    }
    List<Import> allImports = modelFinder.publicImportsIn(start);
    if (allImports.isEmpty()) {
      return emptyList();
    }
    ResourceSet resourceSet = start.eResource().getResourceSet();
    return imported(allImports, modelFinder.packageOf(start), resourceSet, finder, criteria);
  }

  private boolean arePackagesRelated(Package aPackage, EObject root) {
    Package p = modelFinder.packageOf(root);
    return packages.areRelated(aPackage, p);
  }

  private Collection<IEObjectDescription> imported(Package fromImporter, Package fromImported, Resource resource,
      ModelElementFinderDelegate finder, Object criteria) {
    Set<IEObjectDescription> descriptions = newHashSet();
    TreeIterator<Object> contents = getAllContents(resource, true);
    while (contents.hasNext()) {
      Object next = contents.next();
      descriptions.addAll(finder.imported(fromImporter, fromImported, next, criteria));
    }
    return descriptions;
  }
}