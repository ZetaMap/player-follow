/*
 * This file is part of Player Follow. The plugin that allow players to follow each others.
 *
 * MIT License
 *
 * Copyright (c) 2024-2025 ZetaMap
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package fr.zetamap.playerfollow;

import arc.Core;
import arc.Events;
import arc.util.CommandHandler;
import arc.util.Log;

import mindustry.game.EventType;
import mindustry.gen.Player;
import mindustry.mod.Plugin;

import fr.zetamap.playerfollow.api.AbstractPlayerFollow;
import fr.zetamap.playerfollow.api.FollowMode;
import fr.zetamap.playerfollow.api.PlayerFollowManager;


public class Main extends Plugin {
  public static PlayerFollowManager manager;
  public static FollowMode<Player> defaultMode;
  
  public void init() {
    defaultMode = FollowModes.joint;
    
    // Load settings
    String modeName = Core.settings.getString("player-follow-mode", defaultMode.name);
    FollowMode<Player> mode = FollowMode.of(modeName);
    if (mode == null) {
      Log.err("[PlayerFollow] mode '@' not found, using the default mode: @", modeName, defaultMode.name);
      Core.settings.put("player-follow-mode", defaultMode.name);
    } else defaultMode = mode;
    
    // Register an event to remove player from followed target
    Events.on(EventType.PlayerLeave.class, e -> {
      if (e.player == null) return;
      AbstractPlayerFollow follow = manager.get(e.player);

      if (follow != null) {
        follow.message("The followed player disconnected! ([white]@[orange])", follow.followed().name);
        manager.remove(follow);
      } else manager.removeFollower(e.player);
    
    });
    
    // Start the follow updater
    manager = PlayerFollowManager.instance();
  }

  @Override
  public void registerClientCommands(CommandHandler handler) {
    handler.<Player>register("follow", "[player|#unitID|UUID] [mode...]", "Follow/Unfollow a specific player.", 
    (args, player) -> {
      AbstractPlayerFollow follow;
      
      if (args.length == 0) {
        follow = manager.find(player);
        
        if (follow != null) {
          follow.remove(player);
          Players.ok(player, "You stopped following '[white]@[green]'.", follow.followed.name);
        } else Players.warn(player, "You are currently following nobody.");
        return;
      }

      // Search the target
      Players.SearchResult target = Players.find(args);

      if (!target.found) {
        Players.errPlayerNotFound(player);
        return;
      // Avoid self targeting
      } else if (target.player == player) {
        Players.err(player, "You cannot follow yourself...");
        return;
      // Check for a potential follow loop
      } else if ((follow = manager.get(player)) != null && follow.contains(target.player)) {
        Players.err(player, "You cannot follow a player who already follows you.");
        return;
      }

      follow = manager.get(target.player);

      // Create a new follow, if none exists for the target
      if (follow == null) {
        FollowMode<Player> mode = defaultMode;
        
        // Use the follow mode if specified
        if (target.rest.length != 0) {
          String modeName = String.join(" ", target.rest).trim();
          mode = FollowMode.of(modeName);

          if (mode == null) {
            Players.err(player, "Follow mode '[cyan]@[scarlet]' not found.", modeName);
            return;
          }
        }
        
        follow = manager.add(mode, target.player);
        
      // Check whether the player is already following the target
      } else if (follow.contains(player)) {
        Players.warn(player, "You are already following this player.");
        return;
        
      // Ignore mode if another player is following the target
      } else if (target.rest.length != 0) 
        Players.warn(player, "Follow mode ignored because another follower set it.");

      // Remove the player from followers of a potential another target, and add it to this target
      manager.removeFollower(player);
      follow.add(player);
      Players.ok(player, "You are now following '[white]@[green]'.", target.player.name);
    });
    
    handler.<Player>register("follow-stop", "[player|#unitID|UUID...]", "Remove all players currently following a target.", 
    (args, player) -> {
      // Only admins can do that
      if (!player.admin) {
        Players.errCommandUseDenied(player);
        return;
      }
      
      Player target;
      if (args.length >= 1) {
        Players.SearchResult t = Players.find(args);
        
        if (!t.found) {
          Players.errPlayerNotFound(player);
          return;
        }
        
        target = t.player;
      } else target = player;

      AbstractPlayerFollow follow = manager.get(target);
        
      if (follow == null) {
        if (target == player) Players.err(player, "You are currently followed by no one.");
        else Players.err(player, "'[white]@[scarlet]' is currently followed by no one.", target.name);
        return;
      }
    
      manager.remove(follow); 
      follow.message("'@[orange]' requested to not be followed!", follow.followed.name);
      Players.ok(player, "Follow stopped and followers notified.");
    });
    
    handler.<Player>register("follow-mode", "[mode] ['force']", "Get/Set the default follow mode.", 
    (args, player) -> {
      if (args.length == 0) {
        Players.info(player, "Default mode is [cyan]@[] follow.", defaultMode.name);
        String[] message = {"Available modes: \n"};
        FollowMode.each(m -> message[0] += "  - [cyan]" + m.name + "[]\n");
        Players.info(player, message[0]);
        return;
      }

      // Only admins can do that
      if (!player.admin) {
        Players.errArgUseDenied(player);
        return;
      }        
      
      FollowMode<Player> mode = FollowMode.of(args[0]);
      
      if (mode == null) {
        Players.err(player, "Mode [cyan]@[scarlet] not found.", args[0]);
        return;
      } else if (args.length == 2 && !args[1].equals("force")) {
        Players.err(player, "Second argument must be 'force'.");
        Players.warn(player, "Note: the 'force' argument is to change the mode of all players currently followed. "
                           + "Not only the new ones.");
        return;
      }
      
      defaultMode = mode;
      Core.settings.put("player-follow-mode", defaultMode.name);
      Players.ok(player, "Follow mode set to [cyan]@[].", defaultMode.name);
      
      // Change the mode of all target if 'force' argument is provided
      if (args.length == 2) {
        manager.changeMode(mode);
        Players.ok(player, "Forced new mode to all players.");
      }
    });
  }
}
