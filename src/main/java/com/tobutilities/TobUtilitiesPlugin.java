package com.tobutilities;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Provides;
import com.tobutilities.bloat.BloatConstants;
import com.tobutilities.bloat.BloatHandler;
import com.tobutilities.bloat.BloatPlayerOverlay;
import com.tobutilities.common.metronome.MetronomeService;
import com.tobutilities.common.metronome.MetronomeOverlay;
import com.tobutilities.common.enums.Region;
import com.tobutilities.common.util.CommonUtils;
import com.tobutilities.maiden.MaidenHandler;
import com.tobutilities.maiden.ScuffWarningOverlay;
import com.tobutilities.maiden.ScuffedNylocasOverlay;
import com.tobutilities.common.player.PlayerOneOrbOverlay;
import com.tobutilities.common.player.PlayerTwoOrbOverlay;
import com.tobutilities.common.player.PlayerThreeOrbOverlay;
import com.tobutilities.common.player.PlayerFourOrbOverlay;
import com.tobutilities.common.player.PlayerFiveOrbOverlay;
import com.tobutilities.nylocas.NylocasHandler;
import com.tobutilities.nylocas.NylocasOverlay;
import com.tobutilities.verzik.DawnbringerStatusMessage;
import com.tobutilities.verzik.LightbearerWarningOverlay;
import com.tobutilities.verzik.VerzikHandler;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.Hooks;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.party.WSClient;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.HotkeyListener;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.util.Arrays;

@PluginDescriptor(
	name = "ToB Utilities",
	description = "Various tools for the theatre of blood",
	tags = {"timers", "overlays", "tick", "theatre", "metronome", "tob", "maiden", "bloat", "nylo", "xarpus", "verzik"}
)
@Slf4j
public class TobUtilitiesPlugin extends Plugin
{
	@Inject
	private OverlayManager overlayManager;
	@Inject
	public InfoBoxManager infoBoxManager;
	@Inject
	public ConfigManager configManager;
	@Inject
	private Client client;
	@Inject
	private MetronomeOverlay metronomeOverlay;
	@Inject
	private PlayerOneOrbOverlay playerOneOrbOverlay;
	@Inject
	private PlayerTwoOrbOverlay playerTwoOrbOverlay;
	@Inject
	private PlayerThreeOrbOverlay playerThreeOrbOverlay;
	@Inject
	private PlayerFourOrbOverlay playerFourOrbOverlay;
	@Inject
	private PlayerFiveOrbOverlay playerFiveOrbOverlay;
	@Inject
	private ScuffedNylocasOverlay scuffedNylocasOverlay;
	@Inject
	private ScuffWarningOverlay scuffWarningOverlay;
	@Inject
	private BloatPlayerOverlay bloatPlayerOverlay;
	@Inject
	private LightbearerWarningOverlay lightbearerWarningOverlay;
	@Inject
	private NylocasOverlay nylocasOverlay;
	@Inject
	private TobUtilitiesConfig config;
	@Inject
	private KeyManager keyManager;
	@Inject
	private Hooks hooks;
	@Inject
	private MaidenHandler maidenHandler;
	@Inject
	private BloatHandler bloatHandler;
	@Inject
	private NylocasHandler nylocasHandler;

	@Inject
	private VerzikHandler verzikHandler;
	@Inject
	private MetronomeService metronomeService;
	@Inject
	private WSClient wsClient;

	@Inject
	private RenderCallbackManager renderCallbackManager;

	@Inject
	private ClientThread clientThread;


	public Region region = Region.UNKNOWN;
	private final Hooks.RenderableDrawListener drawListener = this::shouldDraw;

	private final RenderCallback renderCallback = new RenderCallback() {

		@Override
		public boolean addEntity(Renderable renderable, boolean drawingUi) {
			// Need to calculate the region in this call instead of using the shared one updated by the metronome because
			// this will get called before onGameTick, so the region will be incorrect when walking into a new room.
			Region localRegion = CommonUtils.getRegionByRegionId(CommonUtils.getRegionID(client));

			if (localRegion.equals(Region.BLOAT)) {
				return bloatHandler.addEntity(renderable, drawingUi);
			}

			return RenderCallback.super.addEntity(renderable, drawingUi);
		}
	};

	@Provides
	TobUtilitiesConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TobUtilitiesConfig.class);
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		Region oldRegion = region;
		Region currentRegion = CommonUtils.getRegionByRegionId(CommonUtils.getRegionID(client));
		if (!currentRegion.equals(Region.VERZIK))
		{
			verzikHandler.captureEntryCameraTargets();
		}
        // metronomeService updates region
		metronomeService.onGameTick(tick);
		if (!oldRegion.equals(Region.BLOAT) && region.equals(Region.BLOAT)) {
			bloatHandler.onRoomEntry();
		}
		if (oldRegion.equals(Region.BLOAT) && !region.equals(Region.BLOAT))
		{
			bloatHandler.onRoomExit();
		}
		if (!oldRegion.equals(Region.VERZIK) && region.equals(Region.VERZIK))
		{
			verzikHandler.onRoomEntry();
		}
		if (oldRegion.equals(Region.VERZIK) && !region.equals(Region.VERZIK))
		{
			verzikHandler.onRoomExit();
		}
		if (region.equals(Region.MAIDEN))
		{
			maidenHandler.onGameTick(tick);
		}
		else if (region.equals(Region.VERZIK))
		{
			verzikHandler.onGameTick(tick);
		}
		else if (region.equals(Region.BLOAT))
		{
			bloatHandler.onGameTick(tick);
		}

		syncTobHealthBarVisibility();
	}


	private final HotkeyListener hideVerzikHotkeyListener = new HotkeyListener(() -> config.hideVerzikHotkey())
	{
		@Override
		public void keyPressed(KeyEvent e)
		{
			if (Region.VERZIK.equals(region))
			{
				verzikHandler.keyPressed(e);
			}
		}

		@Override
		public void keyReleased(KeyEvent e)
		{
			hotkeyReleased();
		}
	};

	private final HotkeyListener metronomeResetHotkeyListener = new HotkeyListener(() -> config.metronomeResetHotkey())
	{
		@Override
		public void keyPressed(KeyEvent e)
		{
			metronomeService.keyPressed(e);
		}

		@Override
		public void keyReleased(KeyEvent e)
		{
			hotkeyReleased();
		}
	};

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		if (Region.MAIDEN.equals(region))
		{
			maidenHandler.onNpcSpawned(event);
		}
		else if (Region.NYLOCAS.equals(region))
		{
			nylocasHandler.onNpcSpawned(event);
		}
	}


	@VisibleForTesting
	boolean shouldDraw(Renderable renderable, boolean drawingUI)
	{
		if (Region.VERZIK.equals(region))
		{
			return verzikHandler.shouldDraw(renderable, drawingUI);
		}
		return true;
	}


	@Subscribe
	void onActorDeath(ActorDeath event)
	{
		if (Region.NYLOCAS.equals(region))
		{
			nylocasHandler.onActorDeath(event);
		}
	}

	@Subscribe
	public void onNpcChanged(NpcChanged event)
	{
		if (Region.NYLOCAS.equals(region))
		{
			nylocasHandler.onNpcChanged(event);
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		if (Region.MAIDEN.equals(region))
		{
			maidenHandler.onNpcDespawned(event);
		}
		else if (Region.NYLOCAS.equals(region))
		{
			nylocasHandler.onNpcDespawned(event);
		}
	}

	@Subscribe
	public void onDawnbringerStatusMessage(DawnbringerStatusMessage message)
	{
		verzikHandler.onDawnbringerStatusMessage(message);
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (Region.VERZIK.equals(region)){
			verzikHandler.onItemContainerChanged(event);
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == (InterfaceID.TobHud.PROGRESS_CONTAINER >>> 16))
		{
			syncTobHealthBarVisibility();
		}
	}

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
		if (Region.BLOAT.equals(region)) {
			bloatHandler.onConfigChanged(event);
		}

		if ("tobutilities".equals(event.getGroup()) && "hideVanillaTobHealthBar".equals(event.getKey()))
		{
			syncTobHealthBarVisibility();
		}
	}

	@Subscribe
	public void onPreMapLoad(PreMapLoad event)
	{
		if (Arrays.stream(event.getScene().getMapRegions())
			.anyMatch(id -> id == Region.BLOAT.getRegionId())) {
			bloatHandler.onPreMapLoad(event);
		}
	}

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
		verzikHandler.onGameStateChanged(event);
    }

	private void syncTobHealthBarVisibility()
	{
		Widget tobHealthBar = client.getWidget(InterfaceID.TobHud.PROGRESS_CONTAINER);
		if (tobHealthBar == null)
		{
			return;
		}

		boolean shouldHide = config.hideVanillaTobHealthBar();
		if (tobHealthBar.isHidden() != shouldHide)
		{
			tobHealthBar.setHidden(shouldHide);
		}
	}

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(metronomeOverlay);
		overlayManager.add(playerOneOrbOverlay);
		overlayManager.add(playerTwoOrbOverlay);
		overlayManager.add(playerThreeOrbOverlay);
		overlayManager.add(playerFourOrbOverlay);
		overlayManager.add(playerFiveOrbOverlay);
		overlayManager.add(scuffedNylocasOverlay);
		overlayManager.add(scuffWarningOverlay);
		overlayManager.add(bloatPlayerOverlay);
		overlayManager.add(lightbearerWarningOverlay);
		overlayManager.add(nylocasOverlay);
        bloatHandler.startUp();
		verzikHandler.startUp();
		wsClient.registerMessage(DawnbringerStatusMessage.class);
		keyManager.registerKeyListener(hideVerzikHotkeyListener);
		keyManager.registerKeyListener(metronomeResetHotkeyListener);
		hooks.registerRenderableDrawListener(drawListener);
		renderCallbackManager.register(renderCallback);

		clientThread.invokeLater(this::tryReloadScene);
		clientThread.invokeLater(this::syncTobHealthBarVisibility);
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(metronomeOverlay);
		overlayManager.remove(playerOneOrbOverlay);
		overlayManager.remove(playerTwoOrbOverlay);
		overlayManager.remove(playerThreeOrbOverlay);
		overlayManager.remove(playerFourOrbOverlay);
		overlayManager.remove(playerFiveOrbOverlay);
		overlayManager.remove(scuffedNylocasOverlay);
		overlayManager.remove(scuffWarningOverlay);
		overlayManager.remove(bloatPlayerOverlay);
		overlayManager.remove(lightbearerWarningOverlay);
		overlayManager.remove(nylocasOverlay);
		nylocasHandler.shutDown();
		maidenHandler.shutDown();
        bloatHandler.shutDown();
		verzikHandler.shutDown();
		wsClient.unregisterMessage(DawnbringerStatusMessage.class);
		keyManager.unregisterKeyListener(hideVerzikHotkeyListener);
		keyManager.unregisterKeyListener(metronomeResetHotkeyListener);
		hooks.unregisterRenderableDrawListener(drawListener);
		renderCallbackManager.unregister(renderCallback);

		clientThread.invokeLater(this::tryReloadScene);
		clientThread.invokeLater(this::syncTobHealthBarVisibility);
	}

	private void tryReloadScene() {
		assert client.isClientThread();
		if (client.getGameState() == GameState.LOGGED_IN)
			client.setGameState(GameState.LOADING);
	}
}
