/*
 * Copyright 2015 OrientDB LTD (info(at)orientdb.com)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *   For more information: http://www.orientdb.com
 */

package com.orientechnologies.orient.core.storage.impl.local.paginated;

import com.orientechnologies.common.io.OFileUtils;
import com.orientechnologies.orient.core.command.OCommandOutputListener;
import com.orientechnologies.orient.core.db.*;
import com.orientechnologies.orient.core.db.document.ODatabaseDocument;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.db.tool.ODatabaseCompare;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OSchema;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.storage.OStorage;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * @author Andrey Lomakin <lomakin.andrey@gmail.com>.
 * @since 9/9/2015
 */

public class StorageBackupTest {
  private String buildDirectory;

  @Before
  public void before() {
    buildDirectory = System.getProperty("buildDirectory", ".");
  }

  @Test
  public void testSingeThreadFullBackup() throws IOException {
    final String dbName = StorageBackupTest.class.getSimpleName();
    final String dbDirectory = buildDirectory + File.separator + dbName;

    OFileUtils.deleteRecursively(new File(dbDirectory));

    OrientDB orientDB = new OrientDB("embedded:" + buildDirectory, OrientDBConfig.defaultConfig());
    orientDB.create(dbName, ODatabaseType.PLOCAL);

    ODatabaseDocument db = orientDB.open(dbName, "admin", "admin");

    final OSchema schema = db.getMetadata().getSchema();
    final OClass backupClass = schema.createClass("BackupClass");
    backupClass.createProperty("num", OType.INTEGER);
    backupClass.createProperty("data", OType.BINARY);

    backupClass.createIndex("backupIndex", OClass.INDEX_TYPE.NOTUNIQUE, "num");

    final Random random = new Random();
    for (int i = 0; i < 1_000_000; i++) {
      final byte[] data = new byte[16];
      random.nextBytes(data);

      final int num = random.nextInt();

      final ODocument document = new ODocument("BackupClass");
      document.field("num", num);
      document.field("data", data);

      document.save();
    }

    final File backupDir = new File(buildDirectory, "backupDir");
    OFileUtils.deleteRecursively(backupDir);

    if (!backupDir.exists())
      Assert.assertTrue(backupDir.mkdirs());

    db.incrementalBackup(backupDir.getAbsolutePath());
    orientDB.close();

    final String backupDbName = StorageBackupTest.class.getSimpleName() + "BackUp";
    final String backedUpDbDirectory = buildDirectory + File.separator + backupDbName;

    OFileUtils.deleteRecursively(new File(backedUpDbDirectory));

    OrientDBEmbedded embedded = (OrientDBEmbedded) OrientDBInternal.embedded(buildDirectory, OrientDBConfig.defaultConfig());
    embedded.restore(backupDbName, null, null, null, backupDir.getAbsolutePath(), OrientDBConfig.defaultConfig());
    embedded.close();

    final ODatabaseCompare compare = new ODatabaseCompare("plocal:" + dbDirectory, "plocal:" + backedUpDbDirectory, "admin",
        "admin", new OCommandOutputListener() {
      @Override
      public void onMessage(String iText) {
        System.out.println(iText);
      }
    });

    Assert.assertTrue(compare.compare());

    ODatabaseDocumentTx.closeAll();

    orientDB = new OrientDB("embedded:" + buildDirectory, OrientDBConfig.defaultConfig());
    orientDB.drop(dbName);
    orientDB.drop(backupDbName);

    orientDB.close();

    OFileUtils.deleteRecursively(backupDir);
  }

  @Test
  public void testSingeThreadIncrementalBackup() throws IOException {
    final String dbDirectory = buildDirectory + File.separator + StorageBackupTest.class.getSimpleName();
    OFileUtils.deleteRecursively(new File(dbDirectory));

    OrientDB orientDB = new OrientDB("embedded:" + buildDirectory, OrientDBConfig.defaultConfig());

    final String dbName = StorageBackupTest.class.getSimpleName();
    orientDB.create(dbName, ODatabaseType.PLOCAL);

    ODatabaseDocument db = orientDB.open(dbName, "admin", "admin");

    final OSchema schema = db.getMetadata().getSchema();
    final OClass backupClass = schema.createClass("BackupClass");
    backupClass.createProperty("num", OType.INTEGER);
    backupClass.createProperty("data", OType.BINARY);

    backupClass.createIndex("backupIndex", OClass.INDEX_TYPE.NOTUNIQUE, "num");

    final Random random = new Random();
    for (int i = 0; i < 1_000_000; i++) {
      final byte[] data = new byte[16];
      random.nextBytes(data);

      final int num = random.nextInt();

      final ODocument document = new ODocument("BackupClass");
      document.field("num", num);
      document.field("data", data);

      document.save();
    }

    final File backupDir = new File(buildDirectory, "backupDir");
    OFileUtils.deleteRecursively(backupDir);

    if (!backupDir.exists())
      Assert.assertTrue(backupDir.mkdirs());

    db.incrementalBackup(backupDir.getAbsolutePath());

    for (int n = 0; n < 3; n++) {
      for (int i = 0; i < 1_000_000; i++) {
        final byte[] data = new byte[16];
        random.nextBytes(data);

        final int num = random.nextInt();

        final ODocument document = new ODocument("BackupClass");
        document.field("num", num);
        document.field("data", data);

        document.save();
      }

      db.incrementalBackup(backupDir.getAbsolutePath());
    }

    db.incrementalBackup(backupDir.getAbsolutePath());
    db.close();

    orientDB.close();

    final String backupDbName = StorageBackupTest.class.getSimpleName() + "BackUp";

    final String backedUpDbDirectory = buildDirectory + File.separator + backupDbName;
    OFileUtils.deleteRecursively(new File(backedUpDbDirectory));

    OrientDBEmbedded embedded = (OrientDBEmbedded) OrientDBInternal.embedded(buildDirectory, OrientDBConfig.defaultConfig());

    embedded.restore(backupDbName, null, null, null, backupDir.getAbsolutePath(), OrientDBConfig.defaultConfig());
    embedded.close();

    final ODatabaseCompare compare = new ODatabaseCompare("plocal:" + dbDirectory, "plocal:" + backedUpDbDirectory, "admin",
        "admin", new OCommandOutputListener() {
      @Override
      public void onMessage(String iText) {
        System.out.println(iText);
      }
    });

    Assert.assertTrue(compare.compare());

    ODatabaseDocumentTx.closeAll();

    orientDB = new OrientDB("embedded:" + buildDirectory, OrientDBConfig.defaultConfig());
    orientDB.drop(dbName);
    orientDB.drop(backupDbName);

    orientDB.close();

    OFileUtils.deleteRecursively(backupDir);
  }
}