package dev.privalo.radiantengine.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class FileUtil {

    private FileUtil() {
    }

    public static void writeString(File file, String content) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
    }

    public static void writeBytes(File file, byte[] content) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }
        Files.write(file.toPath(), content);
    }

    public static void deleteRecursively(File file) throws IOException {
        if (!file.exists()) {
            return;
        }
        Files.walkFileTree(file.toPath(), new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path p, BasicFileAttributes attrs) throws IOException {
                Files.delete(p);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void copyRecursively(File source, File target) throws IOException {
        Path src = source.toPath();
        Path dst = target.toPath();
        if (!Files.exists(src)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(src)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                Path out = dst.resolve(src.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(p, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    public static List<File> listRecursively(File dir, java.util.function.Predicate<File> filter) {
        List<File> out = new ArrayList<>();
        if (!dir.isDirectory()) {
            return out;
        }
        try (Stream<Path> stream = Files.walk(dir.toPath())) {
            stream.map(Path::toFile).filter(File::isFile).filter(filter).sorted().forEach(out::add);
        } catch (IOException ignored) {
            // unreadable folder -> empty list
        }
        return out;
    }

    public static void zipDirectory(File directory, File zipFile) throws IOException {
        Path root = directory.toPath();
        Files.deleteIfExists(zipFile.toPath());
        try (OutputStream os = Files.newOutputStream(zipFile.toPath());
             ZipOutputStream zip = new ZipOutputStream(os);
             Stream<Path> stream = Files.walk(root)) {
            List<Path> files = stream.filter(Files::isRegularFile).sorted().toList();
            for (Path p : files) {
                String name = root.relativize(p).toString().replace('\\', '/');
                ZipEntry entry = new ZipEntry(name);
                entry.setTime(0L);
                zip.putNextEntry(entry);
                try (InputStream in = Files.newInputStream(p)) {
                    in.transferTo(zip);
                }
                zip.closeEntry();
            }
        }
    }

    /** SHA-1 of one file's content (the value used by server.properties resource-pack-sha1), or null. */
    public static String sha1(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        try (InputStream in = Files.newInputStream(file.toPath())) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            return null;
        }
    }

    /** SHA-1 over every file (relative path + content) below the given roots; used to detect changes. */
    public static String hash(File... roots) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            for (File root : roots) {
                if (root == null || !root.exists()) {
                    continue;
                }
                if (root.isFile()) {
                    digest.update(root.getName().getBytes(StandardCharsets.UTF_8));
                    digest.update(Files.readAllBytes(root.toPath()));
                    continue;
                }
                try (Stream<Path> stream = Files.walk(root.toPath())) {
                    List<Path> files = stream.filter(Files::isRegularFile).sorted().toList();
                    for (Path p : files) {
                        digest.update(root.toPath().relativize(p).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
                        digest.update(Files.readAllBytes(p));
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            return "";
        }
    }
}
