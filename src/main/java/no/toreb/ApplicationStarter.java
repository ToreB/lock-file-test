package no.toreb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.File;
import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@EnableScheduling
@SpringBootApplication
public class ApplicationStarter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationStarter.class);

    private static final long LOCK_TIMEOUT = 20000;

    public static void main(final String[] args) {
        SpringApplication.run(ApplicationStarter.class);
    }

    private static File getLatestLockFile() {
        final File dir = new File(".");
        return Arrays.stream(dir.listFiles((dir1, name) -> name.startsWith("lockfile")))
                                          .max(Comparator.comparing(File::getName))
                                          .orElse(null);
    }

    private SimpleEntry<Boolean, File> acquireLock() throws IOException {
        LOGGER.info("Trying to acquire lock.");

        final File latestLockFile = getLatestLockFile();
        // If lock file does not exist, try to create it.
        if (latestLockFile == null) {
            LOGGER.info("Lock file does not exist. Trying to create.");
            final File lockFile = new File("lockfile1");
            final boolean fileCreated = lockFile.createNewFile();
            if (fileCreated) {
               LOGGER.info("New lock file \"{}\" created.", lockFile.getName());
            } else {
                LOGGER.warn("Unable to create lock file \"{}\".", lockFile.getName());
            }
            return new SimpleEntry<>(fileCreated, fileCreated ? lockFile : null);
        }

        // A lock file exists.
        final long lastModified = latestLockFile.lastModified();
        final long lockDuration = System.currentTimeMillis() - lastModified;
        LOGGER.info("Lock file \"{}\" exists. Created {} milliseconds ago.", latestLockFile.getName(), lockDuration);
        if (lockDuration >= LOCK_TIMEOUT) {
            LOGGER.info("Lock file \"{}\" has expired!", latestLockFile.getName());
            final String lockFileName = latestLockFile.getName();
            final Pattern pattern = Pattern.compile("\\d+");
            final Matcher matcher = pattern.matcher(lockFileName);
            matcher.find();
            final int lockFileNumber = Integer.parseInt(matcher.group());

            final File newLockFile = new File("lockfile" + (lockFileNumber + 1));
            LOGGER.info("Trying to creating new lock file \"{}\".", newLockFile.getName());
            final boolean newLockFileCreated = newLockFile.createNewFile();
            if (!newLockFileCreated) {
                LOGGER.warn("Unable to create lock file \"{}\".", newLockFile.getName());
                return new SimpleEntry<>(false, null);
            }

            LOGGER.info("New lock file \"{}\" created.", newLockFile.getName());
            LOGGER.info("Trying to delete lock file \"{}\".", latestLockFile.getName());

            final boolean lockFileDeleted = latestLockFile.delete();
            if (lockFileDeleted) {
                LOGGER.info("Lock file \"{}\" deleted.", latestLockFile.getName());
            } else {
                LOGGER.warn("Unable to delete lock file \"{}\".", latestLockFile.getName());
            }

            return new SimpleEntry<>(lockFileDeleted, lockFileDeleted ? newLockFile : null);
        }

        // Lock file exists and lock has not expired.
        return new SimpleEntry<>(false, null);
    }

    @Scheduled(fixedDelay = 5000)
    public void process() throws InterruptedException, IOException {
        LOGGER.info("**********************************************");

        final SimpleEntry<Boolean, File> lock = acquireLock();
        final boolean lockAcquired = lock.getKey();
        if (lockAcquired) {
            LOGGER.info("Lock acquired.");
        } else {
            LOGGER.info("Unable to acquire lock. Going back to sleep...");
            return;
        }

        final File lockFile = lock.getValue();
        final long lastModified = lockFile.lastModified();

        final long millis = (long) (Math.random() * 15000);
        LOGGER.info("Starting processing! Duration {} milliseconds.", millis);
        Thread.sleep(millis);
        if (Math.random() > 0.8) {
            LOGGER.info("Sleeping 20 seconds to simulate a hang situation.");
            Thread.sleep(20000);
        }
        LOGGER.info("Done processing!");

        final long lockDuration = System.currentTimeMillis() - lastModified;
        if (lockDuration < LOCK_TIMEOUT) {
            LOGGER.info("Deleting lock file \"{}\"", lockFile.getName());
            final boolean lockFileDeleted = lockFile.delete();
            if (lockFileDeleted) {
                LOGGER.info("Lock file \"{}\" deleted.", lockFile.getName());
            } else {
                LOGGER.warn("Unable to delete lock file \"{}\".", lockFile.getName());
            }
        } else {
            LOGGER.warn("Lock for file \"{}\" has expired! Should not delete!", lockFile.getName());
        }

        LOGGER.info("**********************************************");
    }
}
