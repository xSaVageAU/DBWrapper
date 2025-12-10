package savage.dbwrapper.config;

import com.google.gson.annotations.SerializedName;

public class InstallationProgress {
    @SerializedName("binary_copied")
    private boolean binaryCopied = false;

    @SerializedName("binary_extracted")
    private boolean binaryExtracted = false;

    @SerializedName("database_installed")
    private boolean databaseInstalled = false;

    @SerializedName("database_started")
    private boolean databaseStarted = false;

    public boolean isBinaryCopied() {
        return binaryCopied;
    }

    public void setBinaryCopied(boolean binaryCopied) {
        this.binaryCopied = binaryCopied;
    }

    public boolean isBinaryExtracted() {
        return binaryExtracted;
    }

    public void setBinaryExtracted(boolean binaryExtracted) {
        this.binaryExtracted = binaryExtracted;
    }

    public boolean isDatabaseInstalled() {
        return databaseInstalled;
    }

    public void setDatabaseInstalled(boolean databaseInstalled) {
        this.databaseInstalled = databaseInstalled;
    }

    public boolean isDatabaseStarted() {
        return databaseStarted;
    }

    public void setDatabaseStarted(boolean databaseStarted) {
        this.databaseStarted = databaseStarted;
    }
}