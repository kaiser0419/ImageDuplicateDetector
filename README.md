markdown
# ImageDuplicateDetector

A clean, dark industrial-themed tool designed to scan, group, and manage duplicate or visually similar images using perceptual hashing.

---

## 🚀 Key Features

* **Drag & Drop UI:** Quickly load multiple images via drag-and-drop or traditional file browsing.
* **pHash (Perceptual Hash) with DCT:** Detects visually similar images even if they have been resized, compressed, or slightly altered.
* **Union-Find Grouping:** Correctly chains and groups transitive similarities together.
* **Smart Quality Sorting:** Results within each group are automatically ordered by file size, placing the highest quality (largest) image at the top.
* **"BEST" Badge Protection:** The top-quality image in each group receives a `BEST` badge and is protected from accidental deletion.
* **Flexible Clean-up:** Take action per-image with options to move files to the **Trash** or **Permanently Delete** them.
* **Bulk Actions:** Clean up entire groups instantly using the "Delete All Duplicates" feature (supports both Trash and Permanent deletion).
* **Live Analytics:** Summary bar displaying total groups found, redundant files, and total scanned counts alongside a scan progress indicator.

---

## 📦 How to Build and Run

### Prerequisites
* **Java Development Kit (JDK 17 or higher)**
* **Apache Maven**

### Build the Project
To compile the project and build the executable JAR file, run the following command in the root directory:
```bash
mvn clean package

```

This will generate an executable JAR file inside the `target/` directory.

### Run the Application

Once the build is complete, execute the application using:

```bash
java -jar target/ImageDuplicateDetector-1.0-SNAPSHOT.jar

```

*(Note: Replace `ImageDuplicateDetector-1.0-SNAPSHOT.jar` with your actual artifact name if it differs in your `pom.xml`).*

---

## 🛠 Under Development

* **Side-by-Side Comparison:** *(Beta / Buggy)* Work is currently underway to provide a side-by-side visual comparison matrix for grouped images.

---

## ⚙️ Tech Stack

* **Java** * **Maven** (`pom.xml`)
* **Perceptual Hashing (pHash) via Discrete Cosine Transform (DCT)**
* **Union-Find (Disjoint Set Union) Algorithm**

```

