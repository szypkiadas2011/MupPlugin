package mup.nolan.mupplugin.modules;

import mup.nolan.mupplugin.MupPlugin;
import mup.nolan.mupplugin.config.Config;

public abstract class Module
{
	private final MupPlugin mupPlugin;
	private final Config config;
	private final String name;
	private boolean enabled = false;

	public Module(MupPlugin mupPlugin, String name)
	{
		this.mupPlugin = mupPlugin;
		config = mupPlugin.getConfigManager().getConfig(name);
		this.name = name;
	}

	public void onEnable()
	{
	}

	public void onDisable()
	{
	}

	public void reload()
	{
		try
		{
			onDisable();
			onEnable();
		} catch (Exception e)
		{
			MupPlugin.log().severe("Error reloading module " + name);
			e.printStackTrace();
			enabled = false;
			return;
		}
		enabled = true;
		MupPlugin.log().info(mupPlugin.getConfigManager().getConfig("modules").getStringF("messages.on-reload").replace("{}", name));
	}

	public String getName()
	{
		return name;
	}

	public boolean isEnabled()
	{
		return enabled;
	}

	public void setEnabled(boolean enabled)
	{
		if (this.enabled == enabled)
			return;

		try
		{
			if (this.enabled = enabled)
				onEnable();
			else
				onDisable();
		} catch (Exception e)
		{
			MupPlugin.log().severe("Error %s module %s".formatted(enabled ? "enabling" : "disabling", name));
			e.printStackTrace();
			return;
		}

		final String cfgStr = enabled ? "messages.on-enable" : "messages.on-disable";
		MupPlugin.log().info(mupPlugin.getConfigManager().getConfig("modules").getStringF(cfgStr).replace("{}", name));
	}

	protected MupPlugin mup()
	{
		return mupPlugin;
	}

	protected Config cfg()
	{
		return config;
	}
}
