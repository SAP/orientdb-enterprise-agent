package com.orientechnologies.website.github;

import com.jcabi.http.Response;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.website.exception.ServiceException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by Enrico Risa on 05/11/14.
 */
public class GRepo extends GEntity {

  private static SimpleDateFormat dateFormat;
  static {
    dateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z");
    dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
  }

  protected GRepo(GitHub gitHub, String content) {
    super(gitHub, null, content);
  }

  @Override
  protected String getBaseUrl() {
    return "/repos/" + _local.field("full_name");
  }

  public String getName() {
    return get("name");
  }

  public String getDescription() {
    return get("description");
  }

  public String getFullName() {
    return _local.field("full_name");
  }

  public Iterable<GIssue> getIssues(GIssueState s) throws IOException {

    String page = "1";
    String state = s.toString();
    List<GIssue> issues = new ArrayList<GIssue>();

    while (page != null) {
      page = fillIssue(page, state, issues);
    }
    return issues;
  }

  public Iterable<GIssue> getIssues(List<Integer> issues) throws IOException {

    List<GIssue> iss = new ArrayList<GIssue>();

    for (Integer issue : issues) {
      GIssue issue1 = getIssue(issue);
      iss.add(issue1);
    }
    return iss;
  }

  private GIssue getSingleIssue(Integer i) throws IOException {
    Response response = github.REQUEST.uri().path(getBaseUrl() + "/issues/" + i).back().method("GET")
        .header("Authorization", String.format("token %s", github.token)).fetch();

    byte[] bodies = response.binary();
    String body = new String(bodies, "UTF-8");

    GIssue g = new GIssue(github, this, body);
    return g;
  }

  private String fillIssue(String page, String state, List<GIssue> issues) throws IOException {
    Response response = github.REQUEST.uri().path(getBaseUrl() + "/issues").queryParam("page", page).queryParam("per_page", "100")
        .queryParam("state", state).back().method("GET").header("Authorization", String.format("token %s", github.token)).fetch();

    byte[] bodies = response.binary();
    String body = new String(bodies, "UTF-8");

    Map<String, List<String>> headers = response.headers();

    String next = null;
    if (headers.get("Link") != null) {
      String link = headers.get("Link").get(0);
      for (String token : link.split(", ")) {
        if (token.endsWith("rel=\"next\"")) {
          // found the next page. This should look something like
          // <https://api.github.com/repos?page=3&per_page=100>; rel="next"
          int idx = token.indexOf('>');
          Map<String, String> stringMap = splitQuery(new URL(token.substring(1, idx)));
          next = stringMap.get("page");
        }
      }
    }
    JSONArray array = new JSONArray(body);

    String tmp;
    for (int i = 0; i < array.length(); i++) {
      JSONObject obj = array.getJSONObject(i);
      tmp = obj.toString();
      GIssue g = new GIssue(github, this, tmp);
      if (!g.isPullRequest())
        issues.add(g);
    }

    return next;
  }

  public static Map<String, String> splitQuery(URL url) throws UnsupportedEncodingException {
    Map<String, String> query_pairs = new LinkedHashMap<String, String>();
    String query = url.getQuery();
    String[] pairs = query.split("&");
    for (String pair : pairs) {
      int idx = pair.indexOf("=");
      query_pairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
    }
    return query_pairs;
  }

  public GIssue getIssue(Integer number) throws IOException {
    String content = github.REQUEST.uri().path(getBaseUrl() + "/issues/" + number).back().method("GET")
        .header("Authorization", String.format("token %s", github.token)).fetch().body();
    GIssue g = new GIssue(github, this, content);
    return g;
  }

  public GIssue isChangedIssue(Integer number, Date updatedAt) throws IOException {

    String date = dateFormat.format(updatedAt);
    Response reponse = github.REQUEST.uri().path(getBaseUrl() + "/issues/" + number).back().method("GET")

    .header("Authorization", String.format("token %s", github.token)).header("If-Modified-Since", date).fetch();

    int status = reponse.status();

    if (status == 304) {
      return null;
    }
    GIssue g = new GIssue(github, this, reponse.body());
    return g;
  }

  public GIssue openIssue(String content) throws IOException {
    String res = header(github.REQUEST.uri().path(getBaseUrl() + "/issues").back().body().set(content).back().method("POST"))
        .fetch().body();
    return new GIssue(github, this, res);

  }

  public List<GLabel> changeIssueLabels(Integer number, String content) throws IOException {
    String res = header(
        github.REQUEST.uri().path(getBaseUrl() + "/issues/" + number + "/labels").back().body().set(content).back().method("POST"))
        .fetch().body();
    JSONArray array = new JSONArray(res);
    List<GLabel> issues = new ArrayList<GLabel>();
    String tmp;
    for (int i = 0; i < array.length(); i++) {
      JSONObject obj = array.getJSONObject(i);
      tmp = obj.toString();
      GLabel g = new GLabel(github, this, tmp);
      issues.add(g);
    }
    return issues;

  }

  public void removeIssueLabel(Integer number, String label) throws IOException {
    String res = header(github.REQUEST.uri().path(getBaseUrl() + "/issues/" + number + "/labels/" + label).back().method("DELETE"))
        .fetch().body();

  }

  public GComment commentIssue(Integer number, String content) throws IOException {
    String res = header(
        github.REQUEST.uri().path(getBaseUrl() + "/issues/" + number + "/comments").back().body().set(content).back()
            .method("POST")).fetch().body();
    return new GComment(github, this, res);

  }

  public GComment patchComment(Integer number, Integer commentId, String content) throws IOException {
    String res = github.REQUEST.uri().path(getBaseUrl() + "/issues/comments/" + commentId).back().body().set(content).back()
        .method("PATCH").header("Authorization", String.format("token %s", github.token)).fetch().body();
    return new GComment(github, this, res);

  }

  public GIssue patchIssue(Integer number, String content) throws IOException {
    Response resp = header(
        github.REQUEST.uri().path(getBaseUrl() + "/issues/" + number).back().body().set(content).back().method("PATCH")).fetch();

    if (resp.status() == 200) {
      String res = resp.body();
      return new GIssue(github, this, res);
    } else {
      throw ServiceException.create(resp.status()).withMessage("Cannot patch issue number : %d , with payload %s, got: %s ",
          number, content, resp.body());
    }

  }

  public List<GLabel> getLabels() throws IOException {
    String content = github.REQUEST.uri().path(getBaseUrl() + "/labels").back().method("GET")
        .header("Authorization", String.format("token %s", github.token)).fetch().body();
    JSONArray array = new JSONArray(content);

    List<GLabel> issues = new ArrayList<GLabel>();
    String tmp;
    for (int i = 0; i < array.length(); i++) {
      JSONObject obj = array.getJSONObject(i);
      tmp = obj.toString();
      GLabel g = new GLabel(github, this, tmp);
      issues.add(g);
    }
    return issues;
  }

  public List<GMilestone> getMilestones() throws IOException {
    String content = github.REQUEST.uri().path(getBaseUrl() + "/milestones").back().method("GET")
        .header("Authorization", String.format("token %s", github.token)).fetch().body();
    JSONArray array = new JSONArray(content);

    List<GMilestone> issues = new ArrayList<GMilestone>();
    String tmp;
    for (int i = 0; i < array.length(); i++) {
      JSONObject obj = array.getJSONObject(i);
      tmp = obj.toString();
      ODocument document = new ODocument().fromJSON(tmp, "noMap");
      GMilestone g = new GMilestone(github, this, tmp);
      issues.add(g);
    }
    return issues;
  }

  public void deleteComment(Integer number, Integer commentId, String value) throws IOException {
    String res = github.REQUEST.uri().path(getBaseUrl() + "/issues/comments/" + commentId).back().method("DELETE")
        .header("Authorization", String.format("token %s", github.token)).fetch().body();

  }
}