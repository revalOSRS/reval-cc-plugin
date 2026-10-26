package com.revalclan.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps one credential per game account in a small file under
 * {@code ~/.runelite/reval-clan/link/}, <b>outside</b> the RuneLite profile config. The token is never
 * handed to {@link ConfigManager}: the profile is what RuneLite's cloud profile sync uploads, it
 * reaches disk only on a timer, and a hard kill can resurrect or lose entries. (The one-use Link code
 * is different: it is a short-lived setting the player pastes, so it stays in the config.)
 * <ul>
 *   <li>On POSIX systems the folder is created {@code rwx------} and every file {@code rw-------}, set
 *       in the create call itself so there is no window where the token is readable by others. An
 *       existing folder that is wider is tightened, or the save is refused.</li>
 *   <li>On Windows there is no mode bit to set; the file is protected by the ACL it inherits from the
 *       user's profile folder. That is access control, <b>not</b> encryption.</li>
 *   <li>Writes go to a private temp file that is fsync'ed and then atomically moved over the old one,
 *       so a crash leaves either the old or the new credential, never half of one.</li>
 *   <li>Reads refuse symbolic links and anything but a regular file, read at most
 *       {@value #MAX_FILE_BYTES} bytes, and accept only a file that passes
 *       {@link LinkCredential#fromData} for exactly the account it is named after.</li>
 *   <li>Two RuneLite clients can run at once on the same folder. An advisory lock on {@code .lock} (held only
 *       for the few milliseconds of the rename, of a conditional delete, or of removing an unusable file) keeps
 *       one client's "forget this rejected token" from deleting the newer token another client just saved.
 *       It is advisory: it protects between copies of this plugin, not against other programs. If it cannot
 *       be had in {@value #LOCK_WAIT_MS} ms the delete is skipped (a rejected token is rejected again the next time
 *       it is used, while a valid newer one must never be lost) and a save goes ahead without it; if the file
 *       system does not support locks at all, both behave as they did before the lock existed.</li>
 * </ul>
 * This keeps other users and other programs' accidents away from the token; it does not protect against
 * malware running as the same user, and it does not encrypt anything. Tokens are never logged.
 * <p>
 * Expiry is deliberately not judged here: a file whose expiry has passed <i>on this computer's clock</i>
 * is still returned, because a wrong clock must not be able to erase a valid server token. The caller
 * decides whether it may be used; only the server's word (or an unreadable file) removes one.
 */
@Slf4j
@Singleton
public class CredentialStore {
	static final String GROUP = "revalclan";
	/** Key prefix used while 2.24 was in development, when the token lived in the (syncable) profile config. */
	static final String LEGACY_PREFIX = "linkCredential_";
	static final String LINK_CODE_KEY = "linkCode";
	/** A credential is a few hundred bytes; anything bigger is not one. */
	static final int MAX_FILE_BYTES = 2048;
	private static final long STALE_TEMP_MS = 10 * 60 * 1000L;
	/** How long a save or a conditional delete waits for another client that is changing the same folder. */
	static final long LOCK_WAIT_MS = 1000;
	private static final long LOCK_POLL_MS = 20;
	static final String LOCK_FILE = ".lock";

	private static final Set<PosixFilePermission> OWNER_ONLY_FILE = PosixFilePermissions.fromString("rw-------");
	private static final Set<PosixFilePermission> OWNER_ONLY_DIR = PosixFilePermissions.fromString("rwx------");

	private final ConfigManager configManager;
	private final Gson gson;
	private final Path directory;
	private final boolean posix;
	private final long lockWaitMs;
	private boolean legacySwept; // guarded by this

	@Inject
	public CredentialStore(ConfigManager configManager, Gson gson) {
		this(configManager, gson, directoryUnder(RuneLite.RUNELITE_DIR));
	}

	/** Tests point this at a temporary folder; nothing in the player's real profile is touched. */
	CredentialStore(ConfigManager configManager, Gson gson, Path directory) {
		this(configManager, gson, directory, LOCK_WAIT_MS);
	}

	CredentialStore(ConfigManager configManager, Gson gson, Path directory, long lockWaitMs) {
		this.configManager = configManager;
		this.gson = gson;
		this.directory = directory;
		this.posix = directory.getFileSystem().supportedFileAttributeViews().contains("posix");
		this.lockWaitMs = lockWaitMs;
	}

	static Path directoryUnder(File runeliteDir) {
		return runeliteDir.toPath().resolve("reval-clan").resolve("link");
	}

	Path directory() {
		return directory;
	}

	Path fileFor(long accountHash) {
		return directory.resolve("account-" + accountHash + ".json");
	}

	// ------------------------------------------------------------------ credentials

	/**
	 * The stored credential for exactly this account, or null. Expiry is not checked (see the class
	 * comment). A file that cannot be a credential for this account is removed; a symbolic link or other
	 * non-regular file is refused and left alone.
	 */
	public synchronized LinkCredential load(long accountHash) {
		dropLegacyEntry(accountHash);
		return read(accountHash, false);
	}

	/** Stores the credential for its account, replacing any earlier one. Throws if it could not be made durable and private. */
	public synchronized void save(LinkCredential credential) {
		long accountHash = credential.getAccountHash();
		dropLegacyEntry(accountHash);
		byte[] bytes = gson.toJson(credential.toJson()).getBytes(StandardCharsets.UTF_8);
		Path temp = null;
		try {
			prepareDirectory();
			temp = directory.resolve("account-" + accountHash + "." + UUID.randomUUID() + ".tmp");
			writeOwnerOnly(temp, bytes);
			try (FolderLock ignored = lockOrNull()) {
				moveIntoPlace(temp, fileFor(accountHash));
			}
			temp = null;
			syncDirectory();
			sweepStaleTemps();
		} catch (IOException e) {
			log.warn("Could not store the Reval link: {}", e.toString());
			throw new UncheckedIOException("Could not store the Reval link", e);
		} finally {
			if (temp != null) {
				try {
					Files.deleteIfExists(temp);
				} catch (IOException ignored) {
					// nothing more can be done
				}
			}
		}
	}

	/**
	 * Removes the stored credential only if it is, right now, a valid credential for this account holding exactly
	 * this token. A newer link that replaced it meanwhile, in this client or in another one, is left alone: the
	 * read, the comparison and the delete happen under the folder lock. If another client keeps the lock for
	 * longer than {@value #LOCK_WAIT_MS} ms nothing is deleted.
	 */
	public synchronized void deleteIfToken(long accountHash, String token) {
		dropLegacyEntry(accountHash);
		if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return; // nothing stored, or a redirected folder that is never used
		try (FolderLock ignored = lockOrSkip()) {
			LinkCredential stored = read(accountHash, true);
			if (stored == null || !stored.getToken().equals(token)) return;
			Files.deleteIfExists(fileFor(accountHash));
		} catch (LockBusy busy) {
			log.warn("Left a rejected Reval link in place: another Reval Clan client is changing the link folder");
		} catch (IOException e) {
			log.warn("Could not remove a rejected Reval link: {}", e.toString());
		}
	}

	/** @param locked whether the caller already holds the folder lock */
	private LinkCredential read(long accountHash, boolean locked) {
		if (Files.isSymbolicLink(directory)) {
			log.warn("Ignoring the Reval link folder: it is a symbolic link");
			return null;
		}
		Path file = fileFor(accountHash);
		BasicFileAttributes attributes;
		try {
			attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
		} catch (NoSuchFileException e) {
			return null;
		} catch (IOException e) {
			log.warn("Could not look at a stored Reval link: {}", e.toString());
			return null;
		}
		if (attributes.isSymbolicLink() || !attributes.isRegularFile()) {
			log.warn("Ignoring a stored Reval link that is not a plain file");
			return null;
		}
		if (attributes.size() > MAX_FILE_BYTES) return discard(accountHash, locked);

		byte[] bytes;
		try (SeekableByteChannel in = Files.newByteChannel(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
			ByteBuffer buffer = ByteBuffer.allocate(MAX_FILE_BYTES + 1);
			while (buffer.hasRemaining() && in.read(buffer) >= 0) {
				// keep reading until full or end of file
			}
			bytes = Arrays.copyOf(buffer.array(), buffer.position());
		} catch (IOException e) {
			log.warn("Could not read a stored Reval link: {}", e.toString());
			return null;
		}
		if (bytes.length > MAX_FILE_BYTES) return discard(accountHash, locked); // grew since the size check
		try {
			JsonObject data = gson.fromJson(new String(bytes, StandardCharsets.UTF_8), JsonObject.class);
			if (data == null) throw new LinkException(LinkException.Kind.INVALID_RESPONSE);
			return LinkCredential.fromData(data, accountHash);
		} catch (Exception e) {
			return discard(accountHash, locked);
		}
	}

	/**
	 * Removes a file that cannot be a credential. Outside the folder lock a valid one may have been put there by
	 * another client since it was read, so the lock is taken first and the file is judged again; with the lock
	 * held elsewhere the file is simply left for the next look.
	 */
	private LinkCredential discard(long accountHash, boolean locked) {
		if (!locked) {
			try (FolderLock ignored = lockOrSkip()) {
				return read(accountHash, true);
			} catch (LockBusy busy) {
				return null;
			}
		}
		log.info("Dropping an unusable stored Reval link");
		try {
			Files.deleteIfExists(fileFor(accountHash));
		} catch (IOException e) {
			log.warn("Could not remove an unusable stored Reval link: {}", e.toString());
		}
		return null;
	}

	// ------------------------------------------------------------------ the folder lock

	/** The advisory lock on {@value #LOCK_FILE}; closing it lets other clients in. */
	private static final class FolderLock implements AutoCloseable {
		private final FileChannel channel;
		private final FileLock lock;

		FolderLock(FileChannel channel, FileLock lock) {
			this.channel = channel;
			this.lock = lock;
		}

		@Override
		public void close() {
			try {
				lock.release();
			} catch (IOException ignored) {
				// closing the channel releases it anyway
			}
			try {
				channel.close();
			} catch (IOException ignored) {
				// nothing more can be done
			}
		}
	}

	/** Another client kept the lock for the whole wait. */
	private static final class LockBusy extends IOException {
		LockBusy() {
			super("The Reval link folder is locked by another client");
		}
	}

	/**
	 * Takes the lock, waiting up to {@code lockWaitMs} for another client. Throws {@link LockBusy} when that
	 * client did not let go, and a plain {@link IOException} when this file system cannot lock at all.
	 */
	private FolderLock acquireLock() throws IOException {
		FileAttribute<?>[] attributes = posix
			? new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(OWNER_ONLY_FILE)}
			: new FileAttribute<?>[0];
		Set<OpenOption> options = new HashSet<>(Arrays.asList(StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS));
		FileChannel channel = FileChannel.open(directory.resolve(LOCK_FILE), options, attributes);
		boolean handedOver = false;
		try {
			long deadline = System.nanoTime() + lockWaitMs * 1_000_000L;
			while (true) {
				try {
					FileLock lock = channel.tryLock();
					if (lock != null) {
						handedOver = true;
						return new FolderLock(channel, lock);
					}
				} catch (OverlappingFileLockException heldInThisProcess) {
					// another CredentialStore in this very JVM has it: same thing as another client
				}
				if (System.nanoTime() >= deadline) throw new LockBusy();
				try {
					Thread.sleep(LOCK_POLL_MS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new LockBusy();
				}
			}
		} finally {
			if (!handedOver) {
				try {
					channel.close();
				} catch (IOException ignored) {
					// nothing more can be done
				}
			}
		}
	}

	/** For a change that must not be skipped: the lock, or null (go ahead unlocked) if it is busy or unsupported. */
	private FolderLock lockOrNull() {
		try {
			return acquireLock();
		} catch (LockBusy busy) {
			log.warn("Saving the Reval link without the folder lock: another Reval Clan client holds it");
		} catch (IOException | RuntimeException e) {
			log.debug("The Reval link folder cannot be locked here: {}", e.toString());
		}
		return null;
	}

	/** For a change that is safe to skip: the lock, null where locks are unsupported, {@link LockBusy} if busy. */
	private FolderLock lockOrSkip() throws LockBusy {
		try {
			return acquireLock();
		} catch (LockBusy busy) {
			throw busy;
		} catch (IOException | RuntimeException e) {
			log.debug("The Reval link folder cannot be locked here: {}", e.toString());
			return null;
		}
	}

	// ------------------------------------------------------------------ files

	private void prepareDirectory() throws IOException {
		if (Files.isSymbolicLink(directory)) throw new IOException("The Reval link folder is a symbolic link");
		if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
			Path parent = directory.getParent();
			if (parent != null && !Files.isDirectory(parent)) Files.createDirectories(parent);
			try {
				if (posix) {
					Files.createDirectory(directory, PosixFilePermissions.asFileAttribute(OWNER_ONLY_DIR));
				} else {
					Files.createDirectory(directory);
				}
			} catch (FileAlreadyExistsException raced) {
				// another client created it first; checked below
			}
		}
		if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw new IOException("The Reval link folder is not a folder");
		if (posix && !Files.getPosixFilePermissions(directory, LinkOption.NOFOLLOW_LINKS).equals(OWNER_ONLY_DIR)) {
			Files.setPosixFilePermissions(directory, OWNER_ONLY_DIR);
		}
	}

	/** Creates the file (never following a link, never reusing one) with its final permissions and forces it to disk. */
	private void writeOwnerOnly(Path file, byte[] bytes) throws IOException {
		FileAttribute<?>[] attributes = posix
			? new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(OWNER_ONLY_FILE)}
			: new FileAttribute<?>[0];
		try (FileChannel channel = FileChannel.open(file, EnumSet.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE), attributes)) {
			ByteBuffer buffer = ByteBuffer.wrap(bytes);
			while (buffer.hasRemaining()) channel.write(buffer);
			channel.force(true);
		}
	}

	private static void moveIntoPlace(Path temp, Path target) throws IOException {
		try {
			Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/** Makes the rename itself durable. Not possible everywhere (Windows cannot open a folder), so best effort. */
	private void syncDirectory() {
		try (FileChannel folder = FileChannel.open(directory, StandardOpenOption.READ)) {
			folder.force(true);
		} catch (IOException | RuntimeException ignored) {
			// the file itself was already forced
		}
	}

	/** Temp files only outlive a crash between writing and renaming; they hold a full token, so do not leave them. */
	private void sweepStaleTemps() {
		long cutoff = System.currentTimeMillis() - STALE_TEMP_MS;
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.tmp")) {
			for (Path temp : stream) {
				BasicFileAttributes attributes = Files.readAttributes(temp, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
				if (attributes.isRegularFile() && attributes.lastModifiedTime().toMillis() < cutoff) Files.deleteIfExists(temp);
			}
		} catch (IOException | RuntimeException ignored) {
			// housekeeping only
		}
	}

	// ------------------------------------------------------------------ the config: Link code, and old entries to remove

	/** The raw, unvalidated Link code setting, or an empty string. */
	public String configuredLinkCode() {
		String value = configManager.getConfiguration(GROUP, LINK_CODE_KEY);
		return value == null ? "" : value;
	}

	/** Clears the Link code setting, but only if it still holds the value that was submitted. */
	public void clearLinkCodeIfUnchanged(String submitted) {
		if (submitted.equals(configuredLinkCode().trim())) {
			configManager.unsetConfiguration(GROUP, LINK_CODE_KEY);
		}
	}

	/**
	 * Development builds kept the token in the profile config, where cloud profile sync can copy it. Such an
	 * entry is deleted when met and is never read back into use: that token has already left the machine's
	 * private folder, so the account is simply linked again.
	 */
	private void dropLegacyEntry(long accountHash) {
		sweepLegacyEntriesOnce();
		String key = LEGACY_PREFIX + accountHash;
		if (configManager.getConfiguration(GROUP, key) != null) {
			configManager.unsetConfiguration(GROUP, key);
		}
	}

	private void sweepLegacyEntriesOnce() {
		if (legacySwept) return;
		legacySwept = true;
		try {
			String prefix = GROUP + "." + LEGACY_PREFIX;
			List<String> keys = configManager.getConfigurationKeys(prefix);
			if (keys == null) return;
			for (String full : keys) {
				if (full.startsWith(prefix)) configManager.unsetConfiguration(GROUP, full.substring(GROUP.length() + 1));
			}
		} catch (RuntimeException e) {
			log.debug("Could not look for old Reval link entries in the profile");
		}
	}
}
