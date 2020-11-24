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

import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.record.impl.ODocument;

/** Created by Enrico Risa on 25/03/16. */
public class OBackupFinishedLog extends OBackupLog {
  OBackupLogFactory factory = new OBackupLogFactory();
  private String fileName;
  private String path;
  private long elapsedTime = 0;
  private long fileSize = 0;
  private Boolean prevChange;
  private OBackupUploadFinishedLog upload;

  public OBackupFinishedLog(long unitId, long opsId, String uuid, String dbName, String mode) {
    super(unitId, opsId, uuid, dbName, mode);
  }

  @Override
  public ODocument toDoc() {
    ODocument doc = super.toDoc();
    doc.field("path", path);
    doc.field("fileName", fileName);
    doc.field("elapsedTime", elapsedTime);
    doc.field("fileSize", fileSize);
    doc.field("prevChange", prevChange);
    if (upload != null) {
      doc.field("upload", new ORecordId(upload.getInternalId()));
    }
    return doc;
  }

  @Override
  public void fromDoc(ODocument doc) {
    super.fromDoc(doc);
    path = doc.field("path");
    fileName = doc.field("fileName");
    elapsedTime = doc.field("elapsedTime");
    fileSize = doc.field("fileSize");
    prevChange = doc.field("prevChange");

    OIdentifiable upload = doc.field("upload");

    if (upload != null) {
      this.upload = ((OBackupUploadFinishedLog) factory.fromDoc(upload.getRecord()));
    }
  }

  @Override
  public OBackupLogType getType() {
    return OBackupLogType.BACKUP_FINISHED;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getPath() {
    return path;
  }

  public void setFileSize(long fileSize) {
    this.fileSize = fileSize;
  }

  public long getFileSize() {
    return fileSize;
  }

  public String getFileName() {
    return fileName;
  }

  public void setUpload(OBackupUploadFinishedLog upload) {
    this.upload = upload;
  }

  public OBackupUploadFinishedLog getUpload() {
    return upload;
  }

  public long getElapsedTime() {
    return elapsedTime;
  }

  public void setElapsedTime(long elapsedTime) {
    this.elapsedTime = elapsedTime;
  }

  public Boolean getPrevChange() {
    return prevChange;
  }

  public void setPrevChange(final Boolean prevChange) {
    this.prevChange = prevChange;
  }
}
