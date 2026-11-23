package local.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProjectTools {
    private static final Logger log = LoggerFactory.getLogger(ProjectTools.class);
    private static final int MAX_FILE_BYTES = 40_000;
    private final Path projectRoot;

    public ProjectTools(@Value("${agent.project-root:..}") String projectRoot) {
        this.projectRoot = Path.of(projectRoot).toAbsolutePath().normalize();
    }

    @Tool(description = "List files inside the project directory. Optionally provide a relative subdirectory.")
    public String listFiles(@ToolParam(description = "Relative directory; use . for the project root") String directory) {
        long startedAt = System.nanoTime();
        String requestedDirectory = directory == null || directory.isBlank() ? "." : directory;
        log.info("Tool invoked tool=listFiles directory={}", requestedDirectory);
        try {
            Path dir = resolve(requestedDirectory);
            if (!Files.isDirectory(dir)) {
                String output = "That directory does not exist.";
                log.info("Tool completed tool=listFiles durationMs={} output={}", elapsedMillis(startedAt), output);
                return output;
            }
            try (Stream<Path> paths = Files.walk(dir)) {
                String output = paths.filter(Files::isRegularFile)
                        .filter(p -> !p.startsWith(projectRoot.resolve(".git")))
                        .sorted(Comparator.naturalOrder())
                        .limit(200)
                        .map(projectRoot::relativize)
                        .map(Path::toString)
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("No files found.");
                log.info("Tool completed tool=listFiles durationMs={} output={}", elapsedMillis(startedAt), output);
                return output;
            }
        } catch (IOException | IllegalArgumentException e) {
            String output = "Could not list files: " + e.getMessage();
            log.warn("Tool failed tool=listFiles durationMs={} output={}", elapsedMillis(startedAt), output, e);
            return output;
        }
    }

    @Tool(description = "Read a UTF-8 text file inside the project directory. Use a relative path.")
    public String readFile(@ToolParam(description = "Relative path of the file") String relativePath) {
        long startedAt = System.nanoTime();
        log.info("Tool invoked tool=readFile path={}", relativePath);
        try {
            Path file = resolve(relativePath);
            if (!Files.isRegularFile(file)) {
                String output = "That file does not exist.";
                log.info("Tool completed tool=readFile durationMs={} output={}", elapsedMillis(startedAt), output);
                return output;
            }
            if (Files.size(file) > MAX_FILE_BYTES) {
                String output = "File is too large to read (40 KB limit).";
                log.info("Tool completed tool=readFile durationMs={} output={}", elapsedMillis(startedAt), output);
                return output;
            }
            String output = Files.readString(file, StandardCharsets.UTF_8);
            log.info("Tool completed tool=readFile durationMs={} output={}", elapsedMillis(startedAt), output);
            return output;
        } catch (IOException | IllegalArgumentException e) {
            String output = "Could not read file: " + e.getMessage();
            log.warn("Tool failed tool=readFile durationMs={} output={}", elapsedMillis(startedAt), output, e);
            return output;
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private Path resolve(String relativePath) {
        Path candidate = projectRoot.resolve(relativePath).normalize().toAbsolutePath();
        if (!candidate.startsWith(projectRoot)) {
            throw new IllegalArgumentException("Path is outside the project directory.");
        }
        try {
            if (Files.exists(candidate) && !candidate.toRealPath().startsWith(projectRoot.toRealPath())) {
                throw new IllegalArgumentException("Path resolves outside the project directory.");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not resolve that path.", e);
        }
        return candidate;
    }
}
