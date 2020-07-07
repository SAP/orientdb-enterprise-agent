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

import com.orientechnologies.enterprise.server.OEnterpriseServer;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Created by Enrico Risa on 25/03/16. */
public interface OBackupLogger {

  OBackupLog log(OBackupLog log);

  long nextOpId();

  OBackupLog findLast(OBackupLogType op, String uuid) throws IOException;

  OBackupLog findLast(OBackupLogType op, String uuid, Long unitId) throws IOException;

  List<OBackupLog> findByUUID(String uuid, int page, int pageSize, Map<String, String> params)
      throws IOException;

  List<OBackupLog> findByUUIDAndUnitId(
      String uuid, Long unitId, int page, int pageSize, Map<String, String> params)
      throws IOException;

  void deleteByUUIDAndUnitIdAndTx(String uuid, Long unitId, Long tx) throws IOException;

  void deleteByUUIDAndTimestamp(String uuid, Long timestamp) throws IOException;

  List<OBackupLog> findAllLatestByUUID(String uuid, int page, int pageSize) throws IOException;

  void deleteLog(OBackupLog scheduled);

  void updateLog(OBackupLog log);

  OEnterpriseServer getServer();
}
