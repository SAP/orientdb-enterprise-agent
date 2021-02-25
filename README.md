OrientDB Enterprise Edition
===================

Fortify (develop) [![Fortify](https://gkesaporientdb.jaas-gcp.cloud.sap.corp/job/3.1.x/job/orientdb-enterprise-fortify-3.1.x/badge/icon)](https://gkesaporientdb.jaas-gcp.cloud.sap.corp/job/3.1.x/job/orientdb-enterprise-fortify-3.1.x/) |
Whitesource (develop) [![Whitesource](https://gkesaporientdb.jaas-gcp.cloud.sap.corp/job/3.0.x/job/orientdb-enterprise-whitesource-3.1.x/badge/icon)](https://gkesaporientdb.jaas-gcp.cloud.sap.corp/job/3.0.x/job/orientdb-enterprise-whitesource-3.1.x/) |
PPMS (develop) [![PPMS](https://gkesaporientdb.jaas-gcp.cloud.sap.corp/job/3.0.x/job/orientdb-enterprise-whitesource-ppms2-3.1.x//badge/icon)](https://gkesaporientdb.jaas-gcp.cloud.sap.corp/job/3.0.x/job/orientdb-enterprise-whitesource-ppms2-3.1.x/)

### ODB 3.0 ADB release

Current release process for ODB EE `v3.0.x` is as follows.

Create ADB request for final assembly: https://adbweb.wdf.sap.corp/  >  Patch Assembly >  Create ADB request. Use note https://launchpad.support.sap.com/#/notes/2875730 and copy text into input field next to the number, leave `SP Level` = 00.

Note: if you do not have permissions to create the request, ask NAAS (Tobias Schreck).

Put JAR file into folder: \\\\mediaserver.wdf.sap.corp\UPLINBOX\ORIENTDB_30_EE and inform NAAS (Tobias Schreck).

Note for 3.1: https://launchpad.support.sap.com/#/notes/2915917

### ODB 3.1 CWB process

When creating a pull request in https://github.wdf.sap.corp/final-assembly/orientdb-enterprise (e.g. 3.1.x -> fa/rel-3.1), a `CR-Id: unique technical ID` is required. This can be created following the `New CR` link in the PR.
Note that when fixing the build, select `No Bug Report` as `Type` and check `Not Patch Relevant`!
In `Application Component` set our BCP component `BC-DB-ODB`.

Then `Next` and select the correct branch e.g. `fa/rel-3.1` and patch level e.g. `000`.

Copy the `ID` of the correction request to the initial comment in your PR e.g. `CR-Id: 002075125900001706292020`. After that the CWB voter will vote successfully.

> Put CWB CR on "patch request", only after the "stage, promote" was successful.

> run `stage` with `fa/rel-3.1` in https://prod-build10300.wdf.sap.corp/job/final-assembly/job/final-assembly-orientdb-enterprise-SP-REL-common_directshipment/
