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

package com.orientechnologies.agent.services.backup.log;

import com.orientechnologies.orient.core.record.impl.ODocument;
import java.util.Map;

/** Created by Enrico Risa on 25/03/16. */
public class ORestoreFinishedLog extends OBackupLog {

  private long elapsedTime = 0;
  private String targetDB;
  private Long restoreUnitId;
  private String path;

  protected Map<String, String> metadata;

  public ORestoreFinishedLog(long unitId, long opsId, String uuid, String dbName, String mode) {
    super(unitId, opsId, uuid, dbName, mode);
  }

  @Override
  public OBackupLogType getType() {
    return OBackupLogType.RESTORE_FINISHED;
  }

  @Override
  public ODocument toDoc() {
    ODocument doc = super.toDoc();
    doc.field("elapsedTime", elapsedTime);
    doc.field("targetDB", targetDB);
    doc.field("unitId", restoreUnitId);
    doc.field("path", path);
    doc.field("metadata", metadata);
    return doc;
  }

  public void setTargetDB(String targetDB) {
    this.targetDB = targetDB;
  }

  @Override
  public void fromDoc(ODocument doc) {
    super.fromDoc(doc);
    elapsedTime = doc.field("elapsedTime");
    targetDB = doc.field("targetDB");
    restoreUnitId = doc.field("unitId");
    path = doc.field("path");
    metadata = doc.field("metadata");
  }

  public long getElapsedTime() {
    return elapsedTime;
  }

  public void setElapsedTime(long elapsedTime) {
    this.elapsedTime = elapsedTime;
  }

  public String getTargetDB() {
    return targetDB;
  }

  public Long getRestoreUnitId() {
    return restoreUnitId;
  }

  public void setRestoreUnitId(Long restoreUnitId) {
    this.restoreUnitId = restoreUnitId;
  }

  public void setMetadata(Map<String, String> metadata) {
    this.metadata = metadata;
  }

  public Map<String, String> getMetadata() {
    return metadata;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }
}
