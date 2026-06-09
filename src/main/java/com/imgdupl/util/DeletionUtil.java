package com.imgdupl.util;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Cross-platform file deletion utility.
 * Supports both Trash (safe) and permanent deletion.
 */
public class DeletionUtil {

    public enum DeleteMode { TRASH, PERMANENT }

    /**
     * Delete a file using the specified mode.
     * @return true on success, false on failure
     */
    public static boolean delete(File file, DeleteMode mode) {
        if (!file.exists()) return false;

        if (mode == DeleteMode.TRASH) {
            return moveToTrash(file);
        } else {
            return file.delete();
        }
    }

    private static boolean moveToTrash(File file) {
        // Java 9+ Desktop.moveToTrash support
        try {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.MOVE_TO_TRASH)) {
                return desktop.moveToTrash(file);
            }
        } catch (Exception ignored) {}

        // Fallback: move to user home/.Trash or user home/Desktop/Trash
        try {
            File trash = getTrashDirectory();
            if (trash != null) {
                Path dest = trash.toPath().resolve(file.getName());
                // Handle name collisions
                if (Files.exists(dest)) {
                    String name = file.getName();
                    String base = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
                    String ext  = name.contains(".") ? name.substring(name.lastIndexOf('.'))    : "";
                    dest = trash.toPath().resolve(base + "_" + System.currentTimeMillis() + ext);
                }
                Files.move(file.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
                return true;
            }
        } catch (IOException ignored) {}

        // Last resort: permanent delete
        return file.delete();
    }

    private static File getTrashDirectory() {
        String os = System.getProperty("os.name").toLowerCase();
        File trash = null;

        if (os.contains("mac")) {
            trash = new File(System.getProperty("user.home") + "/.Trash");
        } else if (os.contains("win")) {
            // Windows Recycle Bin via Desktop API already handled above
            trash = new File(System.getProperty("user.home") + "/Desktop/Deleted");
        } else {
            // Linux XDG Trash
            String xdgData = System.getenv("XDG_DATA_HOME");
            if (xdgData != null) {
                trash = new File(xdgData + "/Trash/files");
            } else {
                trash = new File(System.getProperty("user.home") + "/.local/share/Trash/files");
            }
        }

        if (trash != null && !trash.exists()) {
            trash.mkdirs();
        }
        return trash;
    }

    /** Check if OS-level Trash is supported */
    public static boolean isTrashSupported() {
        try {
            return Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH);
        } catch (Exception e) {
            return false;
        }
    }
}
