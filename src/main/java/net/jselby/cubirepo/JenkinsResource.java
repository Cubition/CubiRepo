package net.jselby.cubirepo;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * The JenkinsResource pulls Servers directly from my Jenkins.
 *
 * @author j_selby
 */
public class JenkinsResource extends Resource {
    private transient final long serialVersionUID = 104242301239l;

    private transient static final URL PROJECT_URL;

    public JenkinsResource() {
        name = "server";
        version = "latest";
        author = "cubition";
        mainClass = "net.cubition.server.ServerBaseController";
        type = "jar";
    }

    static {
        URL PROJECT_URL1 = null;
        try {
            PROJECT_URL1 = new URL("http://jenkins.jselby.net/job/Cubition/api/json");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        PROJECT_URL = PROJECT_URL1;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getAuthor() {
        return author;
    }

    @Override
    public byte[] getContents() {
        // Find out latest build
        try {
            Gson gson = new Gson();

            InputStream jsonBasicIn = PROJECT_URL.openStream();
            String jsonBasicString = IOUtils.toString(jsonBasicIn);
            jsonBasicIn.close();

            // Get the last stable build
            JsonObject jsonBasic = gson.fromJson(jsonBasicString, JsonObject.class);
            JsonObject jsonBasicLastStable = jsonBasic.get("lastStableBuild").getAsJsonObject();

            // Poll the build itself
            URL buildDescriptionUrl = new URL(jsonBasicLastStable.get("url").getAsString() + "api/json");

            InputStream buildDescriptionIn = buildDescriptionUrl.openStream();
            String buildDescriptionString = IOUtils.toString(buildDescriptionIn);
            buildDescriptionIn.close();

            // Get the artifacts from it
            JsonObject buildDescription = gson.fromJson(buildDescriptionString, JsonObject.class);
            JsonArray artifacts = buildDescription.get("artifacts").getAsJsonArray();

            JsonObject artifact = null;

            for (JsonElement object : artifacts) {
                JsonObject discoveredArtifact = object.getAsJsonObject();
                String name = discoveredArtifact.get("fileName").getAsString().toLowerCase();
                if (name.startsWith("server")) {
                    artifact = discoveredArtifact;
                    break;
                }
            }

            if (artifact == null) {
                return null;
            }

            // Woo! We have an artifact now
            String name = artifact.get("fileName").getAsString();

            URL artifactUrl = new URL(jsonBasicLastStable.get("url").getAsString() + "artifact/out/" + name);

            return IOUtils.toByteArray(artifactUrl);
        } catch ( IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public String getMainClass() {
        return mainClass;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public void setContents(byte[] contents) {}

    @Override
    public void setAuthor(String author) {}

    @Override
    public void setMainClass(String mainClass) {}

    @Override
    public void setName(String name) {}

    @Override
    public void setType(String type) {}

    @Override
    public void setVersion(String version) {}

    @Override
    public boolean canDelete() {
        return false;
    }
}
