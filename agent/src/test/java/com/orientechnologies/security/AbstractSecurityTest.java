package com.orientechnologies.security;

import com.orientechnologies.common.io.OFileUtils;
import java.io.*;

/**
 * @author sdipro
 * @since 15/06/16
 */
public abstract class AbstractSecurityTest {

  protected static final String SERVER_DIRECTORY = "./target"; // /security_tests";
  protected static final String ROOT_PASSWORD =
      "D2AFD02F20640EC8B7A5140F34FCA49D2289DB1F0D0598BB9DE8AAA75A0792F3";
  protected static String prevOrientHome;
  //  private static OServer server;

  public static void setup(final String dbName) throws Exception {
    prevOrientHome = System.setProperty("ORIENTDB_HOME", SERVER_DIRECTORY);

    // This is a "just in case".
    OFileUtils.deleteRecursively(new File(SERVER_DIRECTORY + "/databases/" + dbName));

    //  	 createDirectory(SERVER_DIRECTORY);
    createDirectory(SERVER_DIRECTORY + "/config");
    createDirectory(SERVER_DIRECTORY + "/databases");
  }

  public static void cleanup(final String dbName) {
    if (prevOrientHome != null) System.setProperty("ORIENTDB_HOME", prevOrientHome);

    OFileUtils.deleteRecursively(new File(SERVER_DIRECTORY + "/databases/" + dbName));

    try {
      OFileUtils.delete(new File(SERVER_DIRECTORY + "/config/security.json"));
    } catch (IOException e) {

    }
  }

  protected static void createDirectory(final String path) {
    try {
      File f = new File(path);
      if (!f.exists()) {
        f.mkdir();
      }
    } catch (Exception ex) {
    }
  }

  protected static void createFile(final String path, final InputStream is) {
    try {
      byte[] buffer = new byte[is.available()];
      is.read(buffer);

      OutputStream fos = new FileOutputStream(path);
      fos.write(buffer);
      fos.flush();
      fos.close();
    } catch (IOException ex) {
    }
  }

  protected boolean fileExists(final String path) {
    return new File(path).exists();
  }

  protected static String shellCommand(final String command) {
    String response = "";

    try {
      String[] cmd = new String[3];
      cmd[0] = "/bin/bash";
      cmd[1] = "-c";
      cmd[2] = command;

      Process p = Runtime.getRuntime().exec(cmd);
      p.waitFor();

      StringBuilder sb = new StringBuilder();

      BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

      String line = "";
      while ((line = reader.readLine()) != null) {
        sb.append(line);
      }

      response = sb.toString();

    } catch (Exception ex) {
      System.out.println("shellCommand Exception: " + ex.getMessage());
    }

    return response;
  }
}
