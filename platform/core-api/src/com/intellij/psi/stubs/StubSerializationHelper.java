/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.stubs;

import com.intellij.openapi.diagnostic.LogUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.util.containers.SLRUCache;
import com.intellij.util.io.AbstractStringEnumerator;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.IOUtil;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import jsr166e.SequenceLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;

/**
 * Author: dmitrylomov
 */
public class StubSerializationHelper {

  private final static Logger LOG = Logger.getInstance(StubSerializationHelper.class);
  private AbstractStringEnumerator myNameStorage;

  protected final TIntObjectHashMap<ObjectStubSerializer> myIdToSerializer = new TIntObjectHashMap<ObjectStubSerializer>();
  protected final TObjectIntHashMap<ObjectStubSerializer> mySerializerToId = new TObjectIntHashMap<ObjectStubSerializer>();

  public StubSerializationHelper(AbstractStringEnumerator nameStorage) {
    myNameStorage = nameStorage;
  }

  public void assignId(@NotNull final ObjectStubSerializer serializer) throws IOException {
    final int id = persistentId(serializer);
    final ObjectStubSerializer old = myIdToSerializer.put(id, serializer);
    assert old == null : "ID: " + serializer.getExternalId() + " is not unique; Already registered serializer with this ID: " + old.getClass().getName();

    final int oldId = mySerializerToId.put(serializer, id);
    assert oldId == 0 : "Serializer " + serializer + " is already registered; Old ID:" + oldId;
  }

  private int persistentId(@NotNull final ObjectStubSerializer serializer) throws IOException {
    if (myNameStorage == null) {
      throw new IOException("SerializationManager's name storage failed to initialize");
    }
    return myNameStorage.enumerate(serializer.getExternalId());
  }

  private void doSerialize(final Stub rootStub, final StubOutputStream stream) throws IOException {
    final ObjectStubSerializer serializer = StubSerializationUtil.getSerializer(rootStub);

    DataInputOutputUtil.writeINT(stream, getClassId(serializer));
    serializer.serialize(rootStub, stream);

    final List<? extends Stub> children = rootStub.getChildrenStubs();
    final int childrenSize = children.size();
    DataInputOutputUtil.writeINT(stream, childrenSize);
    for (int i = 0; i < childrenSize; ++i) {
      doSerialize(children.get(i), stream);
    }
  }

  public void serialize(Stub rootStub, OutputStream stream) throws IOException {
    BufferExposingByteArrayOutputStream out = new BufferExposingByteArrayOutputStream();
    FileLocalStringEnumerator storage = new FileLocalStringEnumerator();
    StubOutputStream stubOutputStream = new StubOutputStream(out, storage);

    doSerialize(rootStub, stubOutputStream);
    DataOutputStream resultStream = new DataOutputStream(stream);
    DataInputOutputUtil.writeINT(resultStream, storage.myStrings.size());
    byte[] buffer = IOUtil.allocReadWriteUTFBuffer();
    for(String s:storage.myStrings) {
      IOUtil.writeUTFFast(buffer, resultStream, s);
    }
    resultStream.write(out.getInternalBuffer(), 0, out.size());
  }

  private int getClassId(final ObjectStubSerializer serializer) {
    final int idValue = mySerializerToId.get(serializer);
    assert idValue != 0: "No ID found for serializer " + LogUtil.objectAndClass(serializer);
    return idValue;
  }

  private static class RecentStringInterner {
    private final int myStripeMask;
    private final SLRUCache<String, String>[] myInterns;
    private final Lock[] myStripeLocks;
    private final LowMemoryWatcher myClearingCallback;

    private RecentStringInterner(int capacity) {
      final int stripes = 16;
      myInterns = new SLRUCache[stripes];
      myStripeLocks = new Lock[myInterns.length];
      for(int i = 0; i < myInterns.length; ++i) {
        myInterns[i] = new SLRUCache<String, String>(capacity / stripes, capacity / stripes) {
          @NotNull
          @Override
          public String createValue(String key) {
            return key;
          }
        };
        myStripeLocks[i] = new SequenceLock();
      }

      assert Integer.highestOneBit(stripes) == stripes;
      myStripeMask = stripes - 1;
      myClearingCallback = LowMemoryWatcher.register(new Runnable() {
        @Override
        public void run() {
          clear();
        };
      });
    }

    String get(String s) {
      final int stripe = Math.abs(s.hashCode()) & myStripeMask;
      try {
        myStripeLocks[stripe].lock();
        return myInterns[stripe].get(s);
      } finally {
        myStripeLocks[stripe].unlock();
      }
    }

    void clear() {
      for(int i = 0; i < myInterns.length; ++i) {
        myStripeLocks[i].lock();
        myInterns[i].clear();
        myStripeLocks[i].unlock();
      }
    }
  }

  private final RecentStringInterner myStringInterner = new RecentStringInterner(8192);

  public Stub deserialize(InputStream stream) throws IOException, SerializerNotFoundException {
    FileLocalStringEnumerator storage = new FileLocalStringEnumerator();
    StubInputStream inputStream = new StubInputStream(stream, storage);
    final int size = DataInputOutputUtil.readINT(inputStream);
    byte[] buffer = IOUtil.allocReadWriteUTFBuffer();

    int i = 1;
    while(i <= size) {
      String s = myStringInterner.get(IOUtil.readUTFFast(buffer, inputStream));
      storage.myStrings.add(s);
      storage.myEnumerates.put(s, i);
      ++i;
    }
    return deserialize(inputStream, null);
  }

  private Stub deserialize(StubInputStream stream, @Nullable Stub parentStub) throws IOException, SerializerNotFoundException {
    final int id = DataInputOutputUtil.readINT(stream);
    final ObjectStubSerializer serializer = getClassById(id);
    if (serializer == null) {
      throw new SerializerNotFoundException("No serializer registered for stub: ID=" + id + "; parent stub class=" + (parentStub != null? parentStub.getClass().getName() : "null"));
    }

    Stub stub = serializer.deserialize(stream, parentStub);
    int childCount = DataInputOutputUtil.readINT(stream);
    for (int i = 0; i < childCount; i++) {
      deserialize(stream, stub);
    }
    return stub;
  }


  private ObjectStubSerializer getClassById(int id) {
    return myIdToSerializer.get(id);
  }

  private static class FileLocalStringEnumerator implements AbstractStringEnumerator {
    private final TObjectIntHashMap<String> myEnumerates = new TObjectIntHashMap<String>();
    private final ArrayList<String> myStrings = new ArrayList<String>();

    @Override
    public int enumerate(@Nullable String value) throws IOException {
      if (value == null) return 0;
      int i = myEnumerates.get(value);
      if (i == 0) {
        myEnumerates.put(value, i = myStrings.size() + 1);
        myStrings.add(value);
      }
      return i;
    }

    @Override
    public String valueOf(int idx) throws IOException {
      if (idx == 0) return null;
      return myStrings.get(idx - 1);
    }

    @Override
    public void markCorrupted() {
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public boolean isDirty() {
      return false;
    }

    @Override
    public void force() {
    }
  }
}
