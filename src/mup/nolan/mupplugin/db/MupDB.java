package mup.nolan.mupplugin.db;

import mup.nolan.mupplugin.MupPlugin;
import mup.nolan.mupplugin.config.Config;
import mup.nolan.mupplugin.utils.FileUtils;
import mup.nolan.mupplugin.utils.FuncUtils;
import mup.nolan.mupplugin.utils.ItemBuilder;
import mup.nolan.mupplugin.utils.Resrc;
import mup.nolan.mupplugin.utils.meter.TurboMeter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MupDB
{
	private final List<Runnable> onConnect = new ArrayList<>();
	private final Config dbConfig;
	private Connection conn;

	public MupDB(Config dbConfig)
	{
		this.dbConfig = dbConfig;
	}

	public void connect()
	{
		TurboMeter.start("init_db");

		final String type = dbConfig.getString("type");
		final String address = dbConfig.getString("address");
		final String user = dbConfig.getString("user");
		final String passwd = dbConfig.getString("passwd");
		final String dbName = dbConfig.getString("db-name");

		Bukkit.getScheduler().runTaskAsynchronously(MupPlugin.get(), () -> {
			TurboMeter.start("init_db_async");
			try
			{
				conn = type.equalsIgnoreCase("sqlite") ?
						DriverManager.getConnection(String.format("jdbc:sqlite:%s/%s", MupPlugin.get().getDataFolder().getPath(), address)) :
						DriverManager.getConnection(String.format("jdbc:mysql://%s/%s", address, dbName), user, passwd);

				final Statement stat = conn.createStatement();
				for (String s : FileUtils.readStr(MupPlugin.getRes("sql/createTables.sql")).split(";"))
					stat.execute(replaceFor(type, s));
				stat.close();

			} catch (SQLException e)
			{
				e.printStackTrace();
			}
			TurboMeter.end(MupPlugin.DEBUG > 1);

			onConnect.forEach(Runnable::run);
			onConnect.clear();
		});

		TurboMeter.end(MupPlugin.DEBUG > 0);
	}

	public void disconnect()
	{
		if (conn == null)
			return;

		try
		{
			if (!conn.getAutoCommit())
				conn.commit();
			conn.close();
		} catch (SQLException e)
		{
			e.printStackTrace();
		}
	}

	// TODO move all this to models

	public boolean itemsortEnabled(OfflinePlayer player)
	{
		boolean ret = false;
		final Statement st = getStatement();
		try
		{
			final ResultSet rs = st.executeQuery("select id from mup_itemsort where player = '" + player.getName() + "'");
			if (rs.next())
				ret = true;
		} catch (SQLException e)
		{
			e.printStackTrace();
		}
		closeStatement(st);
		return ret;
	}

	public void setItemsort(OfflinePlayer player, boolean enabled)
	{
		final Statement st = getStatement();
		try
		{
			st.executeUpdate(enabled ?
					"insert into mup_itemsort values (null, '" + player.getName() + "')" :
					"delete from mup_itemsort where player = '" + player.getName() + "'");
		} catch (SQLException e)
		{
			e.printStackTrace();
		}
		closeStatement(st);
	}

	public void getGalleryData(OfflinePlayer owner, boolean editmode, Resrc<List<GalleryRow>> items, Resrc<GalleryUserdataRow> userdata)
	{
		if (items.isNull())
			items.set(new ArrayList<>());

		final Statement st = getStatement();
		try
		{
			ResultSet rs = st.executeQuery("select * from mup_gallery where owner = '" + owner.getName() + "' " + (editmode ? "and lock_id is null" : "") + " order by sort_num");
			while (rs.next())
				items.get().add(new GalleryRow(rs.getInt("id"), owner, rs.getInt("sort_num"), ItemBuilder.fromString(rs.getString("item")), rs.getInt("amount"), rs.getDate("placed"), rs.getString("lock_id")));

			rs = st.executeQuery("select " + (editmode ? "*" : "unlocked_slots, current_border") + " from mup_gallery_userdata where player = '" + owner.getName() + "'");
			if (rs.next())
			{
				final int slots = rs.getInt("unlocked_slots");

				userdata.set(editmode ?
						new GalleryUserdataRow(rs.getInt("id"), owner, slots, rs.getString("unlocked_borders"), Material.getMaterial(rs.getString("current_border")), null, null) :
						new GalleryUserdataRow(-1, owner, slots, null, Material.getMaterial(rs.getString("current_border")), null, null));
			}

			final Material defaultBorder = MupPlugin.get().getConfigManager().getConfig("gallery").getMaterial("gui-items.default-border");

			if (userdata.isNull())
				userdata.set(new GalleryUserdataRow(-1, owner, 0, defaultBorder.name(), defaultBorder, null, null));
		} catch (SQLException e)
		{
			e.printStackTrace();
		}
		closeStatement(st);
	}

	public void updateGalleryData(List<GalleryRow> rows)
	{
		if (rows.isEmpty())
			return;

		final Statement st = getStatement();
		try
		{
			for (GalleryRow row : rows)
				st.addBatch("update mup_gallery set sort_num = %d%s where id = %d".formatted(
						row.getSortNum(),
						row.isAmountUpdate() ? ", amount = " + row.getAmount() : "",
						row.getId()
				));
			st.executeBatch();
		} catch (SQLException e)
		{
			e.printStackTrace();
		}
		closeStatement(st);
	}

	public void insertGalleryData(List<GalleryRow> rows)
	{
		if (rows.isEmpty())
			return;

		final Statement st = getStatement();
		try
		{
			for (GalleryRow row : rows)
			{
				final long placed;
				if (row.getPlaced() == null || (row.getPlaced() != null && row.getPlaced().getTime() == -1))
					placed = System.currentTimeMillis();
				else
					placed = row.getPlaced().getTime();

				st.addBatch(String.format(
						"insert into mup_gallery values(null, '%s', %d, '%s', %d, %d, null)",
						row.getOwner().getName(),
						row.getSortNum(),
						ItemBuilder.toString(row.getItem()),
						row.getAmount(),
						placed
				));
			}
			st.executeBatch();
		} catch (SQLException e)
		{
			e.printStackTrace();
		}
		closeStatement(st);
	}

	public void deleteGalleryData(List<GalleryRow> rows)
	{
		if (rows.isEmpty())
			return;

		final Statement st = getStatement();
		try
		{
			for (GalleryRow row : rows)
				st.addBatch("delete from mup_gallery where id = " + row.getId());
			st.executeBatch();
		} catch (SQLException e)
		{
			e.printStackTrace();
		}
		closeStatement(st);
	}

	public void upsertGalleryUserdata(GalleryUserdataRow userdata)
	{
		final Statement st = getStatement();
		try
		{
			if (userdata.getId() < 0)
			{
				st.executeUpdate("insert into mup_gallery_userdata values (null, '%s', %d, '%s', '%s', null, null)".formatted(
						userdata.getPlayer().getName(),
						userdata.getUnlockedSlots(),
						userdata.getUnlockedBorders(),
						userdata.getCurrentBorder().name()
				));
			}
			else
			{
				st.executeUpdate(("update mup_gallery_userdata set " +
						"unlocked_slots = %d, unlocked_borders = '%s', current_border = '%s' where id = %d").formatted(
						userdata.getUnlockedSlots(),
						userdata.getUnlockedBorders(),
						userdata.getCurrentBorder().name(),
						userdata.getId()
				));
			}

		} catch (SQLException e)
		{
			e.printStackTrace();
		}
		closeStatement(st);
	}

	public DiscordLink getLinked(Object acc)
	{
		final String where;
		if (acc instanceof OfflinePlayer)
			where = "player = '" + ((OfflinePlayer)acc).getName() + "'";
		else if (acc instanceof String)
			where = "player = '" + acc + "'";
		else if (acc instanceof Long)
			where = "dc_id = " + acc;
		else
			return null;

		DiscordLink ret = null;

		final Statement st = getStatement();
		try
		{
			final ResultSet rs = st.executeQuery("select * from mup_discord_linked where " + where);
			if (rs.next())
			{
				final OfflinePlayer player = acc instanceof OfflinePlayer ? (OfflinePlayer)acc : Bukkit.getOfflinePlayer(rs.getString("player"));
				final long discordId = acc instanceof Long ? (long)acc : rs.getLong("dc_id");
				final String verificationCode = rs.getString("verification_code");
				final boolean verified = rs.getBoolean("verified");
				ret = new DiscordLink(player, discordId, verificationCode, verified);
			}
		} catch (SQLException e)
		{
			e.printStackTrace();
		}
		closeStatement(st);
		return ret;
	}

	public boolean requestLink(DiscordLink link)
	{
		if (getLinked(link.getPlayer()) != null)
			return false;

		final Statement st = getStatement();
		try
		{
			st.executeUpdate("insert into mup_discord_linked values (null, '%s', %d, '%s', %s)".formatted(
					link.getPlayer().getName(),
					link.getDiscordId(),
					link.getVerificationCode(),
					link.isVerified()
			));
		} catch (SQLException e)
		{
			e.printStackTrace();
		}
		closeStatement(st);
		return true;
	}

	public boolean verify(DiscordLink link)
	{
		if (link.isVerified())
			return false;

		final Statement st = getStatement();
		try
		{
			st.executeUpdate("update mup_discord_linked set verified = true where dc_id = " + link.getDiscordId());
		} catch (SQLException e)
		{
			e.printStackTrace();
		}
		closeStatement(st);
		return true;
	}

	public void unlink(DiscordLink link)
	{
		final Statement st = getStatement();
		try
		{
			st.executeUpdate("delete from mup_discord_linked where dc_id = " + link.getDiscordId());
		} catch (SQLException e)
		{
			e.printStackTrace();
		}
		closeStatement(st);
	}

	public boolean linkCodeExists(String code)
	{
		boolean ret = false;
		final Statement st = getStatement();
		try
		{
			final ResultSet rs = st.executeQuery("select verified from mup_discord_linked where verification_code = '" + code + "'");
			if (rs.next())
				ret = rs.getBoolean("verified");
		} catch (SQLException e)
		{
			e.printStackTrace();
		}
		closeStatement(st);
		return ret;
	}

	public ReportsBlacklistRow getReportsBlacklist(OfflinePlayer player)
	{
		ReportsBlacklistRow ret = null;
		final Statement st = getStatement();
		try
		{
			final ResultSet rs = st.executeQuery("select * from mup_reports_blacklist where player = '%s'".formatted(player.getName()));
			if (rs.next())
				ret = new ReportsBlacklistRow(rs.getInt("id"), player, rs.getDate("applied"), rs.getDate("expires"), rs.getBoolean("expired"));
		} catch (SQLException e)
		{
			e.printStackTrace();
		}
		closeStatement(st);
		return ret;
	}

	public void removeReportsBlacklist(ReportsBlacklistRow row)
	{
		final Statement st = getStatement();
		try
		{
			st.executeUpdate("delete from mup_reports_blacklist where id = %d".formatted(row.getId()));
		} catch (SQLException e)
		{
			e.printStackTrace();
		}
		closeStatement(st);
	}

	public void insertReportsBlacklist(ReportsBlacklistRow row)
	{
		final Statement st = getStatement();
		try
		{
			st.executeUpdate("insert into mup_reports_blacklist values (null, '%s', %d, %d, %s)".formatted(
					row.getPlayer().getName(),
					row.getApplied().getTime(),
					row.getExpires() == null ? null : row.getExpires().getTime(),
					row.isExpired()
			));
		} catch (SQLException e)
		{
			e.printStackTrace();
		}
		closeStatement(st);
	}

	public List<ReportsRow> getReports(int id, OfflinePlayer from, String type, OfflinePlayer player, boolean uncheckedOnly)
	{
		final List<String> where = new ArrayList<>();

		if (id > 0)
			where.add("id = " + id);
		else
		{
			if (uncheckedOnly)
				where.add("checked = false");
			if (from != null)
				where.add("from_player = '%s'".formatted(from.getName()));
			if (type != null)
				where.add("type = '%s'".formatted(type));
			if (player != null)
				where.add("player = '%s'".formatted(player.getName()));
		}

		final List<ReportsRow> ret = new ArrayList<>();

		final Statement st = getStatement();
		try
		{
			final ResultSet rs = st.executeQuery("select * from mup_reports" + (where.isEmpty() ? "" : " where " + String.join(" and ", where)));
			while (rs.next())
			{
				ret.add(new ReportsRow(
						rs.getInt("id"),
						Bukkit.getOfflinePlayer(rs.getString("from_player")),
						rs.getString("type"),
						Bukkit.getOfflinePlayer(rs.getString("player")),
						new Location(Bukkit.getWorld(rs.getString("pos_world")), rs.getInt("pos_x"), rs.getInt("pos_y"), rs.getInt("pos_z")),
						rs.getString("comment"),
						rs.getDate("sent_at"),
						rs.getBoolean("checked")
				));
			}

		} catch (SQLException e)
		{
			e.printStackTrace();
		}
		closeStatement(st);
		return ret;
	}

	public int insertReport(ReportsRow row)
	{
		int id = -1;
		final Statement st = getStatement();
		try
		{
			st.executeUpdate("insert into mup_reports values (null, '%s', '%s', '%s', '%s', %d, %d, %d, '%s', %d, false)".formatted(
					row.getFrom().getName(),
					row.getType(),
					FuncUtils.optionallyMap(row.getPlayer(), OfflinePlayer::getName),
					FuncUtils.optionallyMap(row.getPos(), pos -> pos.getWorld().getName()),
					FuncUtils.optionallyMap(row.getPos(), Location::getBlockX),
					FuncUtils.optionallyMap(row.getPos(), Location::getBlockY),
					FuncUtils.optionallyMap(row.getPos(), Location::getBlockZ),
					row.getComment(),
					row.getSentAt().getTime()
			));
			final ResultSet rs = st.getGeneratedKeys();
			if (rs.next())
				id = rs.getInt(1);
		} catch (SQLException e)
		{
			e.printStackTrace();
		}
		closeStatement(st);
		return id;
	}

	public void updateReport(List<ReportsRow> rows)
	{
		final Statement st = getStatement();
		try
		{
			for (ReportsRow row : rows)
				st.addBatch("update mup_reports set checked = %s where id = %d".formatted(row.isChecked(), row.getId()));
			st.executeBatch();
		} catch (SQLException e)
		{
			e.printStackTrace();
		}
		closeStatement(st);
	}

	public int removeReports(List<ReportsRow> rows)
	{
		int ret = -1;
		final Statement st = getStatement();
		try
		{
			for (ReportsRow row : rows)
				st.addBatch("delete from mup_reports where id = " + row.getId());
			ret = st.executeBatch().length;
		} catch (SQLException e)
		{
			e.printStackTrace();
		}
		closeStatement(st);
		return ret;
	}

	public Statement getStatement()
	{
		try
		{
			return conn.createStatement();
		} catch (SQLException e)
		{
			e.printStackTrace();
			return null;
		}
	}

	public String getType()
	{
		return dbConfig.getString("type");
	}

	public void addOnConnect(Runnable func)
	{
		if (conn == null)
			onConnect.add(func);
		else
			func.run();
	}

	private String replaceFor(String type, String str)
	{
		if (type.equalsIgnoreCase("sqlite"))
		{
			return str.replaceAll("auto_increment", "autoincrement");
		}
		else // mysql
		{
			return str.replaceAll("autoincrement", "auto_increment");
		}
	}

	public static void closeStatement(Statement stat)
	{
		try
		{
			stat.close();
		} catch (SQLException e)
		{
			e.printStackTrace();
		}
	}
}
