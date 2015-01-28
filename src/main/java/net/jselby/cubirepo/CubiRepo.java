package net.jselby.cubirepo;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.gson.Gson;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Request;
import spark.Route;

import javax.servlet.MultipartConfigElement;
import javax.servlet.http.Part;
import java.io.*;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static spark.Spark.*;

/**
 * CubiRepo represents the main entrypoint for the standalone software.
 *
 * This sets up a webserver at :3645.
 *
 * @author j_selby
 */
public class CubiRepo {

    @Parameter(names = "--help", description = "Sets the port", help = true)
    private boolean showHelp;

    @Parameter(names = "--port", description = "Sets the port")
    private int port = 3645;

    @Parameter(names = "--password", description = "Sets the password")
    private String password = "12345";

    private List<Resource> resources = null;
    private Gson gson = new Gson();

    public void start() {
        load();
        port(port);

        // Class file mapping
        get("/:author/:name/*", (req, res) -> {
            if (req.pathInfo().startsWith("/delete/") || req.pathInfo().startsWith("/upload/")) {
                return null;
            }

            String path = req.pathInfo();

            String version = path.substring(path.lastIndexOf("/") + 1).toLowerCase();
            String extension = null;
            if (version.contains(".")) {
                extension = version.substring(version.lastIndexOf(".") + 1);
                version = version.substring(0, version.lastIndexOf("."));
            }

            String author = req.params("author").toLowerCase();
            String name = req.params("name").toLowerCase();

            // The version currently contains the name. Split that out, or error.
            // Extension must not be null as well
            if (!version.startsWith(name + "_") || extension == null) {
                halt(404, "File not found. (get)");
                return null;
            }
            version = version.substring((name + "_").length());

            // Find a resource that matches this description
            boolean categoryExists = false;
            Resource resource = null;

            for (Resource checkResource : resources) {
                if (checkResource.getName().equalsIgnoreCase(name)
                        && checkResource.getAuthor().equalsIgnoreCase(author)) {
                    if (checkResource.getVersion().equalsIgnoreCase(version)) {
                        resource = checkResource;
                        break;
                    } else {
                        categoryExists = true;
                    }
                }
            }

            if (resource == null) {
                if (categoryExists) {
                    halt(404, "Version not found, but resource exists in other variants.");
                } else {
                    halt(404, "Resource not found, and no variants detected.");
                }
                return null;
            }

            if (extension.equalsIgnoreCase("json")) {
                // Return a JSON representation of this file
                res.type("application/json");
                return gson.toJson(resource);
            } else if (resource.getType().equalsIgnoreCase(extension)) {
                // Return the resource itself
                res.type("application/octet-stream");
                BufferedInputStream in =
                        new BufferedInputStream(new ByteArrayInputStream(resource.getContents()));
                IOUtils.copy(in, res.raw().getOutputStream());
                in.close();
                halt(200);
            }

            halt(404, "File not found.");
            return null;
        });

        // User frontend for managing
        get("/manage/", (req, res) -> {
            // Make sure they are logged in
            if (req.session() == null || req.session().attribute("loggedIn") == null) {
                halt(403, "Not logged in.<script>document.location=\"/login/\";</script>");
            }

            String elements = "";
            for (Resource resource : resources) {
                elements += "<tr>" +
                        "<td>" + resource.getName() + "</td>" +
                        "<td>" + resource.getAuthor() + "</td>" +
                        "<td>" + resource.getVersion() + "</td>" +
                        "<td>" +
                        "<a href=\"/delete/?author=" + resource.getAuthor()
                            + "&name=" + resource.getName() + "&version=" + resource.getVersion()
                            + "\"><button class=\"btn btn-default\">Delete</button></a>&nbsp;" +
                        "<a href=\"#\" onclick=\"doPrompt(this," +
                            " '" + resource.getName() + "'," +
                            " '" + resource.getAuthor() + "'," +
                            " '" + resource.getVersion() + "'); return false;\">" +
                            "<button class=\"btn btn-default\">Get code</button></a>&nbsp;" +
                        "<a href=\"/" + resource.getAuthor() + "/"
                            + resource.getName() + "/" + resource.getName()
                            + "_" + resource.getVersion() + "." + resource.getType() + "\">" +
                            "<button class=\"btn btn-default\">Download</button></a>&nbsp;" +
                        "</td>" +
                        "</tr>\n";
            }

            try (InputStream in = getClass().getResourceAsStream("/manage.html")) {
                return IOUtils.toString(in).replace("%deleteElements%", elements);
            }
        });

        // Login page
        get("/login/", (req, res) -> {
            if (req.session() == null || req.session().attribute("loggedIn") == null) {
                String password = req.queryParams("password");
                String message = "Please login before accessing our resources.";

                if (password != null) {
                    if (password.equalsIgnoreCase(this.password)) {
                        req.session(true).attribute("loggedIn", true);
                        return "Logged in.<script>document.location=\"/manage/\";</script>";
                    }  else {
                        message = "Invalid password.";
                    }
                }

                try (InputStream in = getClass().getResourceAsStream("/login.html")) {
                    return IOUtils.toString(in).replace("%messages%", message);
                }
            } else {
                return "Logged in.<script>document.location=\"/manage/\";</script>";
            }
        });


        // Resource deletion
        get("/delete/", (req, res) -> {
            // Make sure they are logged in
            if (req.session() == null || req.session().attribute("loggedIn") == null) {
                halt(403, "Not logged in.<script>document.location=\"/login/\";</script>");
            }

            String name = req.queryParams("name");
            String author = req.queryParams("author");
            String version = req.queryParams("version");

            // Find the resource
            Resource resource = null;

            for (Resource checkResource : resources) {
                if (checkResource.getName().equalsIgnoreCase(name)
                        && checkResource.getAuthor().equalsIgnoreCase(author)
                        && checkResource.getVersion().equalsIgnoreCase(version)) {
                    resource = checkResource;
                    break;
                }
            }

            if (resource == null) {
                halt(404, "Resource not found.");
                return null;
            }

            System.out.println("Deleting resource " + resource);
            resources.remove(resource);
            save();

            return "Successfully deleted.<script>document.location=\"/manage/\";</script>";
        });

        // Resource uploading
        post("/upload/", (req, res) -> {
            // Make sure they are logged in
            if (req.session() == null || req.session().attribute("loggedIn") == null) {
                halt(403, "Not logged in.<script>document.location=\"/login/\";</script>");
            }

            // Make sure we have the multipart attribute set
            req.raw().setAttribute(Request.__MULTIPART_CONFIG_ELEMENT,
                    new MultipartConfigElement(System.getProperty("java.io.tmpdir")));

            File parentFolder = new File("resources");
            if (!parentFolder.exists() && !parentFolder.mkdirs()) {
                throw new RuntimeException("Failed to create directory " + parentFolder.getPath());
            }
            if (req.raw().getContentType() != null
                    && req.raw().getContentType().startsWith("multipart/form-data")) {
                Part userfiles = req.raw().getPart("file");
                if (userfiles != null) {
                    byte[] data = IOUtils.toByteArray(userfiles.getInputStream(), userfiles.getSize());

                    // Create a new resource
                    Resource resource = new Resource();
                    resource.setName(req.queryParams("name"));
                    resource.setAuthor(req.queryParams("author"));
                    resource.setVersion(req.queryParams("version"));

                    String filename = userfiles.getHeader("content-disposition");
                    filename = filename.substring(filename.lastIndexOf(".") + 1);
                    if (filename.endsWith("\"") || filename.endsWith("'")) {
                        filename = filename.substring(0, filename.length() - 1);
                    }
                    resource.setType(filename);

                    resource.setContents(data);

                    if (req.queryParams("mainClass") != null) {
                        String mainClass = URLDecoder.decode(req.queryParams("mainClass"), "UTF-8");
                        resource.setMainClass(mainClass);
                    }

                    System.out.println("Uploaded new resource: " + resource);

                    resources.add(resource);
                    save();

                    halt(200, "Upload completed successfully.<script>document.location=\"/manage/\";</script>");
                    return null;
                } else {
                    halt(404, "Failed to upload a file with your request. (as part \"file\")");
                    return null;
                }
            } else {
                halt(404, "Failed to upload a file with your request. (as part \"file\")");
                return null;
            }
        });

        get("/res/*", (req, res) -> {
            String url = req.pathInfo();

            if (url.contains("*") || url.contains("//") || url.contains("..") || url.contains("~")) {
                halt(404);
            }

            String contents = null;
            try (InputStream in = getClass().getResourceAsStream(url)) {
                contents = IOUtils.toString(in);
            }

            String type;

            if (url.endsWith(".js")) {
                type = "text/javascript";
            } else if (url.endsWith(".html")) {
                type = "text/html";
            } else if (url.endsWith(".css")) {
                type = "text/css";
            } else {
                type = "application/octet-stream";
            }

            res.type(type);

            return contents;
        });

        get("*", (req, res) -> "File not found.");
    }

    private void save() {
        try (ObjectOutputStream out = new ObjectOutputStream(
                new FileOutputStream(new File("cubirepo.dat")))) {
            out.writeObject(resources);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void load() {
        if (!new File("cubirepo.dat").exists()) {
            resources = new ArrayList<>();
            return;
        }

        try (ObjectInputStream in = new ObjectInputStream(
                new FileInputStream(new File("cubirepo.dat")))) {
            resources = (List<Resource>) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        System.out.println("CubiRepo v0.1");

        CubiRepo instance = new CubiRepo();

        JCommander commander = new JCommander(instance);
        commander.setAcceptUnknownOptions(false);
        commander.setAllowAbbreviatedOptions(true);
        commander.setCaseSensitiveOptions(false);
        commander.setProgramName("java -jar cubirepo.jar");
        commander.parse(args);

        if (instance.showHelp) {
            commander.usage();
            System.exit(0);
        }

        instance.start();
    }
}
