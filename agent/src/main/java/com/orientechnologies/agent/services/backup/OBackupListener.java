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

package com.orientechnologies.agent.services.backup;

import com.orientechnologies.agent.services.backup.log.OBackupLog;
import com.orientechnologies.orient.core.record.impl.ODocument;

/** Created by Enrico Risa on 25/03/16. */
public interface OBackupListener {

  /**
   * Listener on backup event
   *
   * @param cfg Configuration of the backup
   * @param log Event Logged
   */
  public Boolean onEvent(ODocument cfg, OBackupLog log);
}
