package com.tobutilities;

import com.tobutilities.bloat.BloatHandler;
import com.tobutilities.common.enums.Region;
import com.tobutilities.common.metronome.MetronomeService;
import com.tobutilities.common.util.CommonUtils;
import com.tobutilities.maiden.MaidenHandler;
import com.tobutilities.nylocas.NylocasHandler;
import com.tobutilities.verzik.VerzikHandler;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TobUtilitiesPluginBehaviorTest
{
	private TobUtilitiesPlugin plugin;
	private Client client;
	private ClientThread clientThread;
	private TobUtilitiesConfig config;
	private MetronomeService metronomeService;
	private BloatHandler bloatHandler;
	private VerzikHandler verzikHandler;
	private Widget tobHealthBarWidget;

	@Before
	public void setUp()
	{
		plugin = new TobUtilitiesPlugin();
		client = mock(Client.class);
		clientThread = mock(ClientThread.class);
		config = mock(TobUtilitiesConfig.class);
		metronomeService = mock(MetronomeService.class);
		bloatHandler = mock(BloatHandler.class);
		verzikHandler = mock(VerzikHandler.class);
		tobHealthBarWidget = mock(Widget.class);

		TestUtils.setField(plugin, "client", client);
		TestUtils.setField(plugin, "clientThread", clientThread);
		TestUtils.setField(plugin, "config", config);
		TestUtils.setField(plugin, "metronomeService", metronomeService);
		TestUtils.setField(plugin, "bloatHandler", bloatHandler);
		TestUtils.setField(plugin, "verzikHandler", verzikHandler);
		TestUtils.setField(plugin, "maidenHandler", mock(MaidenHandler.class));
		TestUtils.setField(plugin, "nylocasHandler", mock(NylocasHandler.class));
		when(client.getWidget(InterfaceID.TobHud.PROGRESS_CONTAINER)).thenReturn(tobHealthBarWidget);
	}

	@Test
	public void enteringBloatReloadsSceneWhenFloorHideEnabled()
	{
		plugin.region = Region.UNKNOWN;
		when(config.hideBloatFloor()).thenReturn(true);
		doAnswer(invocation ->
		{
			plugin.region = Region.BLOAT;
			return null;
		}).when(metronomeService).onGameTick(any(GameTick.class));

		try (MockedStatic<CommonUtils> commonUtils = Mockito.mockStatic(CommonUtils.class))
		{
			commonUtils.when(() -> CommonUtils.getRegionID(client)).thenReturn(0);
			commonUtils.when(() -> CommonUtils.getRegionByRegionId(0)).thenReturn(Region.UNKNOWN);

			plugin.onGameTick(mock(GameTick.class));
		}

		verify(verzikHandler).captureEntryCameraTargets();
		verify(bloatHandler).onRoomEntry();
		verify(clientThread).invokeLater(any(Runnable.class));
	}

	@Test
	public void leavingBloatReloadsSceneWhenFloorHideEnabled()
	{
		plugin.region = Region.BLOAT;
		when(config.hideBloatFloor()).thenReturn(true);
		doAnswer(invocation ->
		{
			plugin.region = Region.UNKNOWN;
			return null;
		}).when(metronomeService).onGameTick(any(GameTick.class));

		try (MockedStatic<CommonUtils> commonUtils = Mockito.mockStatic(CommonUtils.class))
		{
			commonUtils.when(() -> CommonUtils.getRegionID(client)).thenReturn(0);
			commonUtils.when(() -> CommonUtils.getRegionByRegionId(0)).thenReturn(Region.UNKNOWN);

			plugin.onGameTick(mock(GameTick.class));
		}

		verify(verzikHandler).captureEntryCameraTargets();
		verify(bloatHandler).onRoomExit();
		verify(clientThread).invokeLater(any(Runnable.class));
	}

	@Test
	public void enteringBloatSkipsReloadWhenFloorHideDisabled()
	{
		plugin.region = Region.UNKNOWN;
		when(config.hideBloatFloor()).thenReturn(false);
		doAnswer(invocation ->
		{
			plugin.region = Region.BLOAT;
			return null;
		}).when(metronomeService).onGameTick(any(GameTick.class));

		try (MockedStatic<CommonUtils> commonUtils = Mockito.mockStatic(CommonUtils.class))
		{
			commonUtils.when(() -> CommonUtils.getRegionID(client)).thenReturn(0);
			commonUtils.when(() -> CommonUtils.getRegionByRegionId(0)).thenReturn(Region.UNKNOWN);

			plugin.onGameTick(mock(GameTick.class));
		}

		verify(bloatHandler).onRoomEntry();
		verify(clientThread, never()).invokeLater(any(Runnable.class));
	}

	@Test
	public void verzikRoomTransitionsNotifyHandler()
	{
		plugin.region = Region.UNKNOWN;
		doAnswer(invocation ->
		{
			plugin.region = Region.VERZIK;
			return null;
		}).when(metronomeService).onGameTick(any(GameTick.class));

		try (MockedStatic<CommonUtils> commonUtils = Mockito.mockStatic(CommonUtils.class))
		{
			commonUtils.when(() -> CommonUtils.getRegionID(client)).thenReturn(12611);
			commonUtils.when(() -> CommonUtils.getRegionByRegionId(12611)).thenReturn(Region.VERZIK);

			plugin.onGameTick(mock(GameTick.class));
		}

		verify(verzikHandler).onRoomEntry();
	}

	@Test
	public void hidesVanillaTobHealthBarInsideTobWhenEnabled()
	{
		plugin.region = Region.UNKNOWN;
		when(config.hideVanillaTobHealthBar()).thenReturn(true);
		when(tobHealthBarWidget.isHidden()).thenReturn(false);
		doAnswer(invocation ->
		{
			plugin.region = Region.VERZIK;
			return null;
		}).when(metronomeService).onGameTick(any(GameTick.class));

		try (MockedStatic<CommonUtils> commonUtils = Mockito.mockStatic(CommonUtils.class))
		{
			commonUtils.when(() -> CommonUtils.getRegionID(client)).thenReturn(12611);
			commonUtils.when(() -> CommonUtils.getRegionByRegionId(12611)).thenReturn(Region.VERZIK);

			plugin.onGameTick(mock(GameTick.class));
		}

		verify(tobHealthBarWidget).setHidden(true);
	}

	@Test
	public void doesNotRewriteHealthBarVisibilityWhenAlreadyHidden()
	{
		plugin.region = Region.VERZIK;
		when(config.hideVanillaTobHealthBar()).thenReturn(true);
		when(tobHealthBarWidget.isHidden()).thenReturn(true);

		doAnswer(invocation ->
		{
			plugin.region = Region.VERZIK;
			return null;
		}).when(metronomeService).onGameTick(any(GameTick.class));

		try (MockedStatic<CommonUtils> commonUtils = Mockito.mockStatic(CommonUtils.class))
		{
			commonUtils.when(() -> CommonUtils.getRegionID(client)).thenReturn(12611);
			commonUtils.when(() -> CommonUtils.getRegionByRegionId(12611)).thenReturn(Region.VERZIK);

			plugin.onGameTick(mock(GameTick.class));
		}

		verify(tobHealthBarWidget, never()).setHidden(anyBoolean());
	}

	@Test
	public void hidesHealthBarOnTobWidgetLoadBeforeRegionUpdate()
	{
		plugin.region = Region.UNKNOWN;
		when(config.hideVanillaTobHealthBar()).thenReturn(true);
		when(tobHealthBarWidget.isHidden()).thenReturn(false);

		WidgetLoaded widgetLoaded = mock(WidgetLoaded.class);
		when(widgetLoaded.getGroupId()).thenReturn(InterfaceID.TobHud.PROGRESS_CONTAINER >>> 16);

		plugin.onWidgetLoaded(widgetLoaded);

		verify(tobHealthBarWidget).setHidden(true);
	}
}
