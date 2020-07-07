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

/** Created by Enrico Risa on 31/03/16. */
public class OBackupLogFactory {

  public OBackupLog fromDoc(ODocument doc) {

    String opString = doc.field(OBackupLog.OP);
    OBackupLogType op = OBackupLogType.valueOf(opString);
    long unitId = doc.field(OBackupLog.UNITID);
    long txId = doc.field(OBackupLog.TXID);
    String uuid = doc.field(OBackupLog.UUID);
    String dbName = doc.field(OBackupLog.DBNAME);
    String mode = doc.field(OBackupLog.MODE);

    OBackupLog log = null;
    switch (op) {
      case BACKUP_STARTED:
        log = new OBackupStartedLog(unitId, txId, uuid, dbName, mode);
        log.fromDoc(doc);
        break;
      case BACKUP_ERROR:
        log = new OBackupErrorLog(unitId, txId, uuid, dbName, mode);
        log.fromDoc(doc);
        break;
      case BACKUP_FINISHED:
        log = new OBackupFinishedLog(unitId, txId, uuid, dbName, mode);
        log.fromDoc(doc);
        break;
      case BACKUP_SCHEDULED:
        log = new OBackupScheduledLog(unitId, txId, uuid, dbName, mode);
        log.fromDoc(doc);
        break;
      case RESTORE_ERROR:
        log = new ORestoreErrorLog(unitId, txId, uuid, dbName, mode);
        log.fromDoc(doc);
        break;
      case RESTORE_FINISHED:
        log = new ORestoreFinishedLog(unitId, txId, uuid, dbName, mode);
        log.fromDoc(doc);
        break;
      case RESTORE_STARTED:
        log = new ORestoreStartedLog(unitId, txId, uuid, dbName, mode);
        log.fromDoc(doc);
        break;
      case UPLOAD_ERROR:
        log = new OBackupUploadErrorLog(unitId, txId, uuid, dbName, mode);
        log.fromDoc(doc);
        break;
      case UPLOAD_STARTED:
        log = new OBackupUploadStartedLog(unitId, txId, uuid, dbName, mode);
        log.fromDoc(doc);
        break;
      case UPLOAD_FINISHED:
        log = new OBackupUploadFinishedLog(unitId, txId, uuid, dbName, mode);
        log.fromDoc(doc);
        break;
      default:
        throw new IllegalStateException("Cannot deserialize passed in log record.");
    }
    return log;
  }
}
