package eu.ha3.mc.presencefootsteps.game.system;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Scanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

import org.apache.commons.lang3.ArrayUtils;
import org.lwjgl.input.Keyboard;

import eu.ha3.easy.EdgeModel;
import eu.ha3.easy.EdgeTrigger;
import eu.ha3.mc.convenience.Ha3HoldActions;
import eu.ha3.mc.convenience.Ha3KeyHolding;
import eu.ha3.mc.convenience.Ha3KeyManager;
import eu.ha3.mc.convenience.Ha3StaticUtilities;
import eu.ha3.mc.haddon.Identity;
import eu.ha3.mc.haddon.OperatorCaster;
import eu.ha3.mc.haddon.implem.HaddonIdentity;
import eu.ha3.mc.haddon.implem.HaddonImpl;
import eu.ha3.mc.haddon.supporting.SupportsFrameEvents;
import eu.ha3.mc.haddon.supporting.SupportsKeyEvents;
import eu.ha3.mc.haddon.supporting.SupportsTickEvents;
import eu.ha3.mc.presencefootsteps.game.user.PFGuiMenu;
import eu.ha3.mc.presencefootsteps.log.PFLog;
import eu.ha3.mc.presencefootsteps.mcpackage.implem.AcousticsManager;
import eu.ha3.mc.presencefootsteps.mcpackage.implem.BasicPrimitiveMap;
import eu.ha3.mc.presencefootsteps.mcpackage.implem.LegacyCapableBlockMap;
import eu.ha3.mc.presencefootsteps.mcpackage.implem.NormalVariator;
import eu.ha3.mc.presencefootsteps.mcpackage.interfaces.BlockMap;
import eu.ha3.mc.presencefootsteps.mcpackage.interfaces.PrimitiveMap;
import eu.ha3.mc.presencefootsteps.mcpackage.interfaces.Variator;
import eu.ha3.mc.presencefootsteps.parsers.JasonAcoustics_Engine0;
import eu.ha3.mc.presencefootsteps.parsers.PropertyBlockMap_Engine0;
import eu.ha3.mc.presencefootsteps.parsers.PropertyPrimitiveMap_Engine0;
import eu.ha3.mc.quick.chat.ChatColorsSimple;
import eu.ha3.mc.quick.chat.Chatter;
import eu.ha3.mc.quick.keys.KeyWatcher;
import eu.ha3.mc.quick.update.NotifiableHaddon;
import eu.ha3.mc.quick.update.UpdateNotifier;
import eu.ha3.util.property.simple.ConfigProperty;
import eu.ha3.util.property.simple.InputStreamConfigProperty;

/* x-placeholder-wtfplv2 */

public class PFHaddon extends HaddonImpl implements SupportsFrameEvents, SupportsTickEvents, IResourceManagerReloadListener, NotifiableHaddon, Ha3HoldActions, SupportsKeyEvents {
	// Identity
	protected final String NAME = "Presence Footsteps";
	protected final int VERSION = 5;
	protected final String FOR = "1.8";
	protected final String ADDRESS = "http://presencefootsteps.ha3.eu";
	protected final Identity identity = new HaddonIdentity(NAME, VERSION, FOR, ADDRESS);
	
	// NotifiableHaddon and UpdateNotifier
	private ConfigProperty config; // Can't be final
	private final Chatter chatter = new Chatter(this, NAME);
	private UpdateNotifier updateNotifier;
	
	// Meta
	private File presenceDir;
	private EdgeTrigger debugButton;
	private long pressedOptionsTime;
	
	// System
	private PFResourcePackDealer dealer = new PFResourcePackDealer();
	private PFIsolator isolator;
	
	// Binds
	private KeyBinding keyBindingMain;
	private final int keyBindDefaultCode = Keyboard.KEY_P;
	private final KeyWatcher watcher = new KeyWatcher(this);
	private final Ha3KeyManager keyManager = new Ha3KeyManager();
	
	// Use once
	private boolean firstTickPassed;
	private boolean mlpDetectedFirst;
	private boolean hasResourcePacks;
	private boolean hasDisabledResourcePacks;
	private boolean hasResourcePacks_FixMe;
	private int tickRound;
	
	@Override
	public void onLoad() {
		updateNotifier = new UpdateNotifier(this, "http://q.mc.ha3.eu/query/pf-litemod-version.json?ver=%d");
		
		util().registerPrivateSetter("Entity_nextStepDistance", Entity.class, -1, "nextStepDistance", "field_70150_b", "h");
		util().registerPrivateGetter("isJumping", EntityLivingBase.class, -1, "isJumping", "field_70703_bu", "aW");
		
		presenceDir = new File(util().getModsFolder(), "presencefootsteps/");
		
		if (!presenceDir.exists()) presenceDir.mkdirs();
		
		debugButton = new EdgeTrigger(new EdgeModel() {
			@Override
			public void onTrueEdge() {
				pressedOptionsTime = System.currentTimeMillis();
				PFLog.setDebugEnabled(true);
				reloadEverything(false);
			}
			
			@Override
			public void onFalseEdge() {}
		});
		
		reloadEverything(false);// Config is loaded here
		
		if (isInstalledMLP()) {
			if (getConfig().getBoolean("mlp.detected") == false) {
				getConfig().setProperty("mlp.detected", true);
				getConfig().setProperty("custom.stance", 1);
				saveConfig();
				
				mlpDetectedFirst = true;
			}
		}
		
		keyBindingMain = new KeyBinding("Presence Footsteps", keyBindDefaultCode, "key.categories.misc");
		Minecraft.getMinecraft().gameSettings.keyBindings = ArrayUtils.addAll(Minecraft.getMinecraft().gameSettings.keyBindings, keyBindingMain);
		keyBindingMain.setKeyCode(getConfig().getInteger("key.code"));
		KeyBinding.resetKeyBindingArrayAndHash();
		
		watcher.add(keyBindingMain);
		keyManager.addKeyBinding(keyBindingMain, new Ha3KeyHolding(this, 7));
		
		// Hooking
		IResourceManager resMan = Minecraft.getMinecraft().getResourceManager();
		if (resMan instanceof IReloadableResourceManager) {
			((IReloadableResourceManager) resMan).registerReloadListener(this);
		}
		
		((OperatorCaster) op()).setTickEnabled(true);
		((OperatorCaster) op()).setFrameEnabled(true);
	}
	
	public void reloadEverything(boolean nested) {
		isolator = new PFIsolator(this);
		
		reloadConfig();
		
		List<ResourcePackRepository.Entry> repo = dealer.findResourcePacks();
		if (repo.size() == 0)
		{
			PFLog.log("Presence Footsteps didn't find any compatible resource pack.");
			hasResourcePacks = false;
			hasDisabledResourcePacks = dealer.findDisabledResourcePacks().size() > 0;
			
			isolator.setGenerator(null);
			
			return;
		}
		hasResourcePacks = true;
		hasDisabledResourcePacks = false;
		
		for (ResourcePackRepository.Entry pack : repo) {
			PFLog.debug("Will load: " + pack.getResourcePackName());
		}
		
		reloadBlockMap(repo);
		reloadPrimitiveMap(repo);
		reloadAcoustics(repo);
		isolator.setSolver(new PFSolver(isolator));
		reloadVariator(repo);
		
		isolator.setGenerator(getConfig().getInteger("custom.stance") == 0 ? new PFReaderH(isolator, util()) : new PFReaderQP(isolator, util()));
	}
	
	private void reloadConfig() {
		config = new ConfigProperty();
		updateNotifier.fillDefaults(config);
		config.setProperty("update_found.enabled", false);
		config.setProperty("user.volume.0-to-100", 70);
		config.setProperty("mlp.detected", false);
		config.setProperty("custom.stance", 0);
		config.setProperty("key.code", keyBindDefaultCode);
		config.commit();
		
		boolean fileExisted = new File(presenceDir, "userconfig.cfg").exists();
		
		try {
			config.setSource(new File(presenceDir, "userconfig.cfg").getCanonicalPath());
			config.load();
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException("Error caused config not to work: " + e.getMessage());
		}
		
		if (!fileExisted) {
			config.save();
		}
		
		updateNotifier.loadConfig(config);
	}
	
	private void reloadVariator(List<ResourcePackRepository.Entry> repo) {
		Variator var = new NormalVariator();
		
		int working = 0;
		for (ResourcePackRepository.Entry pack : repo) {
			try {
				InputStreamConfigProperty config = new InputStreamConfigProperty();
				config.loadStream(this.dealer.openVariator(pack.getResourcePack()));
				
				var.loadConfig(config);
				working = working + 1;
			} catch (Exception e) {
				PFLog.debug("No variator found in " + pack.getResourcePackName() + ": " + e.getMessage());
			}
		}
		if (working == 0) {
			PFLog.log("No variators found in " + repo.size() + " packs!");
		}
		
		isolator.setVariator(var);
	}
	
	private void reloadBlockMap(List<ResourcePackRepository.Entry> repo) {
		BlockMap blockMap = new LegacyCapableBlockMap();
		
		int working = 0;
		for (ResourcePackRepository.Entry pack : repo) {
			try {
				InputStreamConfigProperty blockSound = new InputStreamConfigProperty();
				blockSound.loadStream(this.dealer.openBlockMap(pack.getResourcePack()));
				
				new PropertyBlockMap_Engine0().setup(blockSound, blockMap);
				working = working + 1;
			} catch (IOException e) {
				PFLog.debug("No blockmap found in " + pack.getResourcePackName() + ": " + e.getMessage());
			}
		}
		if (working == 0) {
			PFLog.log("No blockmaps found in " + repo.size() + " packs!");
		}
		
		isolator.setBlockMap(blockMap);
	}
	
	private void reloadPrimitiveMap(List<ResourcePackRepository.Entry> repo) {
		PrimitiveMap primitiveMap = new BasicPrimitiveMap();
		
		int working = 0;
		for (ResourcePackRepository.Entry pack : repo) {
			try {
				InputStreamConfigProperty primitiveSound = new InputStreamConfigProperty();
				primitiveSound.loadStream(dealer.openPrimitiveMap(pack.getResourcePack()));
				new PropertyPrimitiveMap_Engine0().setup(primitiveSound, primitiveMap);
				working = working + 1;
			} catch (IOException e) {
				PFLog.debug("No primitivemap found in " + pack.getResourcePackName() + ": " + e.getMessage());
			}
		}
		if (working == 0) {
			PFLog.log("No blockmaps found in " + repo.size() + " packs!");
		}
		
		isolator.setPrimitiveMap(primitiveMap);
	}
	
	@SuppressWarnings("resource")
	private void reloadAcoustics(List<ResourcePackRepository.Entry> repo) {
		AcousticsManager acoustics = new AcousticsManager(this.isolator);
		
		int working = 0;
		for (ResourcePackRepository.Entry pack : repo) {
			try {
				String jasonString = new Scanner(dealer.openAcoustics(pack.getResourcePack())).useDelimiter("\\Z").next();
				new JasonAcoustics_Engine0("").parseJSON(jasonString, acoustics);
				working = working + 1;
			} catch (IOException e) {
				PFLog.debug("No acoustics found in " + pack.getResourcePackName() + ": " + e.getMessage());
			}
		}
		if (working == 0) {
			PFLog.log("No blockmaps found in " + repo.size() + " packs!");
		}
		
		isolator.setAcoustics(acoustics);
		isolator.setSoundPlayer(new UserConfigSoundPlayerWrapper(acoustics, config));
		isolator.setDefaultStepPlayer(acoustics);
	}
	
	private boolean isInstalledMLP() {
		return Ha3StaticUtilities.classExists("com.minelittlepony.minelp.Pony", this);
	}
	
	@Override
	public void onFrame(float semi) {
		EntityPlayer ply = Minecraft.getMinecraft().thePlayer;
		
		if (ply == null) return;
		
		isolator.onFrame();
		
		boolean keysDown = util().areKeysDown(29, 42, 33);
		debugButton.signalState(keysDown); // CTRL SHIFT F
		if (keysDown && System.currentTimeMillis() - this.pressedOptionsTime > 1000) {
			displayMenu();
		}
		
		try {
			util().setPrivate(ply, "Entity_nextStepDistance", Integer.MAX_VALUE); //nextStepDistance
			//util().setPrivateValueLiteral(Entity.class, ply, "c", 37, Integer.MAX_VALUE);
			//util().setPrivateValueLiteral(Entity.class, ply, "c", 37, 0);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		if (!this.firstTickPassed) {
			firstTickPassed = true;
			updateNotifier.attempt();
			if (mlpDetectedFirst) {
				chatter.printChat(ChatColorsSimple.COLOR_TEAL, "Mine Little Pony has been detected! ", ChatColorsSimple.COLOR_WHITE, "4-legged mode has been enabled, which will make running sound like galloping amongst other things. ", ChatColorsSimple.COLOR_GRAY, "You can hold down for 1 second the combination LEFT CTRL + LEFT SHIFT + F to disable it.");
			}
			
			if (!hasResourcePacks) {
				hasResourcePacks_FixMe = true;
				if (hasDisabledResourcePacks) {
					chatter.printChat(ChatColorsSimple.COLOR_RED, "Resource Pack not enabled yet!");
					chatter.printChatShort(ChatColorsSimple.COLOR_WHITE, "You need to activate \"Presence Footsteps Resource Pack\" in the Minecraft Options menu for it to run.");
				} else {
					chatter.printChat(ChatColorsSimple.COLOR_RED, "Resource Pack missing from resourcepacks/!");
					chatter.printChatShort(ChatColorsSimple.COLOR_WHITE, "You may have forgotten to put the Resource Pack file into your resourcepacks/ folder.");
				}
				if (getConfig().getInteger("key.code") == this.keyBindDefaultCode) {
					chatter.printChatShort(ChatColorsSimple.COLOR_GRAY, "There is also a Presence Footsteps menu key in the Controls menu.");
				}
			}
		}
		if (hasResourcePacks_FixMe && hasResourcePacks) {
			hasResourcePacks_FixMe = false;
			chatter.printChat(ChatColorsSimple.COLOR_BRIGHTGREEN, "It should work now!");
		}
	}
	
	private void displayMenu() {
		if (util().isCurrentScreen(null)) {
			Minecraft.getMinecraft().displayGuiScreen(new PFGuiMenu((GuiScreen) util().getCurrentScreen(), this));
			PFLog.setDebugEnabled(false);
		}
	}
	
	public boolean hasResourcePacksLoaded() {
		return hasResourcePacks;
	}
	
	public boolean hasNonethelessResourcePacksInstalled() {
		return hasDisabledResourcePacks;
	}
	
	@Override
	public ConfigProperty getConfig() {
		return config;
	}
	
	@Override
	public void saveConfig() {
		if (config.commit()) { // If there were changes...
			PFLog.log("Saving configuration...");
			config.save(); // Write changes on disk.
		}
	}
	
	@Override
	public void onResourceManagerReload(IResourceManager var1) {
		PFLog.log("Resource Pack reload detected...");
		reloadEverything(false);
	}
	
	@Override
	public Chatter getChatter() {
		return chatter;
	}
	
	@Override
	public Identity getIdentity() {
		return identity;
	}
	
	@Override
	public void beginPress() {
		displayMenu();
	}
	
	@Override
	public void endPress() {
		
	}
	
	@Override
	public void shortPress() {
		
	}
	
	@Override
	public void beginHold() {
		
	}
	
	@Override
	public void endHold() {
		
	}
	
	@Override
	public void onKey(KeyBinding event) {
		keyManager.handleKeyDown(event);
	}
	
	@Override
	public void onTick() {
		if (tickRound == 0) {
			int keyCode = keyBindingMain.getKeyCode();
			if (keyCode != config.getInteger("key.code")) {
				PFLog.log("Key binding changed. Saving...");
				config.setProperty("key.code", keyCode);
				saveConfig();
			}
		}
		watcher.onTick();
		keyManager.handleRuntime();
		tickRound = (this.tickRound + 1) % 100;
	}
}
