package com.tobutilities.verzik;

import com.tobutilities.TobUtilitiesConfig;
import com.tobutilities.TobUtilitiesPlugin;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.party.PartyService;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class VerzikHandlerTest
{
	private VerzikHandler handler;
	private Client client;
	private TobUtilitiesConfig config;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		config = mock(TobUtilitiesConfig.class);
		when(config.preserveVerzikEntryCamera()).thenReturn(true);
		when(config.enableDawnbringerOverlay()).thenReturn(false);
		when(config.enableLightbearerOverlay()).thenReturn(false);

		handler = new VerzikHandler(mock(TobUtilitiesPlugin.class), config, client);
		com.tobutilities.TestUtils.setField(handler, "partyService", mock(PartyService.class));
	}

	@Test
	public void preservesEntryCameraWithSingleRestoreTick()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getCameraYawTarget()).thenReturn(512);
		when(client.getCameraPitchTarget()).thenReturn(240);

		handler.captureEntryCameraTargets();
		handler.onRoomEntry();
		handler.onGameTick(null);

		verify(client, org.mockito.Mockito.times(1)).setCameraYawTarget(512);
		verify(client, org.mockito.Mockito.times(1)).setCameraPitchTarget(240);
	}

	@Test
	public void restoresCameraImmediatelyOnRoomEntry()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getCameraYawTarget()).thenReturn(700);
		when(client.getCameraPitchTarget()).thenReturn(260);

		handler.captureEntryCameraTargets();
		handler.onRoomEntry();

		verify(client, org.mockito.Mockito.times(1)).setCameraYawTarget(700);
		verify(client, org.mockito.Mockito.times(1)).setCameraPitchTarget(260);
	}

	@Test
	public void loggedInStateChangeDoesNotRetriggerCameraRestore()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getCameraYawTarget()).thenReturn(640);
		when(client.getCameraPitchTarget()).thenReturn(220);

		handler.captureEntryCameraTargets();
		handler.onRoomEntry();
		handler.onGameTick(null);

		clearInvocations(client);

		GameStateChanged event = new GameStateChanged();
		event.setGameState(GameState.LOGGED_IN);
		handler.onGameStateChanged(event);
		handler.onGameTick(null);

		verify(client, never()).setCameraYawTarget(640);
		verify(client, never()).setCameraPitchTarget(220);
	}

	@Test
	public void loginScreenClearsSavedCameraSnapshot()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getCameraYawTarget()).thenReturn(256);
		when(client.getCameraPitchTarget()).thenReturn(128);

		handler.captureEntryCameraTargets();

		GameStateChanged event = new GameStateChanged();
		event.setGameState(GameState.LOGIN_SCREEN);
		handler.onGameStateChanged(event);

		clearInvocations(client);
		handler.onRoomEntry();
		handler.onGameTick(null);

		verify(client, never()).setCameraYawTarget(256);
		verify(client, never()).setCameraPitchTarget(128);
	}
}
