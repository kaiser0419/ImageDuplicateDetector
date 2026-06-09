package com.imgdupl.core;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;

/**
 * Dual-strategy image similarity engine:
 *   1) MD5 exact match (byte-for-byte identical files)
 *   2) pHash (DCT perceptual hash) for visually similar images
 */
public class PHashEngine {

    // pHash parameters
    private static final int HASH_SIZE  = 32;  // Resize target (32x32 DCT input)
    private static final int SMALL_SIZE = 8;   // Keep top-left 8x8 low-freq block → 64 bits

    // Similarity threshold: 0 = identical hash, 64 = completely different
    // Typical ranges: 0-5 near-exact, 6-15 similar, 16+ different
    private static final int THRESHOLD = 12;

    // ── Public API ──────────────────────────────────────────────────────────

    /** Compute pHash. Returns 0L on failure (we use md5 as primary exact check). */
    public long computeHash(File file) {
        try {
            BufferedImage img = ImageIO.read(file);
            if (img == null) return 0L;
            return dctHash(img);
        } catch (Exception e) {
            return 0L;
        }
    }

    /** MD5 of raw file bytes — catches exact duplicates regardless of metadata. */
    public String computeMD5(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buf = new byte[8192];
            int read;
            while ((read = fis.read(buf)) != -1) md.update(buf, 0, read);
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** Hamming distance between two 64-bit hashes (0 = identical, 64 = max). */
    public int hammingDistance(long h1, long h2) {
        return Long.bitCount(h1 ^ h2);
    }

    public boolean isSimilar(long h1, long h2) {
        // Treat zero hashes (error) as non-matching
        if (h1 == 0L || h2 == 0L) return false;
        return hammingDistance(h1, h2) <= THRESHOLD;
    }

    /**
     * Group records by similarity. Uses MD5 first (exact), then pHash (visual).
     * Records within each group are sorted by file size descending (largest = best).
     */
    public List<List<ImageRecord>> groupSimilar(List<ImageRecord> records) {
        int n = records.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        // Pass 1: MD5 exact matches
        Map<String, Integer> md5Map = new HashMap<>();
        for (int i = 0; i < n; i++) {
            String md5 = records.get(i).getMd5();
            if (md5 != null) {
                if (md5Map.containsKey(md5)) {
                    union(parent, i, md5Map.get(md5));
                } else {
                    md5Map.put(md5, i);
                }
            }
        }

        // Pass 2: pHash visual similarity
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                // Skip if already grouped
                if (find(parent, i) == find(parent, j)) continue;
                long hi = records.get(i).getHash();
                long hj = records.get(j).getHash();
                if (isSimilar(hi, hj)) {
                    union(parent, i, j);
                }
            }
        }

        // Collect and filter groups ≥ 2
        Map<Integer, List<ImageRecord>> groupMap = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            groupMap.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(records.get(i));
        }

        List<List<ImageRecord>> result = new ArrayList<>();
        for (List<ImageRecord> group : groupMap.values()) {
            if (group.size() >= 2) {
                group.sort(Comparator.comparingLong(ImageRecord::getFileSize).reversed());
                result.add(group);
            }
        }

        result.sort((a, b) -> {
            if (b.size() != a.size()) return Integer.compare(b.size(), a.size());
            return Long.compare(b.get(0).getFileSize(), a.get(0).getFileSize());
        });

        return result;
    }

    // ── pHash Core (DCT) ─────────────────────────────────────────────────────

    private long dctHash(BufferedImage original) {
        // Step 1: Flatten alpha, convert to plain RGB, resize to HASH_SIZE x HASH_SIZE
        BufferedImage resized = new BufferedImage(HASH_SIZE, HASH_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setColor(Color.WHITE); // white background for transparent PNGs
        g.fillRect(0, 0, HASH_SIZE, HASH_SIZE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(original, 0, 0, HASH_SIZE, HASH_SIZE, null);
        g.dispose();

        // Step 2: Grayscale pixel matrix [row][col]
        double[][] pixels = new double[HASH_SIZE][HASH_SIZE];
        for (int row = 0; row < HASH_SIZE; row++) {
            for (int col = 0; col < HASH_SIZE; col++) {
                int rgb = resized.getRGB(col, row); // getRGB(x, y) = getRGB(col, row)
                int r = (rgb >> 16) & 0xFF;
                int gv = (rgb >> 8)  & 0xFF;
                int b  =  rgb        & 0xFF;
                pixels[row][col] = 0.299 * r + 0.587 * gv + 0.114 * b;
            }
        }

        // Step 3: 2D DCT — FIX: pixels[row][col] not pixels[col][row]
        double[][] dct = applyDCT(pixels);

        // Step 4: Extract top-left SMALL_SIZE x SMALL_SIZE low-frequency coefficients
        // Skip DC component [0][0] — it encodes average brightness and causes false mismatches
        double[] lowFreq = new double[SMALL_SIZE * SMALL_SIZE - 1];
        double sum = 0;
        int idx = 0;
        for (int row = 0; row < SMALL_SIZE; row++) {
            for (int col = 0; col < SMALL_SIZE; col++) {
                if (row == 0 && col == 0) continue; // skip DC
                lowFreq[idx++] = dct[row][col];
                sum += dct[row][col];
            }
        }

        // Step 5: Mean of AC components only
        double mean = sum / lowFreq.length;

        // Step 6: Build hash — bit i = 1 if lowFreq[i] >= mean
        long hash = 0L;
        for (int i = 0; i < lowFreq.length; i++) {
            if (lowFreq[i] >= mean) {
                hash |= (1L << i);
            }
        }
        return hash;
    }

    /**
     * Separable 2D DCT-II via two passes of 1D DCT for efficiency.
     */
    private double[][] applyDCT(double[][] input) {
        int n = input.length;
        // Row-wise DCT
        double[][] temp = new double[n][n];
        for (int row = 0; row < n; row++) {
            temp[row] = dct1D(input[row], n);
        }
        // Column-wise DCT
        double[][] output = new double[n][n];
        for (int col = 0; col < n; col++) {
            double[] column = new double[n];
            for (int row = 0; row < n; row++) column[row] = temp[row][col];
            double[] dctCol = dct1D(column, n);
            for (int row = 0; row < n; row++) output[row][col] = dctCol[row];
        }
        return output;
    }

    private double[] dct1D(double[] x, int n) {
        double[] out = new double[n];
        double scale0 = Math.sqrt(1.0 / n);
        double scaleK  = Math.sqrt(2.0 / n);
        for (int k = 0; k < n; k++) {
            double sum = 0;
            for (int i = 0; i < n; i++) {
                sum += x[i] * Math.cos(Math.PI * k * (2 * i + 1) / (2.0 * n));
            }
            out[k] = (k == 0 ? scale0 : scaleK) * sum;
        }
        return out;
    }

    // ── Union-Find ───────────────────────────────────────────────────────────

    private int find(int[] parent, int i) {
        if (parent[i] != i) parent[i] = find(parent, parent[i]);
        return parent[i];
    }

    private void union(int[] parent, int a, int b) {
        int ra = find(parent, a), rb = find(parent, b);
        if (ra != rb) parent[ra] = rb;
    }
}
