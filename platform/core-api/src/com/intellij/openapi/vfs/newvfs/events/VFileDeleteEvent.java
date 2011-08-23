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

/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.events;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VFileDeleteEvent extends VFileEvent {
  @NotNull private final VirtualFile myFile;
  private int myDepth = -1;

  public VFileDeleteEvent(@Nullable Object requestor, @NotNull VirtualFile file, boolean isFromRefresh) {
    super(requestor, isFromRefresh);
    myFile = file;
  }

  @NotNull
  public VirtualFile getFile() {
    return myFile;
  }

  @NonNls
  public String toString() {
    return "VfsEvent[deleted: " + myFile.getUrl() + "]";
  }

  public String getPath() {
    return myFile.getPath();
  }

  public VirtualFileSystem getFileSystem() {
    return myFile.getFileSystem();
  }

  public boolean isValid() {
    return myFile.isValid();
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final VFileDeleteEvent event = (VFileDeleteEvent)o;

    return myFile.equals(event.myFile);
  }

  public int hashCode() {
    return myFile.hashCode();
  }

  public int getFileDepth() {
    if (myDepth == -1) {
      int d = 0;
      VirtualFile cur = myFile;
      while (cur != null) {
        d++;
        cur = cur.getParent();
      }
      myDepth = d;
    }

    return myDepth;
  }
}