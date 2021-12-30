package mup.nolan.mupplugin.modules;

import mup.nolan.mupplugin.MupPlugin;
import mup.nolan.mupplugin.modules.antiafk.AntiafkModule;
import mup.nolan.mupplugin.modules.chatpatrol.ChatPatrolModule;
import mup.nolan.mupplugin.modules.gallery.GalleryModule;
import mup.nolan.mupplugin.utils.meter.TurboMeter;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager
{
	private final MupPlugin mupPlugin;
	private final List<Module> modules = new ArrayList<>();

	public ModuleManager(MupPlugin mupPlugin)
	{
		this.mupPlugin = mupPlugin;
	}

	public void registerModules()
	{
		TurboMeter.start("init_modules");

		register(new ItemsortModule(mupPlugin));
		register(new BottlexpModule(mupPlugin));
		register(new GalleryModule(mupPlugin));
		register(new AntiafkModule(mupPlugin));
		register(new CheatnonoModule(mupPlugin));
		register(new ChatPatrolModule(mupPlugin));

		TurboMeter.end(MupPlugin.DEBUG > 0);
	}

	public List<String> getModules(boolean enabledOnly)
	{
		return modules.stream().filter(m -> !enabledOnly || m.isEnabled()).map(Module::getName).toList();
	}

	public Module getModule(String moduleName)
	{
		return modules.stream().filter(m -> m.getName().equalsIgnoreCase(moduleName)).findFirst().orElse(null);
	}

	public boolean isModuleEnabled(String moduleName)
	{
		return getModule(moduleName) != null && getModule(moduleName).isEnabled();
	}

	public boolean checkEnabled(String moduleName, CommandSender sender)
	{
		if (isModuleEnabled(moduleName))
			return false;
		sender.sendMessage(mupPlugin.getConfigManager().getConfig("modules").getStringF("messages.on-command-disabled").replace("{}", moduleName));
		return true;
	}

	public void disableAll()
	{
		modules.forEach(m -> m.setEnabled(false));
	}

	private void register(Module module)
	{
		if (mupPlugin.getConfigManager().getConfig("modules").getBool(module.getName()))
			module.setEnabled(true);
		modules.add(module);
	}
}
