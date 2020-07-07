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

/** Created by Enrico Risa on 01/04/16. */
public class OBackupScheduledLog extends OBackupLog {

  public long nextExecution = 0;

  public OBackupScheduledLog(long unitId, long opsId, String uuid, String dbName, String mode) {
    super(unitId, opsId, uuid, dbName, mode);
  }

  @Override
  public void fromDoc(ODocument doc) {
    super.fromDoc(doc);
    nextExecution = doc.field("nextExecution");
  }

  @Override
  public ODocument toDoc() {
    ODocument document = super.toDoc();
    document.field("nextExecution", nextExecution);
    return document;
  }

  @Override
  public OBackupLogType getType() {
    return OBackupLogType.BACKUP_SCHEDULED;
  }

  public long getNextExecution() {
    return nextExecution;
  }

  public void setNextExecution(long nextExecution) {
    this.nextExecution = nextExecution;
  }
}
