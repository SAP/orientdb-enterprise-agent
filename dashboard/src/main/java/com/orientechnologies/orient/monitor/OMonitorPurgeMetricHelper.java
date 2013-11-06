/*
 * Copyright 2010-2013 Orient Technologies LTD
 * All Rights Reserved. Commercial License.
 *
 * NOTICE:  All information contained herein is, and remains the property of
 * Orient Technologies LTD and its suppliers, if any.  The intellectual and
 * technical concepts contained herein are proprietary to
 * Orient Technologies LTD and its suppliers and may be covered by United
 * Kingdom and Foreign Patents, patents in process, and are protected by trade
 * secret or copyright law.
 * 
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Orient Technologies LTD.
 */
package com.orientechnologies.orient.monitor;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.record.ORecordSchemaAware;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLQuery;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;

public final class OMonitorPurgeMetricHelper {

	public static void delete(Integer hour, ODatabaseDocumentTx db) {
		if (hour != 0) {

			String osql = "select from Metric where snapshot.dateFrom <= :dateFrom ";
			final Map<String, Object> params = new HashMap<String, Object>();

			OSQLQuery<ORecordSchemaAware<?>> osqlQuery = new OSQLSynchQuery<ORecordSchemaAware<?>>(
					osql);

			Calendar calendar = Calendar.getInstance();
			calendar.add(Calendar.HOUR, -hour);
			params.put("dateFrom", calendar.getTime());

			List<ODocument> metrics = db.query(osqlQuery, params);

			ODocument snapshot = null;
			if (metrics != null && !metrics.isEmpty()) {
				snapshot = metrics.get(0).field("snapshot");
			}

			for (ODocument doc : metrics) {
				try {
					ODocument snapshot2compare = doc.field("snapshot");
					if (snapshot != snapshot2compare) {
						snapshot.delete();
						snapshot = snapshot2compare;
					}
					doc.delete();
				} catch (Exception e) {
					e.printStackTrace();
				}

			}
			snapshot.delete();
		}
	}
}