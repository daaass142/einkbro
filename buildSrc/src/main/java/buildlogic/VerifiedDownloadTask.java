package buildlogic;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

@CacheableTask
public abstract class VerifiedDownloadTask extends DefaultTask {
    private static final int BUFFER_SIZE = 64 * 1024;

    @Input
    public abstract Property<String> getSourceUrl();

    @Input
    public abstract Property<String> getExpectedSha256();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void downloadAndVerify() throws Exception {
        File target = getOutputFile().get().getAsFile();
        String expected = getExpectedSha256().get().toLowerCase();

        if (target.isFile() && sha256(target).equals(expected)) {
            return;
        }

        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Unable to create " + parent);
        }

        File partial = new File(parent, target.getName() + ".part");
        Files.deleteIfExists(partial.toPath());

        try {
            download(getSourceUrl().get(), partial);

            String actual = sha256(partial);
            if (!actual.equals(expected)) {
                throw new IllegalStateException(
                    "Checksum mismatch: expected " + expected + ", got " + actual
                );
            }

            try {
                Files.move(
                    partial.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(
                    partial.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                );
            }
        } finally {
            Files.deleteIfExists(partial.toPath());
        }
    }

    private static void download(String source, File destination) throws Exception {
        HttpURLConnection connection =
            (HttpURLConnection) URI.create(source).toURL().openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(120_000);
        connection.setRequestProperty("User-Agent", "EinkBro-verified-build");

        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Download failed with HTTP " + status);
            }

            try (
                BufferedInputStream input =
                    new BufferedInputStream(connection.getInputStream());
                FileOutputStream fileOutput = new FileOutputStream(destination);
                BufferedOutputStream output = new BufferedOutputStream(fileOutput)
            ) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                output.flush();
                fileOutput.getFD().sync();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (
            BufferedInputStream input =
                new BufferedInputStream(new FileInputStream(file))
        ) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
