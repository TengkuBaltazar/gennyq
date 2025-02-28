package life.genny.qwandaq.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TimeZone;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import org.jboss.logging.Logger;

import io.vertx.mutiny.sqlclient.Tuple;
import life.genny.qwandaq.Answer;
import life.genny.qwandaq.attribute.Attribute;
import life.genny.qwandaq.datatype.DataType;
import life.genny.qwandaq.entity.BaseEntity;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TimeZone;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import life.genny.qwandaq.exception.runtime.ItemNotFoundException;

/**
 * A static utility class used for standard requests and
 * operations involving Keycloak.
 * 
 * @author Adam Crow
 */

public class GithubUtils {

    static final Logger log = Logger.getLogger(GithubUtils.class);
    static Jsonb jsonb = JsonbBuilder.create();

    public static final String GIT_PROP_EXTENSION = "-git.properties";

	public static DataType dtt = new DataType(String.class);

    public String gitGet(final String branch, final String project, final String repositoryName,
            final String layoutFilename) throws IOException, GitAPIException {
        String retJson = "";

        final Git git = Git.cloneRepository().setURI("https://github.com/" + project + "/" + repositoryName + ".git")
                .setDirectory(new File(".")).setBranchesToClone(Arrays.asList("refs/heads/" + branch))
                .setBranch("refs/heads/" + branch).call();

        try (Repository repository = git.getRepository()) {
            // find the HEAD
            final ObjectId lastCommitId = repository.resolve(Constants.HEAD);

            // a RevWalk allows to walk over commits based on some filtering that is defined
            try (RevWalk revWalk = new RevWalk(repository)) {
                final RevCommit commit = revWalk.parseCommit(lastCommitId);
                // and using commit's tree find the path
                final RevTree tree = commit.getTree();
                log.info("Having tree: " + tree);

                // now try to find a specific file
                try (TreeWalk treeWalk = new TreeWalk(repository)) {
                    treeWalk.addTree(tree);
                    treeWalk.setRecursive(true);
                    treeWalk.setFilter(PathFilter.create(layoutFilename));
                    if (!treeWalk.next()) {
                        throw new IllegalStateException("Did not find expected file '" + layoutFilename + "'");
                    }

                    final ObjectId objectId = treeWalk.getObjectId(0);
                    final ObjectLoader loader = repository.open(objectId);

                    // and then one can the loader to read the file
                    retJson = new String(loader.getBytes());
                    loader.copyTo(System.out);
                }

                revWalk.dispose();
            }
        }
        return retJson;
    }

    public String getGitVersionString(String projectDependencies) throws IOException {
        Properties gitProperties;
        JsonObjectBuilder response = Json.createObjectBuilder();
        JsonArrayBuilder versionArray = Json.createArrayBuilder();
        Map<String, Object> versionMap;
        StringTokenizer st = new StringTokenizer(projectDependencies, ",");
        while (st.hasMoreElements()) {
            String projectName = st.nextToken();
            String gitPropertiesFileName = projectName + GIT_PROP_EXTENSION;
            URL gitPropertiesURL = Thread.currentThread().getContextClassLoader().getResource(gitPropertiesFileName);
            if (gitPropertiesURL != null && gitPropertiesURL.getFile() != "") {
                gitProperties = new Properties();
                gitProperties.load(gitPropertiesURL.openStream());
                for (String key : gitProperties.stringPropertyNames()) {
                    String value = gitProperties.getProperty(key);
                    JsonObject versionObj = Json.createObjectBuilder().add(key, value).build();
                    versionArray.add(versionObj);
                }
            }
            response.add("version",
                    versionArray.build());

        }
        return jsonb.toJson(response.build());
    }

    public List<BaseEntity> getLayoutBaseEntitys(BaseEntityUtils beUtils, final String remoteUrl, final String branch,
            final String realm,
            final String gitrealm, boolean recursive)
            throws InvalidRemoteException, TransportException, GitAPIException,
            RevisionSyntaxException, AmbiguousObjectException, IncorrectObjectTypeException, IOException {

        List<BaseEntity> layouts = new ArrayList<>();

        Map<String, String> lays = new HashMap<>();

        String gitFolder = gitrealm;
        String realmFilter = gitFolder;

        log.info("remoteUrl=" + remoteUrl);
        log.info("branch=" + branch);
        log.info("gitrealm=" + gitrealm);
        log.info("realm=" + realm);
        log.info("gitFolder=" + gitFolder);
        log.info("realmFilter=" + realmFilter);

        String tmpDir = "/tmp/git";
        try {
            File directory = new File(tmpDir);

            // Deletes a directory recursively. When deletion process is fail an
            // IOException is thrown and that's why we catch the exception.
            FileUtils.deleteDirectory(directory);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Git git = Git.cloneRepository()

                .setURI(remoteUrl).setDirectory(new File(tmpDir)).setBranch(branch).call();

        log.info("Set up Git");

        git.fetch().setRemote(remoteUrl).setRefSpecs(new RefSpec("+refs/heads/*:refs/heads/*")).call();

        Repository repo = git.getRepository();

        /*
         * DfsRepositoryDescription repoDesc = new DfsRepositoryDescription();
         * InMemoryRepository repo = new InMemoryRepository(repoDesc); Git git = new
         * Git(repo); git.fetch() .setRemote(remoteUrl) .setRefSpecs(new
         * RefSpec("+refs/heads/*:refs/heads/*")) .call(); repo.getObjectDatabase();
         */
        // Ref head = repo.getRef("HEAD");
        ObjectId lastCommitId = repo.resolve("refs/heads/" + branch);

        // a RevWalk allows to walk over commits based on some filtering that is defined
        RevWalk walk = new RevWalk(repo);

        RevCommit commit = walk.parseCommit(lastCommitId);
        RevTree tree = commit.getTree();
        log.info("Having tree: " + tree);

        // now use a TreeWalk to iterate over all files in the Tree recursively
        // you can set Filters to narrow down the results if needed
        TreeWalk treeWalk = new TreeWalk(repo);
        treeWalk.addTree(tree);
        treeWalk.setRecursive(true);
        // treeWalk.setFilter(AndTreeFilter.create(TreeFilter.ANY_DIFF,
        // PathFilter.ANY_DIFF));

        treeWalk.setFilter(AndTreeFilter.create(PathFilter.create(realmFilter), PathSuffixFilter.create(".html")));
        while (treeWalk.next()) {

            final ObjectId objectId = treeWalk.getObjectId(0);
            final ObjectLoader loader = repo.open(objectId);
            FileMode fileMode = treeWalk.getFileMode(0);
            // and then one can the loader to read the file

            String layoutCode = "";
            String name = "";
            String uri = "";
            String fullpath = "";

            fullpath = treeWalk.getPathString(); // .substring(realmFilter.length()+1); // get rid of
                                                 // realm+"-new/sublayouts/"

            if (fullpath.equals("internmatch-new/sublayouts/home/agent/bucket/index.json")) {
                log.info("hello");
                // continue;
            }

            // only allow genny/<filename> or genny/sublayouts

            if (((!recursive) && (StringUtils.countMatches(fullpath, "/") == 1)) || (recursive)) {

                Path p = Paths.get(fullpath);

                if (p.getParent() != null) {
                    uri = ("genny".equals(gitrealm) ? "/" : "") + p.getParent().toString();
                }
                name = p.getFileName().toString().replaceFirst("[.][^.]+$", "");
                String nameCode = name.replaceAll("\\-", "");

                if (!name.equals(gitrealm)) { // avoid root folder
                    String content = new String(loader.getBytes());

                    if ("genny".equals(gitrealm)) {
                        uri = uri + name;
                    } else {
                        uri = fullpath.replaceFirst("[.][^.]+$", "");
                        uri = uri.substring(gitrealm.length() + 1);
                        if (uri.startsWith("sublayouts")) {
                            uri = uri.substring("sublayouts/".length());
                        }
                    }
                    uri = StringUtils.removeEndIgnoreCase(uri, "index");
                    if (StringUtils.endsWith(uri, "bucket/")) {
                        uri = StringUtils.removeEndIgnoreCase(uri, "/");
                    }

                    String precode = String.valueOf(uri.replaceAll("[^a-zA-Z0-9\\-]", "").toUpperCase().hashCode());
                    layoutCode = ("DOT_" + nameCode).toUpperCase();

                    String existingUrl = lays.get(layoutCode);
                    BaseEntity layout = null;
                    try {
                        layout = beUtils.getBaseEntityOrNull(realm, layoutCode);
                    } catch (ItemNotFoundException e) {
                        log.info("No be found for " + layoutCode);
                    }

                    if ((existingUrl != null) || (layout != null)) {
                        log.info("DUPLICATE - " + layoutCode + ":" + existingUrl + "--->" + fullpath);

                    } else {
                        lays.put(layoutCode, fullpath);
                        layout = new BaseEntity(layoutCode, name);
                    }

                    layout.addAnswer(new Answer(layout, layout, new Attribute("PRI_LAYOUT_DATA", "Layout Data", dtt),
                            content));

                    layout.addAnswer(
                            new Answer(layout, layout, new Attribute("PRI_LAYOUT_URI", "Layout URI", dtt), uri));
                    layout.addAnswer(new Answer(layout, layout, new Attribute("PRI_LAYOUT_URL", "Layout URL", dtt),
                            "http://layout-cache-service/" + fullpath));
                    layout.addAnswer(
                            new Answer(layout, layout, new Attribute("PRI_LAYOUT_NAME", "Layout Name", dtt), name));
                    layout.addAnswer(
                            new Answer(layout, layout, new Attribute("PRI_BRANCH", "Branch", dtt), branch));
                    long secs = commit.getCommitTime();
                    LocalDateTime commitDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(secs * 1000),
                            TimeZone.getDefault().toZoneId());

                    String lastCommitDateTimeString = commitDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    layout.addAnswer(new Answer(layout, layout,
                            new Attribute("PRI_LAYOUT_MODIFIED_DATE", "Modified", dtt), lastCommitDateTimeString)); // if
                                                                                                                   // new
                    layout.setRealm(realm);
                    layout.setUpdated(commitDateTime);
                    layouts.add(layout);
                }
            }

        }

        return layouts;

    }

    public List<BaseEntity> getLayoutBaseEntitys2(final String remoteUrl, final String branch,
            final String realm) throws InvalidRemoteException, TransportException, GitAPIException,
            RevisionSyntaxException, AmbiguousObjectException, IncorrectObjectTypeException, IOException {

        List<BaseEntity> layouts = new ArrayList<BaseEntity>();

        String gitFolder = realm;

        log.info("remoteUrl=" + remoteUrl);
        log.info("branch=" + branch);
        log.info("realm=" + realm);

        String tmpDir = "/tmp/git";
        try {
            File directory = new File(tmpDir);

            // Deletes a directory recursively. When deletion process is fail an
            // IOException is thrown and that's why we catch the exception.
            FileUtils.deleteDirectory(directory);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Process pp = Runtime.getRuntime().exec("cd /tmp;git clone -b "+branch+"
        // "+remoteUrl);

        Git git = Git.cloneRepository()

                .setURI(remoteUrl).setDirectory(new File(tmpDir)).setBranch(branch).call();

        log.info("Set up Git");

        git.fetch().setRemote(remoteUrl).setRefSpecs(new RefSpec("+refs/heads/*:refs/heads/*")).call();

        // git show -s --format=%ct master^{commit}
        // Process pp = Runtime.getRuntime().exec("cd /tmp;git show -s --format=%ct
        // master^{commit}");

        String commitTimeStr = execCmd("git show -s --format=%ct " + branch + "^{commit}");
        long commitTime = Long.parseLong(commitTimeStr);

        String realmFilter = tmpDir + "/" + gitFolder + "/sublayouts";
        List<Tuple> layoutTexts = readFilenamesFromDirectory(realmFilter);

        for (Tuple tupleFile : layoutTexts) {
            String fullpath = tupleFile.getString(0).substring(realmFilter.length() + 1); // get rid of
                                                                                          // realm+"-new/sublayouts/"
            String content = tupleFile.getString(1);
            Path p = Paths.get(fullpath);
            String filepath = p.getParent().toString();
            String name = fullpath.substring(filepath.length() + 1).replaceFirst("[.][^.]+$", "");
            ;
            filepath = filepath + "/" + name;
            String precode = String.valueOf(filepath.replaceAll("[^a-zA-Z0-9]", "").toUpperCase().hashCode());
            String layoutCode = ("LAY_" + realm + "_" + precode).toUpperCase();
            log.info(layoutCode + " file = " + fullpath + " size=" + tupleFile.getString(1).length());
            BaseEntity layout = new BaseEntity(layoutCode, name);
            layout.addAnswer(new Answer(layout, layout, new Attribute("PRI_LAYOUT_DATA", "Layout Data", dtt), content));
            layout.addAnswer(new Answer(layout, layout, new Attribute("PRI_LAYOUT_URI", "Layout URI", dtt), filepath));
            layout.addAnswer(new Answer(layout, layout, new Attribute("PRI_LAYOUT_URL", "Layout URL", dtt),
                    "http://layout-cache-service/" + realmFilter + "/" + fullpath));
            layout.addAnswer(new Answer(layout, layout, new Attribute("PRI_LAYOUT_NAME", "Layout Name", dtt), name));
            long secs = commitTime;
            LocalDateTime commitDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(secs * 1000),
                    TimeZone.getDefault().toZoneId());

            String lastCommitDateTimeString = commitDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            layout.addAnswer(new Answer(layout, layout, new Attribute("PRI_LAYOUT_MODIFIED_DATE", "Modified", dtt),
                    lastCommitDateTimeString)); // if new
            layout.setRealm(realm);
            layout.setUpdated(commitDateTime);
            layouts.add(layout);

        }

        return layouts;

    }

    public List<Tuple> readFilenamesFromDirectory(final String rootFilePath) {
        List<Tuple> ret = new ArrayList<Tuple>();
        final File folder = new File(rootFilePath);
        final File[] listOfFiles = folder.listFiles();

        for (int i = 0; i < listOfFiles.length; i++) {
            if (listOfFiles[i].isFile()) {
                try {
                    String fileText = getFileAsText(listOfFiles[i]);
                    String filename = listOfFiles[i].getAbsolutePath();
                    Tuple fileTuple = Tuple.of(filename, fileText);
                    ret.add(fileTuple);
                } catch (final IOException e) {
                    e.printStackTrace();
                }

            } else if (listOfFiles[i].isDirectory()) {
                // log.info("Directory " + listOfFiles[i].getName());
                List<Tuple> files = readFilenamesFromDirectory(listOfFiles[i].getAbsolutePath());
                ret.addAll(files);
            }
        }
        return ret;
    }

    private String getFileAsText(final File file) throws IOException {
        final BufferedReader in = new BufferedReader(new FileReader(file));
        String ret = "";
        String line = null;
        while ((line = in.readLine()) != null) {
            ret += line;
        }
        in.close();

        return ret;
    }

    public String execCmd(String cmd) throws java.io.IOException {
        java.util.Scanner s = new java.util.Scanner(Runtime.getRuntime().exec(cmd).getInputStream())
                .useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    public List<BaseEntity> getRulesBaseEntitys(String gitUrl, String branch, String realm, String string,
            boolean b) {
        // TODO Auto-generated method stub
        return null;
    }
}
