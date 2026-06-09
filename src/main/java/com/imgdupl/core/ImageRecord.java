package com.imgdupl.core;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Represents a single image file with its computed metadata.
 */
public class ImageRecord {

    private final File   file;
    private final long   fileSize;
    private       long   hash;    // pHash (0 = uncomputed/error)
    private       String md5;     // MD5 of raw file bytes (null = uncomputed)
    private       int    width;
    private       int    height;
    private       String dimensionStr;

    public ImageRecord(File file) {
        this.file     = file;
        this.fileSize = file.length();
        this.hash     = 0L;
        this.md5      = null;
        readDimensions();
    }

    private void readDimensions() {
        try {
            BufferedImage img = ImageIO.read(file);
            if (img != null) {
                this.width        = img.getWidth();
                this.height       = img.getHeight();
                this.dimensionStr = width + "×" + height;
            } else {
                this.dimensionStr = "?×?";
            }
        } catch (IOException e) {
            this.dimensionStr = "?×?";
        }
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public File   getFile()         { return file; }
    public String getFileName()     { return file.getName(); }
    public String getAbsolutePath() { return file.getAbsolutePath(); }
    public long   getFileSize()     { return fileSize; }
    public long   getHash()         { return hash; }
    public String getMd5()          { return md5; }
    public int    getWidth()        { return width; }
    public int    getHeight()       { return height; }
    public String getDimensionStr() { return dimensionStr; }

    public void setHash(long hash) { this.hash = hash; }
    public void setMd5(String md5) { this.md5  = md5;  }

    /** Human-readable file size */
    public String getFileSizeStr() {
        if (fileSize < 1024)               return fileSize + " B";
        if (fileSize < 1024 * 1024)        return String.format("%.1f KB", fileSize / 1024.0);
        if (fileSize < 1024L * 1024 * 1024) return String.format("%.2f MB", fileSize / (1024.0 * 1024));
        return String.format("%.2f GB", fileSize / (1024.0 * 1024 * 1024));
    }

    @Override
    public String toString() {
        return file.getName() + " [" + getFileSizeStr() + ", " + dimensionStr + "]";
    }
}
