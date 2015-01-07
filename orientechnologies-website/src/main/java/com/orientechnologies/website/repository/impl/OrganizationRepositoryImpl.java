package com.orientechnologies.website.repository.impl;

import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.orientechnologies.website.OrientDBFactory;
import com.orientechnologies.website.hateoas.Page;
import com.orientechnologies.website.helper.SecurityHelper;
import com.orientechnologies.website.model.schema.*;
import com.orientechnologies.website.model.schema.dto.*;
import com.orientechnologies.website.model.schema.dto.OUser;
import com.orientechnologies.website.repository.OrganizationRepository;
import com.orientechnologies.website.services.UserService;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.impls.orient.OrientElement;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;
import com.tinkerpop.blueprints.impls.orient.OrientVertex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class OrganizationRepositoryImpl extends OrientBaseRepository<Organization> implements OrganizationRepository {

    @Autowired
    private UserService userService;
    @Autowired
    private OrientDBFactory dbFactory;

    @Override
    public Organization findOneByName(String name) {

        OrientGraph db = dbFactory.getGraph();
        String query = String.format("select from %s where name = '%s'", getEntityClass().getSimpleName(), name);
        Iterable<OrientVertex> vertices = db.command(new OCommandSQL(query)).execute();

        try {
            ODocument doc = vertices.iterator().next().getRecord();

            return fromDoc(doc);
        } catch (NoSuchElementException e) {
            return null;
        }

    }

    @Override
    public OUser findOwnerByName(String name) {
        OrientGraph db = dbFactory.getGraph();
        String query = String.format("select expand(out('HasOwner')) from Organization where name = '%s'", name);
        Iterable<OrientVertex> vertices = db.command(new OCommandSQL(query)).execute();
        try {
            ODocument doc = vertices.iterator().next().getRecord();
            return com.orientechnologies.website.model.schema.OUser.NAME.fromDoc(doc, db);
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    @Override
    public List<Issue> findOrganizationIssues(String name, String q, String page, String perPage) {
        OrientGraph db = dbFactory.getGraph();

        String query = String
                .format(
                        "select *,$priority from (select expand(out('HasRepo').out('HasIssue')) from Organization where name = '%s') let $priority = out('HasPriority')[0].number ",
                        name);
        query = addParams(name, q, query);
        Integer limit = new Integer(perPage);
        Integer skip = limit * (new Integer(page) - 1);
        query += " SKIP " + skip + " LIMIT " + limit;
        Iterable<OrientVertex> vertices = db.command(new OCommandSQL(query)).execute();

        List<Issue> issues = new ArrayList<Issue>();
        for (OrientVertex vertice : vertices) {
            ODocument doc = vertice.getRecord();
            issues.add(OIssue.NUMBER.fromDoc(doc, db));
        }
        return issues;
    }

    public long countOrganizationIssues(String name, String q, String page, String perPage) {
        OrientGraph db = dbFactory.getGraph();
        String query = String.format(
                "select count(*) from (select expand(out('HasRepo').out('HasIssue')) from Organization where name = '%s') ", name);
        query = addParams(name, q, query);
        Iterable<OrientElement> documents = db.command(new OCommandSQL(query)).execute();
        return documents.iterator().next().getRecord().field("count");

    }

    private String addParams(String orgName, String q, String query) {
        int idx = 0;
        String fullText = "";
        String sort = "";
        if (q != null && !q.isEmpty()) {
            String[] queries = q.split(" (?=(([^'\"]*['\"]){2})*[^'\"]*$)");
            for (String s : queries) {
                String[] values = s.split(":");
                if (values.length == 1) {
                    if (values[0].isEmpty())
                        continue;
                    fullText += " " + values[0];
                } else {
                    if (values[0].equalsIgnoreCase("sort")) {
                        sort = applySort(values[1].replace("\"", ""));
                    } else {
                        query = applyParam(query, values[0], values[1].replace("\"", ""), idx++);

                    }
                }
            }
        }
        if (!fullText.isEmpty())
            query = applyParam(query, "title", fullText, idx++);

        query = addProfilation(orgName, query, idx++);
        if (!sort.isEmpty())
            query += " " + sort;
        else {
            query += " order by createdAt desc";
        }
        return query;
    }

    private String applySort(String field) {
        String[] values = field.split("-");
        if ("priority".equalsIgnoreCase(values[0])) {
            return "order by $priority " + values[1];
        }
        return "";
    }

    private String addProfilation(String orgName, String query, int idx) {
        OUser user = SecurityHelper.currentUser();

        boolean isMember = userService.isMember(user, orgName);
        if (isMember) {
            return query;
        }
        Client client = userService.getClient(user, orgName);
        if (client != null) {
            return query + (idx > 0 ? " and " : " where ")
                    + "  (confidential <> true or (in('HasClient')[@class = 'Client'].clientId IN "
                    + client.getClientId() + "))";
        }
        return query + (idx > 0 ? " and " : " where ") + " confidential <> true";

    }

    @Override
    public Page<Issue> findOrganizationIssuesPagedProfiled(String name, String q, String page, String perPage) {

        List<Issue> issues = findOrganizationIssues(name, q, page, perPage);
        long count = countOrganizationIssues(name, q, page, perPage);
        long p = new Long(page);
        long pP = new Long(perPage);
        return new Page<Issue>(p, pP, count, issues);
    }

    private String applyParam(String incominQuery, String name, String value, int idx) {

        // LITTLE UGLY BUT WORKS :D

        // query += (idx > 0 ? " and " : " where ");
        Object val = null;

        String parsed = parseParam(name, value);
        if (!parsed.isEmpty()) {
            incominQuery = incominQuery + (idx > 0 ? " and " : " where ") + parsed;
        }
        return incominQuery;
    }

    private String parseParam(String name, String value) {
        Object val = null;
        String query = "";
        if ("is".equals(name)) {
            val = value.toUpperCase();
            query = query + " state = '%s'";
        }
        if ("label".equals(name)) {
            val = value.replace("\"", "");
            query = query + " out('HasLabel').name CONTAINS '%s'";
        }
        if ("milestone".equals(name)) {
            val = value;
            query = query + " out('HasMilestone').title CONTAINS '%s'";
        }
        if ("version".equals(name)) {
            val = value;
            query = query + " out('HasVersion').title CONTAINS '%s'";
        }
        if ("author".equals(name)) {
            val = value;
            query = query + " in('HasOpened').name CONTAINS '%s'";
        }
        if ("assignee".equals(name)) {
            val = value;
            query = query + " out('IsAssigned').name CONTAINS '%s'";
        }
        if ("area".equals(name)) {
            val = value;
            query = query + " out('HasScope').name CONTAINS '%s'";
        }
        if ("priority".equals(name)) {
            val = value;
            query = query + " out('HasPriority').name CONTAINS '%s'";
        }
        if ("title".equals(name)) {
            val = value.toLowerCase().trim();
            query = query + "title.toLowerCase() containsText '%s'";
        }
        if ("client".equals(name)) {
            val = value;
            query = query + "in('HasClient')[@class = 'Client'].name  IN '%s'";
        }
        if ("no".equals(name)) {
            if ("label".equals(value)) {
                query = query + " out('HasLabel').size() = 0";
            }
            if ("milestone".equals(value)) {
                query = query + " out('HasMilestone').size() = 0";
            }
            if ("assignee".equals(value)) {
                query = query + " out('IsAssigned').size() = 0";
            }
            if ("version".equals(value)) {
                query = query + " out('HasVersion').size() = 0";
            }
        }
        return val != null ? String.format(query, val) : query;
    }

    @Override
    public List<com.orientechnologies.website.model.schema.dto.Repository> findOrganizationRepositories(String name) {
        OrientGraph db = dbFactory.getGraph();
        String query = String.format("select expand(out('HasRepo')) from Organization where name = '%s'", name);
        Iterable<OrientVertex> vertices = db.command(new OCommandSQL(query)).execute();

        List<com.orientechnologies.website.model.schema.dto.Repository> repositories = new ArrayList<com.orientechnologies.website.model.schema.dto.Repository>();
        for (OrientVertex vertice : vertices) {
            ODocument doc = vertice.getRecord();
            repositories.add(ORepository.NAME.fromDoc(doc, db));
        }
        return repositories;
    }

    @Override
    public List<Client> findClients(String name) {
        OrientGraph db = dbFactory.getGraph();
        String query = String.format("select expand(out('HasClient')) from Organization where name = '%s'", name);
        Iterable<OrientVertex> vertices = db.command(new OCommandSQL(query)).execute();

        List<Client> clients = new ArrayList<Client>();
        for (OrientVertex vertice : vertices) {
            ODocument doc = vertice.getRecord();
            clients.add(OClient.NAME.fromDoc(doc, db));
        }
        return clients;
    }

    @Override
    public List<Priority> findPriorities(String name) {
        OrientGraph db = dbFactory.getGraph();
        String query = String.format("select expand(out('HasPriority')) from Organization where name = '%s'", name);
        Iterable<OrientVertex> vertices = db.command(new OCommandSQL(query)).execute();

        List<Priority> priorities = new ArrayList<Priority>();
        for (OrientVertex vertice : vertices) {
            ODocument doc = vertice.getRecord();
            priorities.add(OPriority.NAME.fromDoc(doc, db));
        }
        return priorities;
    }

    @Override
    public Priority findPriorityByNumber(String name, Integer number) {
        OrientGraph db = dbFactory.getGraph();
        String query = String
                .format("select expand(out('HasPriority')[number = %d]) from Organization where name = '%s'", number, name);
        Iterable<OrientVertex> vertices = db.command(new OCommandSQL(query)).execute();

        try {
            ODocument doc = vertices.iterator().next().getRecord();
            return OPriority.NAME.fromDoc(doc, db);
        } catch (NoSuchElementException e) {
            return null;
        }

    }

    @Override
    public List<Scope> findScopes(String name) {
        OrientGraph db = dbFactory.getGraph();
        String query = String.format("select expand(out('HasRepo').out('HasScope')) from Organization where name = '%s'", name);
        Iterable<OrientVertex> vertices = db.command(new OCommandSQL(query)).execute();

        List<Scope> priorities = new ArrayList<Scope>();
        for (OrientVertex vertice : vertices) {
            ODocument doc = vertice.getRecord();
            priorities.add(OScope.NAME.fromDoc(doc, db));
        }
        return priorities;
    }

    @Override
    public Client findClient(String name, Integer clientId) {
        OrientGraph db = dbFactory.getGraph();
        String query = String.format("select expand(out('HasClient')[clientId = %d]) from Organization where name = '%s'", clientId,
                name);
        Iterable<OrientVertex> vertices = db.command(new OCommandSQL(query)).execute();

        try {
            ODocument doc = vertices.iterator().next().getRecord();
            return OClient.NAME.fromDoc(doc, db);
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    @Override
    public com.orientechnologies.website.model.schema.dto.Repository findOrganizationRepository(String name, String repo) {
        OrientGraph db = dbFactory.getGraph();
        String query = String.format("select expand(out('HasRepo')[name = '%s']) from Organization where name = '%s'", repo, name);
        Iterable<OrientVertex> vertices = db.command(new OCommandSQL(query)).execute();

        try {
            ODocument doc = vertices.iterator().next().getRecord();
            return ORepository.NAME.fromDoc(doc, db);
        } catch (NoSuchElementException e) {
            return null;
        }

    }

    @Override
    public com.orientechnologies.website.model.schema.dto.Repository findOrganizationRepositoryByScope(String name, Integer scope) {
        OrientGraph db = dbFactory.getGraph();
        String query = String.format(
                "select from (select expand(out('HasRepo')) from Organization where name = '%s') where out('HasScope').number CONTAINS %d",
                name, scope);
        Iterable<OrientVertex> vertices = db.command(new OCommandSQL(query)).execute();

        try {
            ODocument doc = vertices.iterator().next().getRecord();
            return ORepository.NAME.fromDoc(doc, db);
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    @Override
    public Issue findSingleOrganizationIssueByRepoAndNumber(String name, String repo, Long number) {

        OrientGraph db = dbFactory.getGraph();
        String query = String.format(
                "select expand(out('HasRepo')[name = '%s'].out('HasIssue')[iid = %d])  from Organization where name = '%s') ", repo,
                number, name);

        Iterable<OrientVertex> vertices = db.command(new OCommandSQL(query)).execute();
        try {
            ODocument doc = vertices.iterator().next().getRecord();
            return OIssue.NUMBER.fromDoc(doc, db);
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    @Override
    public Issue findSingleOrganizationIssueByNumber(String name, Long number) {

        OrientGraph db = dbFactory.getGraph();
        String query = String.format("select expand(out('HasRepo').out('HasIssue')[iid = %d])  from Organization where name = '%s') ",
                number, name);

        Iterable<OrientVertex> vertices = db.command(new OCommandSQL(query)).execute();
        try {
            ODocument doc = vertices.iterator().next().getRecord();
            return OIssue.NUMBER.fromDoc(doc, db);
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    @Override
    public List<Comment> findSingleOrganizationIssueCommentByRepoAndNumber(String owner, String repo, Long number) {

        OrientGraph db = dbFactory.getGraph();
        Issue issue = findSingleOrganizationIssueByRepoAndNumber(owner, repo, number);

        OrientVertex vertex = db.getVertex(new ORecordId(issue.getId()));

        List<Comment> comments = new ArrayList<Comment>();
        for (Vertex vertex1 : vertex.getVertices(Direction.OUT, HasEvent.class.getSimpleName())) {
            OrientVertex v = (OrientVertex) vertex1;
            comments.add(OComment.COMMENT_ID.fromDoc(v.getRecord(), db));
        }

        return comments;
    }

    @Override
    public List<Event> findEventsByOwnerRepoAndIssueNumber(String owner, String repo, Long number) {
        OrientGraph db = dbFactory.getGraph();
        Issue issue = findSingleOrganizationIssueByRepoAndNumber(owner, repo, number);

        if (issue == null) {
            return null;
        }
        OrientVertex vertex = db.getVertex(new ORecordId(issue.getId()));

        final List<Event> events = new ArrayList<Event>();
        for (Vertex vertex1 : vertex.getVertices(Direction.OUT, HasEvent.class.getSimpleName())) {
            OrientVertex v = (OrientVertex) vertex1;
            events.add(OEvent.CREATED_AT.fromDoc(v.getRecord(), db));
        }
        Collections.sort(events, new Comparator<Event>() {
            @Override
            public int compare(Event o1, Event o2) {
                return o1.getCreatedAt().after(o2.getCreatedAt()) ? 1 : -1;
            }
        });
        return events;
    }

    @Override
    public List<OUser> findClientMembers(String org, Integer clientId) {
        OrientGraph db = dbFactory.getGraph();
        String query = String.format(
                "select expand(out('HasClient')[clientId = %d].out('HasMember')) from Organization where name = '%s'", clientId, org);
        Iterable<OrientVertex> vertices = db.command(new OCommandSQL(query)).execute();

        List<OUser> users = new ArrayList<OUser>();
        for (OrientVertex vertice : vertices) {
            ODocument doc = vertice.getRecord();
            users.add(com.orientechnologies.website.model.schema.OUser.NAME.fromDoc(doc, db));
        }
        return users;
    }

    @Override
    public Environment findClientEnvironmentById(String org, Integer clientId, String env) {
        return null;
    }

    @Override
    public List<Sla> findClientEnvironmentSla(String organizationName, Integer clientId, String env) {
        return null;
    }

    @Override
    public List<Environment> findClientEnvironments(String org, Integer clientId) {
        OrientGraph db = dbFactory.getGraph();
        String query = String.format(
                "select expand(out('HasClient')[clientId = %d].out('HasMember').out('HasEnvironment')) from Organization where name = '%s'", clientId, org);
        Iterable<OrientVertex> vertices = db.command(new OCommandSQL(query)).execute();

        List<Environment> users = new ArrayList<Environment>();
        for (OrientVertex vertice : vertices) {
            ODocument doc = vertice.getRecord();
            users.add(OEnvironment.NAME.fromDoc(doc, db));
        }
        return users;
    }

    @Override
    public List<OUser> findTeamMembers(String owner, String repo) {

        // Todo Change the query when the team concept is introduced to repository
        OrientGraph db = dbFactory.getGraph();
        String query = String.format("select expand(out('HasMember')) from Organization where name = '%s'", owner);
        Iterable<OrientVertex> vertices = db.command(new OCommandSQL(query)).execute();

        List<OUser> users = new ArrayList<OUser>();
        for (OrientVertex vertice : vertices) {
            ODocument doc = vertice.getRecord();
            users.add(com.orientechnologies.website.model.schema.OUser.NAME.fromDoc(doc, db));
        }
        return users;
    }

    @Override
    public Milestone findMilestoneByOwnerRepoAndNumberIssueAndNumberMilestone(String owner, String repo, Integer iNumber,
                                                                              Integer mNumber) {
        OrientGraph db = dbFactory.getGraph();
        String query = String
                .format(
                        "select expand(out('HasRepo')[name = '%s'].out('HasIssue')[uuid = %d].out('HasMilestone')[number = %d])  from Organization  where name = '%s')",
                        repo, iNumber, mNumber, owner);

        Iterable<OrientVertex> vertices = db.command(new OCommandSQL(query)).execute();
        try {
            ODocument doc = vertices.iterator().next().getRecord();
            return OMilestone.NUMBER.fromDoc(doc, db);
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    @Override
    public List<Milestone> findRepoMilestones(String owner, String repo) {
        OrientGraph db = dbFactory.getGraph();
        String query = String.format(
                "select expand(out('HasRepo')[name = '%s'].out('HasMilestone'))   from Organization  where name = '%s')", repo, owner);

        Iterable<OrientVertex> vertices = db.command(new OCommandSQL(query)).execute();
        List<Milestone> milestones = new ArrayList<Milestone>();
        for (OrientVertex vertice : vertices) {
            ODocument doc = vertice.getRecord();
            milestones.add(OMilestone.NUMBER.fromDoc(doc, db));
        }
        return milestones;
    }

    @Override
    public List<Label> findRepoLabels(String owner, String repo) {
        OrientGraph db = dbFactory.getGraph();
        String query = String.format(
                "select expand(out('HasRepo')[name = '%s'].out('HasLabel'))   from Organization  where name = '%s')", repo, owner);

        Iterable<OrientVertex> vertices = db.command(new OCommandSQL(query)).execute();
        List<Label> labels = new ArrayList<Label>();
        for (OrientVertex vertice : vertices) {
            ODocument doc = vertice.getRecord();
            labels.add(OLabel.NAME.fromDoc(doc, db));
        }
        return labels;
    }

    @Override
    public List<OUser> findMembers(String name) {
        OrientGraph db = dbFactory.getGraph();
        String query = String.format("select expand(set(out('HasRepo').out('HasMember'))) from Organization where name = '%s'", name);
        Iterable<OrientVertex> vertices = db.command(new OCommandSQL(query)).execute();

        List<OUser> users = new ArrayList<OUser>();
        for (OrientVertex vertice : vertices) {
            ODocument doc = vertice.getRecord();
            users.add(com.orientechnologies.website.model.schema.OUser.NAME.fromDoc(doc, db));
        }
        return users;
    }

    @Override
    public List<Milestone> findMilestones(String name) {
        OrientGraph db = dbFactory.getGraph();
        String query = String.format("select distinct(title) as title from (select expand(out('HasRepo').out('HasMilestone')) from Organization where name = '%s')", name);
        Iterable<OrientVertex> vertices = db.command(new OCommandSQL(query)).execute();

        List<Milestone> milestones = new ArrayList<Milestone>();
        for (OrientVertex vertice : vertices) {
            ODocument doc = vertice.getRecord();
            milestones.add(OMilestone.TITLE.fromDoc(doc, db));
        }
        return milestones;
    }

    @Override
    public List<Label> findLabels(String name) {
        OrientGraph db = dbFactory.getGraph();
        String query = String.format("select name,color from (select expand(out('HasRepo').out('HasLabel')) from Organization where name = '%s')  group by name,color", name);
        Iterable<OrientVertex> vertices = db.command(new OCommandSQL(query)).execute();

        List<Label> labels = new ArrayList<Label>();
        for (OrientVertex vertice : vertices) {
            ODocument doc = vertice.getRecord();
            labels.add(OLabel.NAME.fromDoc(doc, db));
        }
        return labels;
    }

    @Override
    public Organization save(Organization entity) {

        OrientGraph db = dbFactory.getGraph();
        ODocument doc = db.getRawGraph().save(toDoc(entity));
        return fromDoc(doc);
    }

    @Override
    public void save(Collection<Organization> entities) {

    }

    @Override
    public OTypeHolder<Organization> getHolder() {
        return OOrganization.NAME;
    }

    @Override
    public Class<Organization> getEntityClass() {
        return Organization.class;
    }

}