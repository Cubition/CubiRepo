package net.jselby.cubirepo;

import org.apache.commons.io.IOUtils;

import java.io.*;

public class Resource implements Serializable {
    /**
     * The name of the resource defines the actual resource name, and is used in downloading resources.
     */
    private String name;

    /**
     * The author is the one who created the resource, and therefore defines what namespace this resource is under.
     */
    private String author;

    /**
     * The version of the resource.
     */
    private String version;

    /**
     * The main class defines what endpoint the Bootstrap should look at.
     */
    private String mainClass;

    /**
     * Returns the type of object this is.
     */
    private String type;

    /**
     * Returns the name of this resource. This is also the name used in polling for resources.
     *
     * @return The name of this resource.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the author of this resource. This also defines what namespace this resource is under.
     *
     * @return The author of this resource.
     */
    public String getAuthor() {
        return author;
    }

    /**
     * Returns the version of this resource.
     *
     * @return The version of this resource.
     */
    public String getVersion() {
        return version;
    }

    /**
     * Returns the MainClass for this resource.
     *
     * @return The MainClass for this resource.
     */
    public String getMainClass() {
        return mainClass;
    }

    /**
     * Returns the type of file this is (jar, mod, etc)
     *
     * @return The type of this resource.
     */
    public String getType() {
        return type;
    }

    /**
     * Returns the byte contents of the main resource itself.
     *
     * @return The contents of the resource.
     */
    public byte[] getContents() {
        File file = new File("cache", getName() + "_" + getAuthor() + "_" + getVersion() + "." + getType());

        try (FileInputStream in = new FileInputStream(file)) {
            return IOUtils.toByteArray(in);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setMainClass(String mainClass) {
        this.mainClass = mainClass;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setContents(byte[] contents) {
        // Save it to disk
        File parent = new File("cache");
        if (!parent.exists()) {
            parent.mkdir();
        }

        File file = new File(parent, getName() + "_" + getAuthor() + "_" + getVersion() + "." + getType());
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(contents);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean canDelete() {
        return true;
    }

    @Override
    public String toString() {
        return "Resource{" +
                "name='" + name + '\'' +
                ", author='" + author + '\'' +
                ", version='" + version + '\'' +
                ", type='" + type + '\'' +
                ", mainClass='" + mainClass + '\'' +
                '}';
    }
}