package com.revalclan.auth;

import com.google.gson.Gson;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.revalclan.auth.TestKit.*;
import static org.junit.Assert.*;

/** The credential store against a real (temporary) file system: permissions, symlinks, size bounds, durability of what it returns. */
public class CredentialStoreTest {
	private static final boolean POSIX = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

	@Rule public TemporaryFolder temp = new TemporaryFolder();

	private FakeConfig config;
	private Path folder;
	private CredentialStore store;

	@Before
	public void setUp() {
		config = new FakeConfig();
		folder = temp.getRoot().toPath().resolve("reval-clan").resolve("link");
		store = new CredentialStore(config.manager, new Gson(), folder);
	}

	private static String json(long accountHash, String token) {
		return new Gson().toJson(data(accountHash, token, NOW.plus(Duration.ofDays(90))));
	}

	private Path fileOf(long accountHash) {
		return store.fileFor(accountHash);
	}

	private void write(Path file, String content) throws IOException {
		Files.createDirectories(file.getParent());
		Files.write(file, content.getBytes(StandardCharsets.UTF_8));
	}

	private static Set<PosixFilePermission> modeOf(Path path) throws IOException {
		return Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
	}

	private List<String> folderListing() throws IOException {
		try (Stream<Path> stream = Files.list(folder)) {
			return stream.map(path -> path.getFileName().toString()).sorted().collect(Collectors.toList());
		}
	}

	// ------------------------------------------------------------------ round trips

	@Test
	public void roundTripsPerAccount() throws Exception {
		store.save(credential(ACCOUNT_A, TOKEN_A));
		store.save(credential(ACCOUNT_B, TOKEN_B));

		assertEquals(TOKEN_A, store.load(ACCOUNT_A).getToken());
		assertEquals(TOKEN_B, store.load(ACCOUNT_B).getToken());
		assertNull("an account that never linked has nothing", store.load(ACCOUNT_C));
	}

	@Test
	public void aNewStoreOnTheSameFolderReloadsWhatWasSaved() throws Exception {
		store.save(credential(ACCOUNT_A, TOKEN_A));

		CredentialStore afterRestart = new CredentialStore(new FakeConfig().manager, new Gson(), folder);
		LinkCredential reloaded = afterRestart.load(ACCOUNT_A);

		assertNotNull(reloaded);
		assertEquals(TOKEN_A, reloaded.getToken());
		assertEquals(ACCOUNT_A, reloaded.getAccountHash());
		assertEquals(NOW.plus(Duration.ofDays(90)), reloaded.getExpiresAt());
	}

	@Test
	public void signedNegativeAccountHashesAreOrdinaryAccounts() throws Exception {
		long negative = -4_611_686_018_427_387_904L;
		store.save(credential(negative, TOKEN_A));
		assertEquals(TOKEN_A, store.load(negative).getToken());
		assertEquals(negative, store.load(negative).getAccountHash());
	}

	@Test
	public void saveReplacesTheOldCredentialAndLeavesNothingElseBehind() throws Exception {
		store.save(credential(ACCOUNT_A, TOKEN_A));
		store.save(credential(ACCOUNT_A, TOKEN_NEW));
		store.save(credential(ACCOUNT_B, TOKEN_B));

		assertEquals(TOKEN_NEW, store.load(ACCOUNT_A).getToken());
		assertEquals("one file per account, no temp files, and the lock file",
			java.util.Arrays.asList(".lock", "account-" + ACCOUNT_A + ".json", "account-" + ACCOUNT_B + ".json"), folderListing());
	}

	@Test
	public void theStoredFileIsExactlyTheValidatedCredentialJson() throws Exception {
		store.save(credential(ACCOUNT_A, TOKEN_A));
		String text = new String(Files.readAllBytes(fileOf(ACCOUNT_A)), StandardCharsets.UTF_8);
		assertEquals(json(ACCOUNT_A, TOKEN_A), text);
		assertTrue(text.length() < CredentialStore.MAX_FILE_BYTES / 4);
	}

	@Test
	public void theFolderLivesUnderTheRuneLiteFolder() {
		assertEquals(new File("/home/player/.runelite/reval-clan/link").toPath(),
			CredentialStore.directoryUnder(new File("/home/player/.runelite")));
	}

	// ------------------------------------------------------------------ never in the RuneLite config

	@Test
	public void theTokenNeverReachesTheRuneLiteConfig() throws Exception {
		store.save(credential(ACCOUNT_A, TOKEN_A));
		store.load(ACCOUNT_A);
		store.deleteIfToken(ACCOUNT_A, TOKEN_A);
		store.save(credential(ACCOUNT_B, TOKEN_B));

		assertTrue("not a single write to the profile: " + config.writes, config.writes.isEmpty());
		assertTrue(config.values.isEmpty());
	}

	@Test
	public void anOldProfileEntryIsRemovedWhenMetAndNeverReadBackIntoUse() throws Exception {
		config.values.put(FakeConfig.legacyKey(ACCOUNT_A), json(ACCOUNT_A, TOKEN_A));
		config.values.put("linkCode", LINK_CODE);

		assertNull("the profile copy is not migrated into active use", store.load(ACCOUNT_A));

		assertFalse(config.values.containsKey(FakeConfig.legacyKey(ACCOUNT_A)));
		assertEquals("the Link code setting is a different thing", LINK_CODE, config.values.get("linkCode"));
		assertFalse(Files.exists(fileOf(ACCOUNT_A)));
	}

	@Test
	public void oldProfileEntriesOfOtherAccountsAreSweptToo() throws Exception {
		config.values.put(FakeConfig.legacyKey(ACCOUNT_B), json(ACCOUNT_B, TOKEN_B));
		config.values.put(FakeConfig.legacyKey(ACCOUNT_C), json(ACCOUNT_C, TOKEN_NEW));

		store.load(ACCOUNT_A);

		assertTrue(config.values.isEmpty());
	}

	@Test
	public void savingOverAnOldProfileEntryRemovesIt() throws Exception {
		config.values.put(FakeConfig.legacyKey(ACCOUNT_A), json(ACCOUNT_A, TOKEN_A));
		store.save(credential(ACCOUNT_A, TOKEN_NEW));
		assertFalse(config.values.containsKey(FakeConfig.legacyKey(ACCOUNT_A)));
		assertEquals(TOKEN_NEW, store.load(ACCOUNT_A).getToken());
	}

	// ------------------------------------------------------------------ permissions

	@Test
	public void theFolderAndFilesAreOwnerOnlyOnPosix() throws Exception {
		Assume.assumeTrue(POSIX);
		store.save(credential(ACCOUNT_A, TOKEN_A));
		store.save(credential(ACCOUNT_A, TOKEN_NEW)); // replacing must keep it private too

		assertEquals(PosixFilePermissions.fromString("rwx------"), modeOf(folder));
		assertEquals(PosixFilePermissions.fromString("rw-------"), modeOf(fileOf(ACCOUNT_A)));
	}

	@Test
	public void aFolderThatAlreadyExistsWiderIsTightened() throws Exception {
		Assume.assumeTrue(POSIX);
		Files.createDirectories(folder);
		Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("rwxr-xr-x"));

		store.save(credential(ACCOUNT_A, TOKEN_A));

		assertEquals(PosixFilePermissions.fromString("rwx------"), modeOf(folder));
		assertEquals(PosixFilePermissions.fromString("rw-------"), modeOf(fileOf(ACCOUNT_A)));
	}

	@Test
	public void aStaleWideTempFileOfOursIsCleanedUpButAFreshOneIsNotTouched() throws Exception {
		store.save(credential(ACCOUNT_A, TOKEN_A));
		Path stale = folder.resolve("account-" + ACCOUNT_A + ".11111111-1111-1111-1111-111111111111.tmp");
		Path fresh = folder.resolve("account-" + ACCOUNT_A + ".22222222-2222-2222-2222-222222222222.tmp");
		write(stale, "left by a crash");
		write(fresh, "being written by another client");
		Files.setLastModifiedTime(stale, FileTime.fromMillis(System.currentTimeMillis() - Duration.ofHours(1).toMillis()));

		store.save(credential(ACCOUNT_A, TOKEN_NEW));

		assertFalse(Files.exists(stale));
		assertTrue(Files.exists(fresh));
	}

	// ------------------------------------------------------------------ symlinks and odd files

	private void symlink(Path link, Path target) throws IOException {
		try {
			Files.createDirectories(link.getParent());
			Files.createSymbolicLink(link, target);
		} catch (UnsupportedOperationException | java.nio.file.FileSystemException e) {
			Assume.assumeNoException("symbolic links are not available here", e);
		}
	}

	@Test
	public void aSymlinkedCredentialFileIsRefusedAndNeverFollowed() throws Exception {
		Path elsewhere = temp.newFile("elsewhere.json").toPath();
		Files.write(elsewhere, json(ACCOUNT_A, TOKEN_A).getBytes(StandardCharsets.UTF_8));
		symlink(fileOf(ACCOUNT_A), elsewhere);

		assertNull("a perfectly valid credential behind a link is not read", store.load(ACCOUNT_A));
		store.deleteIfToken(ACCOUNT_A, TOKEN_A);

		assertTrue("the link was left alone", Files.isSymbolicLink(fileOf(ACCOUNT_A)));
		assertEquals("and so was its target", json(ACCOUNT_A, TOKEN_A), new String(Files.readAllBytes(elsewhere), StandardCharsets.UTF_8));
	}

	@Test
	public void savingOverASymlinkReplacesTheLinkAndNeverWritesThroughIt() throws Exception {
		Path elsewhere = temp.newFile("victim.txt").toPath();
		Files.write(elsewhere, "precious".getBytes(StandardCharsets.UTF_8));
		symlink(fileOf(ACCOUNT_A), elsewhere);

		store.save(credential(ACCOUNT_A, TOKEN_A));

		assertEquals("precious", new String(Files.readAllBytes(elsewhere), StandardCharsets.UTF_8));
		assertFalse(Files.isSymbolicLink(fileOf(ACCOUNT_A)));
		assertEquals(TOKEN_A, store.load(ACCOUNT_A).getToken());
	}

	@Test
	public void aSymlinkedFolderIsRefusedForReadingAndWriting() throws Exception {
		Path elsewhere = temp.newFolder("elsewhere").toPath();
		Files.write(elsewhere.resolve("account-" + ACCOUNT_A + ".json"), json(ACCOUNT_A, TOKEN_A).getBytes(StandardCharsets.UTF_8));
		symlink(folder, elsewhere);

		assertNull(store.load(ACCOUNT_A));
		try {
			store.save(credential(ACCOUNT_B, TOKEN_B));
			fail("a credential must not be written into a folder somebody redirected");
		} catch (UncheckedIOException expected) {
			assertFalse("the failure never carries the token", String.valueOf(expected.getMessage()).contains(TOKEN_B));
		}
		assertFalse(Files.exists(elsewhere.resolve("account-" + ACCOUNT_B + ".json")));
		try (Stream<Path> listing = Files.list(elsewhere)) {
			assertEquals("nothing else appeared there", 1, listing.count());
		}
	}

	@Test
	public void somethingThatIsNotAPlainFileIsRefusedAndLeftAlone() throws Exception {
		Files.createDirectories(fileOf(ACCOUNT_A));

		assertNull(store.load(ACCOUNT_A));
		store.deleteIfToken(ACCOUNT_A, TOKEN_A);

		assertTrue(Files.isDirectory(fileOf(ACCOUNT_A), LinkOption.NOFOLLOW_LINKS));
	}

	@Test
	public void aFailedSaveThrowsLeavesNoTempFileAndNeverTouchesTheConfig() throws Exception {
		Files.createDirectories(folder.getParent());
		Files.write(folder, "i am a file, not a folder".getBytes(StandardCharsets.UTF_8));

		try {
			store.save(credential(ACCOUNT_A, TOKEN_A));
			fail("expected the save to fail");
		} catch (UncheckedIOException expected) {
			// the caller tells the player the link could not be saved
		}
		assertEquals("untouched", "i am a file, not a folder", new String(Files.readAllBytes(folder), StandardCharsets.UTF_8));
		assertTrue(config.writes.isEmpty());
	}

	// ------------------------------------------------------------------ size bound

	private static String paddedTo(String json, int bytes) {
		StringBuilder padded = new StringBuilder(json);
		while (padded.length() < bytes) padded.append(' ');
		return padded.toString();
	}

	@Test
	public void theSizeBoundIsExactlyTwoKiB() throws Exception {
		write(fileOf(ACCOUNT_A), paddedTo(json(ACCOUNT_A, TOKEN_A), CredentialStore.MAX_FILE_BYTES));
		assertNotNull("2048 bytes is fine", store.load(ACCOUNT_A));

		write(fileOf(ACCOUNT_A), paddedTo(json(ACCOUNT_A, TOKEN_A), CredentialStore.MAX_FILE_BYTES + 1));
		assertNull("2049 bytes is not a credential", store.load(ACCOUNT_A));
		assertFalse("and is dropped", Files.exists(fileOf(ACCOUNT_A)));
	}

	@Test(timeout = 20_000)
	public void aHugeFileIsNeverReadIntoMemory() throws Exception {
		Files.createDirectories(folder);
		try (RandomAccessFile huge = new RandomAccessFile(fileOf(ACCOUNT_A).toFile(), "rw")) {
			huge.setLength(32L * 1024 * 1024);
		}

		assertNull(store.load(ACCOUNT_A));
		assertFalse(Files.exists(fileOf(ACCOUNT_A)));
	}

	// ------------------------------------------------------------------ what a file may contain

	@Test
	public void aCredentialStoredForOneAccountNeverLoadsForAnother() throws Exception {
		store.save(credential(ACCOUNT_A, TOKEN_A));
		// A tampered or copied file: A's credential under B's name
		Files.copy(fileOf(ACCOUNT_A), fileOf(ACCOUNT_B));

		assertNull(store.load(ACCOUNT_B));
		assertFalse("the mismatched file is dropped", Files.exists(fileOf(ACCOUNT_B)));
		assertNotNull("the real one is untouched", store.load(ACCOUNT_A));
	}

	@Test
	public void unusableFilesAreDropped() throws Exception {
		for (String junk : new String[] {"not json", "[]", "{}", "{\"token\":\"rp_short\"}", "null", "", "\u0000\u0000"}) {
			write(fileOf(ACCOUNT_A), junk);
			assertNull(junk, store.load(ACCOUNT_A));
			assertFalse(junk, Files.exists(fileOf(ACCOUNT_A)));
		}
	}

	@Test
	public void aTokenWithTheWrongShapeIsDropped() throws Exception {
		write(fileOf(ACCOUNT_A), json(ACCOUNT_A, TOKEN_A.toUpperCase()));
		assertNull(store.load(ACCOUNT_A));
		assertFalse(Files.exists(fileOf(ACCOUNT_A)));
	}

	@Test
	public void expiryIsNotJudgedHereSoAWrongClockCannotEraseAValidToken() throws Exception {
		LinkCredential longGone = LinkCredential.fromData(data(ACCOUNT_A, TOKEN_A, NOW.minus(Duration.ofDays(400))), ACCOUNT_A);
		store.save(longGone);

		LinkCredential loaded = store.load(ACCOUNT_A);

		assertNotNull("handed back for the caller to judge against the best clock it has", loaded);
		assertTrue(loaded.isExpired(NOW));
		assertTrue("and the file is still there", Files.exists(fileOf(ACCOUNT_A)));
	}

	// ------------------------------------------------------------------ conditional delete

	@Test
	public void deleteIfTokenLeavesANewerLinkAlone() throws Exception {
		store.save(credential(ACCOUNT_A, TOKEN_NEW));
		store.deleteIfToken(ACCOUNT_A, TOKEN_A);
		assertNotNull("a newer token replaced the revoked one", store.load(ACCOUNT_A));
		assertEquals(TOKEN_NEW, store.load(ACCOUNT_A).getToken());

		store.deleteIfToken(ACCOUNT_A, TOKEN_NEW);
		assertNull(store.load(ACCOUNT_A));
		assertFalse(Files.exists(fileOf(ACCOUNT_A)));
	}

	@Test
	public void deleteIfTokenNeedsTheExactTokenNotAFragmentOfIt() throws Exception {
		store.save(credential(ACCOUNT_A, TOKEN_A));
		for (String almost : new String[] {TOKEN_A.substring(3), TOKEN_A.substring(0, 20), TOKEN_A + "0", "", "rp_", TOKEN_A.toUpperCase()}) {
			store.deleteIfToken(ACCOUNT_A, almost);
			assertTrue("[" + almost + "]", Files.exists(fileOf(ACCOUNT_A)));
		}
		store.deleteIfToken(ACCOUNT_A, TOKEN_A);
		assertFalse(Files.exists(fileOf(ACCOUNT_A)));
	}

	@Test
	public void deleteIfTokenOnlyTouchesItsOwnAccount() throws Exception {
		store.save(credential(ACCOUNT_A, TOKEN_A));
		store.save(credential(ACCOUNT_B, TOKEN_B));

		store.deleteIfToken(ACCOUNT_B, TOKEN_A);

		assertNotNull(store.load(ACCOUNT_A));
		assertNotNull(store.load(ACCOUNT_B));
	}

	@Test
	public void deleteIfTokenForNothingIsHarmless() {
		store.deleteIfToken(ACCOUNT_A, TOKEN_A);
		assertNull(store.load(ACCOUNT_A));
	}

	// ------------------------------------------------------------------ the folder lock (two clients, one folder)

	/** Another client changing the folder: an exclusive OS lock on the lock file. Closing the channel lets go. */
	private FileChannel anotherClientHoldsTheLock() throws IOException {
		Files.createDirectories(folder);
		FileChannel channel = FileChannel.open(folder.resolve(CredentialStore.LOCK_FILE), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
		channel.lock();
		return channel;
	}

	private CredentialStore impatientStore() {
		return new CredentialStore(config.manager, new Gson(), folder, 250);
	}

	@Test
	public void theLockFileIsOwnerOnlyEmptyAndNeverRemoved() throws Exception {
		store.save(credential(ACCOUNT_A, TOKEN_A));
		Path lock = folder.resolve(CredentialStore.LOCK_FILE);

		assertTrue(Files.isRegularFile(lock, LinkOption.NOFOLLOW_LINKS));
		assertEquals("it holds nothing", 0, Files.size(lock));
		if (POSIX) assertEquals(PosixFilePermissions.fromString("rw-------"), modeOf(lock));
		store.deleteIfToken(ACCOUNT_A, TOKEN_A);
		store.load(ACCOUNT_A);
		assertTrue("removing it would let two clients lock two different files", Files.exists(lock));
	}

	@Test
	public void aRejectedTokenIsNotDeletedWhileAnotherClientIsChangingTheFolder() throws Exception {
		store.save(credential(ACCOUNT_A, TOKEN_A));
		CredentialStore impatient = impatientStore();

		try (FileChannel other = anotherClientHoldsTheLock()) {
			long start = System.nanoTime();
			impatient.deleteIfToken(ACCOUNT_A, TOKEN_A);

			assertTrue("it waited for the other client", System.nanoTime() - start >= TimeUnit.MILLISECONDS.toNanos(200));
			assertTrue("and then left the file alone rather than risk a newer token", Files.exists(fileOf(ACCOUNT_A)));
		}

		impatient.deleteIfToken(ACCOUNT_A, TOKEN_A);
		assertFalse("once the other client is done it is a normal delete", Files.exists(fileOf(ACCOUNT_A)));
	}

	@Test
	public void aDeleteWaitsForAClientThatIsReplacingTheTokenAndThenLeavesTheNewOneAlone() throws Exception {
		store.save(credential(ACCOUNT_A, TOKEN_A));
		CredentialStore second = new CredentialStore(new FakeConfig().manager, new Gson(), folder);
		ExecutorService pool = Executors.newSingleThreadExecutor();
		try (FileChannel other = anotherClientHoldsTheLock()) {
			Future<?> delete = pool.submit(() -> second.deleteIfToken(ACCOUNT_A, TOKEN_A));

			Thread.sleep(300);
			assertFalse("blocked by the lock, not racing ahead of the other client", delete.isDone());
			assertEquals(TOKEN_A, store.load(ACCOUNT_A).getToken());

			// the other client finishes its pairing: new token in place, then it lets go
			Path replacement = folder.resolve("account-" + ACCOUNT_A + ".other.tmp");
			Files.write(replacement, json(ACCOUNT_A, TOKEN_NEW).getBytes(StandardCharsets.UTF_8));
			Files.move(replacement, fileOf(ACCOUNT_A), java.nio.file.StandardCopyOption.ATOMIC_MOVE);
			other.close();

			delete.get(10, TimeUnit.SECONDS);
		} finally {
			pool.shutdownNow();
		}

		assertEquals("the revoked token's cleanup did not take the new one with it", TOKEN_NEW, store.load(ACCOUNT_A).getToken());
	}

	@Test
	public void aSaveGoesAheadWithoutTheLockWhenAnotherClientKeepsItTooLong() throws Exception {
		CredentialStore impatient = impatientStore();

		try (FileChannel other = anotherClientHoldsTheLock()) {
			long start = System.nanoTime();
			impatient.save(credential(ACCOUNT_A, TOKEN_A));

			assertTrue("it asked for the lock first and only then gave up on it",
				System.nanoTime() - start >= TimeUnit.MILLISECONDS.toNanos(200));
		}

		assertEquals("a fresh credential must never be lost to a stuck neighbour", TOKEN_A, store.load(ACCOUNT_A).getToken());
		assertEquals("and no temp file is left", java.util.Arrays.asList(".lock", "account-" + ACCOUNT_A + ".json"), folderListing());
	}

	@Test
	public void anUnusableFileIsLeftAloneWhileAnotherClientHoldsTheLockAndDroppedAfterwards() throws Exception {
		write(fileOf(ACCOUNT_A), "not a credential");
		CredentialStore impatient = impatientStore();

		try (FileChannel other = anotherClientHoldsTheLock()) {
			assertNull(impatient.load(ACCOUNT_A));
			assertTrue("the other client may be about to put a valid file here", Files.exists(fileOf(ACCOUNT_A)));
		}

		assertNull(impatient.load(ACCOUNT_A));
		assertFalse(Files.exists(fileOf(ACCOUNT_A)));
	}

	@Test
	public void aFileThatBecameValidWhileItWasBeingDiscardedIsNotDiscarded() throws Exception {
		// load() sees junk, but by the time it holds the lock another client has put a good credential there
		write(fileOf(ACCOUNT_A), "not a credential");
		ExecutorService pool = Executors.newSingleThreadExecutor();
		try (FileChannel other = anotherClientHoldsTheLock()) {
			Future<LinkCredential> load = pool.submit(() -> store.load(ACCOUNT_A));
			Thread.sleep(150);
			assertFalse(load.isDone());
			Path replacement = folder.resolve("account-" + ACCOUNT_A + ".other.tmp");
			Files.write(replacement, json(ACCOUNT_A, TOKEN_NEW).getBytes(StandardCharsets.UTF_8));
			Files.move(replacement, fileOf(ACCOUNT_A), java.nio.file.StandardCopyOption.ATOMIC_MOVE);
			other.close();

			LinkCredential loaded = load.get(10, TimeUnit.SECONDS);
			assertNotNull(loaded);
			assertEquals(TOKEN_NEW, loaded.getToken());
		} finally {
			pool.shutdownNow();
		}
		assertTrue(Files.exists(fileOf(ACCOUNT_A)));
	}

	@Test
	public void withoutWorkingLocksEverythingBehavesAsItDidBefore() throws Exception {
		Files.createDirectories(folder.resolve(CredentialStore.LOCK_FILE)); // cannot be opened as a lock file

		store.save(credential(ACCOUNT_A, TOKEN_A));
		assertEquals(TOKEN_A, store.load(ACCOUNT_A).getToken());
		store.deleteIfToken(ACCOUNT_A, TOKEN_NEW);
		assertTrue(Files.exists(fileOf(ACCOUNT_A)));
		store.deleteIfToken(ACCOUNT_A, TOKEN_A);
		assertFalse(Files.exists(fileOf(ACCOUNT_A)));
	}

	@Test
	public void twoStoresSavingAndForgettingAtTheSameTimeNeverLoseTheNewerToken() throws Exception {
		CredentialStore first = new CredentialStore(new FakeConfig().manager, new Gson(), folder);
		CredentialStore second = new CredentialStore(new FakeConfig().manager, new Gson(), folder);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			for (int round = 0; round < 80; round++) {
				first.save(credential(ACCOUNT_A, TOKEN_A));
				CountDownLatch go = new CountDownLatch(1);
				Future<?> save = pool.submit(() -> {
					go.await();
					second.save(credential(ACCOUNT_A, TOKEN_NEW));
					return null;
				});
				Future<?> forget = pool.submit(() -> {
					go.await();
					first.deleteIfToken(ACCOUNT_A, TOKEN_A);
					return null;
				});
				go.countDown();
				save.get(30, TimeUnit.SECONDS);
				forget.get(30, TimeUnit.SECONDS);

				LinkCredential left = first.load(ACCOUNT_A);
				assertNotNull("round " + round + ": the new token was deleted by the old one's cleanup", left);
				assertEquals(TOKEN_NEW, left.getToken());
			}
		} finally {
			pool.shutdownNow();
		}
	}

	/** The real thing: a second operating-system process, as a second RuneLite client would be. */
	@Test
	public void aLockHeldByAnotherProcessIsRespected() throws Exception {
		Path source = temp.newFolder("holder").toPath().resolve("HoldLock.java");
		Files.write(source, ("import java.nio.channels.*; import java.nio.file.*;\n"
			+ "public class HoldLock { public static void main(String[] args) throws Exception {\n"
			+ "  try (FileChannel c = FileChannel.open(Paths.get(args[0]), StandardOpenOption.CREATE, StandardOpenOption.WRITE);\n"
			+ "       FileLock l = c.lock()) { System.out.println(\"locked\"); System.out.flush(); System.in.read(); }\n"
			+ "} }\n").getBytes(StandardCharsets.UTF_8));
		store.save(credential(ACCOUNT_A, TOKEN_A));
		Path java = javaLauncher();
		Assume.assumeTrue("no java launcher to start a second process with", Files.isExecutable(java));

		Process holder = new ProcessBuilder(java.toString(), source.toString(), folder.resolve(CredentialStore.LOCK_FILE).toString())
			.redirectErrorStream(true).start();
		try {
			java.io.BufferedReader out = new java.io.BufferedReader(new java.io.InputStreamReader(holder.getInputStream(), StandardCharsets.UTF_8));
			java.util.concurrent.ExecutorService reader = Executors.newSingleThreadExecutor();
			String first;
			try {
				first = reader.submit(out::readLine).get(60, TimeUnit.SECONDS);
			} finally {
				reader.shutdownNow();
			}
			Assume.assumeTrue("the second process did not start", "locked".equals(first));

			CredentialStore impatient = impatientStore();
			impatient.deleteIfToken(ACCOUNT_A, TOKEN_A);
			assertTrue("another process holds the lock: nothing is deleted", Files.exists(fileOf(ACCOUNT_A)));
			impatient.save(credential(ACCOUNT_A, TOKEN_NEW));
			assertEquals("but a save is never lost", TOKEN_NEW, impatient.load(ACCOUNT_A).getToken());

			holder.getOutputStream().close(); // the other client is done
			assertTrue(holder.waitFor(30, TimeUnit.SECONDS));

			impatient.deleteIfToken(ACCOUNT_A, TOKEN_NEW);
			assertFalse(Files.exists(fileOf(ACCOUNT_A)));
		} finally {
			holder.destroyForcibly();
		}
	}

	private static Path javaLauncher() {
		return java.nio.file.Paths.get(System.getProperty("java.home"), "bin", "java");
	}

	// ------------------------------------------------------------------ the Link code (still a config setting)

	@Test
	public void linkCodeIsClearedOnlyWhileItStillHoldsTheSubmittedValue() {
		config.values.put("linkCode", LINK_CODE);
		store.clearLinkCodeIfUnchanged(repeat('e', 64));
		assertEquals("the player typed something newer meanwhile", LINK_CODE, config.values.get("linkCode"));

		store.clearLinkCodeIfUnchanged(LINK_CODE);
		assertFalse(config.values.containsKey("linkCode"));
	}

	@Test
	public void linkCodeComparisonIgnoresSurroundingWhitespace() {
		config.values.put("linkCode", "  " + LINK_CODE + "\n");
		store.clearLinkCodeIfUnchanged(LINK_CODE);
		assertFalse(config.values.containsKey("linkCode"));
	}

	@Test
	public void configuredLinkCodeIsEmptyWhenUnset() {
		assertEquals("", store.configuredLinkCode());
	}
}
