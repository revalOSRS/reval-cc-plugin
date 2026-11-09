package com.revalclan.events;

import com.google.gson.Gson;
import com.revalclan.auth.TestKit.Rig;
import com.revalclan.api.RevalApiService;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class RegistrationMarksTest {
	private Rig rig;
	@org.junit.Before public void openRig() {
		rig = new Rig();
		rig.link(123L, com.revalclan.auth.TestKit.TOKEN_A);
		rig.tokens.onAccount(123L);
	}
	@org.junit.After public void closeRig() throws Exception { rig.close(); }

	@Test public void resetCannotBeOvertakenByAnAlreadyStartedEventPublication() throws Exception {
		ConcurrentLinkedQueue<Runnable> clientTasks = new ConcurrentLinkedQueue<>();
		ClientThread clientThread = mock(ClientThread.class);
		doAnswer(invocation -> { clientTasks.add(invocation.getArgument(0)); return null; })
			.when(clientThread).invokeLater(any(Runnable.class));
		OkHttpClient http = mock(OkHttpClient.class);
		Call call = mock(Call.class);
		when(http.newCall(any(Request.class))).thenReturn(call);
		RevalApiService api = new RevalApiService(http, new Gson(), rig.tokens, clientThread);
		CountDownLatch publishing = new CountDownLatch(1);
		CountDownLatch resume = new CountDownLatch(1);
		api.addEventsListener(response -> {
			assertFalse("Listeners must not hold the API service monitor", Thread.holdsLock(api));
			if (response.getData() == null) return;
			publishing.countDown();
			try {
				assertTrue("Publication was never resumed", resume.await(5, TimeUnit.SECONDS));
			} catch (InterruptedException error) {
				throw new AssertionError(error);
			}
		});
		RegistrationMarks marks = new RegistrationMarks(mock(Client.class), api);
		api.fetchEvents(response -> {}, error -> {});
		runClientTasks(clientTasks);
		ArgumentCaptor<Callback> callback = ArgumentCaptor.forClass(Callback.class);
		verify(call).enqueue(callback.capture());
		AtomicReference<Throwable> threadError = new AtomicReference<>();
		Thread publication = new Thread(() -> {
			try {
				callback.getValue().onResponse(call, new Response.Builder()
					.request(new Request.Builder().url("https://example.invalid").build())
					.protocol(Protocol.HTTP_1_1).code(200).message("OK")
					.body(ResponseBody.create(null, "{\"status\":\"success\",\"data\":{\"events\":[{\"id\":\"old\",\"name\":\"Previous account event\",\"status\":\"scheduled\",\"registrations\":[{\"osrsNickname\":\"Alice\",\"status\":\"registered\"}]}]}}"))
					.build());
				runClientTasks(clientTasks);
			} catch (Throwable error) {
				threadError.set(error);
			}
		});
		publication.start();
		try {
			assertTrue("Response never reached the event listeners", publishing.await(5, TimeUnit.SECONDS));
			api.resetEventsSession();
		} finally {
			resume.countDown();
			publication.join(5000);
		}
		assertFalse("Event publication remained blocked", publication.isAlive());
		if (threadError.get() != null) throw new AssertionError(threadError.get());
		runClientTasks(clientTasks);
		assertTrue("The previous account restored its registration marks after reset", marks.getRegistrations().isEmpty());
	}

	private static void runClientTasks(ConcurrentLinkedQueue<Runnable> tasks) {
		Runnable task;
		while ((task = tasks.poll()) != null) task.run();
	}
}
