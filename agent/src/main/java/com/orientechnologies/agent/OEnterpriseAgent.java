/*
 * Copyright 2010-2013 Orient Technologies LTD (info--at--orientechnologies.com)
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
 * 
 * For more information: http://www.orientechnologies.com
 */
package com.orientechnologies.agent;

import com.orientechnologies.agent.http.command.OServerCommandGetLog;
import com.orientechnologies.agent.http.command.OServerCommandGetProfiler;
import com.orientechnologies.orient.server.OServer;
import com.orientechnologies.orient.server.config.OServerParameterConfiguration;
import com.orientechnologies.orient.server.network.OServerNetworkListener;
import com.orientechnologies.orient.server.network.protocol.http.ONetworkProtocolHttpAbstract;
import com.orientechnologies.orient.server.plugin.OServerPluginAbstract;

public class OEnterpriseAgent extends OServerPluginAbstract {
	private OServer server;

	public OEnterpriseAgent() {
	}

	@Override
	public void config(OServer oServer, OServerParameterConfiguration[] iParams) {
		server = oServer;
	}

	@Override
	public String getName() {
		return "enterprise-agent";
	}

	@Override
	public void startup() {
		installCommands();
	}

	@Override
	public void shutdown() {
		uninstallCommands();
	}

	private void installCommands() {
		final OServerNetworkListener listener = server
				.getListenerByProtocol(ONetworkProtocolHttpAbstract.class);
		listener.registerStatelessCommand(new OServerCommandGetProfiler());
		listener.registerStatelessCommand(new OServerCommandGetLog());

	}

	private void uninstallCommands() {
		final OServerNetworkListener listener = server
				.getListenerByProtocol(ONetworkProtocolHttpAbstract.class);
		listener.unregisterStatelessCommand(OServerCommandGetProfiler.class);
		listener.unregisterStatelessCommand(OServerCommandGetLog.class);
	}
}