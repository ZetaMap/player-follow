/*
 * This file is part of Player Follow. The plugin that allow players to follow each others.
 *
 * MIT License
 *
 * Copyright (c) 2024 ZetaMap
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

package player_follow;

import arc.Core;
import mindustry.gen.Player;


public class Main extends mindustry.mod.Plugin {
  public static FollowMode defaultMode = FollowMode.joint;
  
  public Main() {
    // Load settings
    String mode = Core.settings.getString("player-follow-mode", defaultMode.name());
    FollowMode temp = FollowMode.get(mode);
    if (temp == null) {
      arc.util.Log.err("[PlayerFollow] mode '@' not found, using the default mode: @", mode, defaultMode.name());
      Core.settings.put("player-follow-mode", defaultMode.name());
    } else defaultMode = temp;
    
    // Register an event to remove player from followed target
    arc.Events.on(mindustry.game.EventType.PlayerLeave.class, e -> {
      if (e.player != null) {
        Follower follow = Follower.follows.find(f -> f.followed.uuid().equals(e.player.uuid()));
        
        // If the player is followed, warn followers and remove the associated task.
        if (follow != null) {
          follow.following.each(p -> 
              Players.warn(p, "The followed player disconnected! ([white]@[orange])", follow.followed.name));
          follow.stop();
          Follower.follows.remove(follow);
          
        // Else, see if the player is following anyone, to remove them from the list
        } else Follower.follows.contains(f -> f.removeFollower(p -> p.uuid().equals(e.player.uuid())));
      }
    });
  }

  @Override
  public void registerClientCommands(arc.util.CommandHandler handler) {
    handler.<Player>register("follow", "[player] [mode...]", "Follow/Unfollow a specific player.", 
    (args, player) -> {
      if (args.length >= 1) {
        // Search the target
        Players target = Players.find(args);

        if (!target.found) {
          Players.errPlayerNotFound(player);
          return;

        // Avoid self targeting
        } else if (target.player.uuid().equals(player.uuid())) {
          Players.err(player, "You cannot target yourself...");
          return;
        }

        Follower follow = Follower.follows.find(f -> f.followed.uuid().equals(target.player.uuid()));

        // Check whether this could create a follow loop
        if (Follower.follows.contains(f -> f.followed.uuid().equals(player.uuid()) && 
                                           f.following.contains(p -> p.uuid().equals(target.player.uuid())))) {
          Players.err(player, "You cannot follow a player who already follows you.");
          return;

        // Create a new one, if none exists
        } else if (follow == null) {
          FollowMode mode = defaultMode;
          
          // Now search the follow mode if specified
          if (target.rest.length != 0) {
            String modeName = String.join(" ", target.rest).trim();
            FollowMode temp = FollowMode.get(modeName);
            
            if (temp == null) {
              Players.err(player, "Follow mode '[cyan]@[scarlet]' not found.", modeName);
              return;
            }
            
            mode = temp;
          }
          
          follow = mode.newFollow(target.player);
          follow.addFollower(player);

        // Checks whether the player is already following the target
        } else if (follow.following.contains(p -> p.uuid().equals(player.uuid()))) {
          Players.err(player, "You are already following this player.");
          return;

        // Else, simply add the player to the list of target
        } else {
          if (target.rest.length != 0)
            Players.warn(player, "Follow mode ignored because another player is following the target.");
          follow.addFollower(player);
        }

        // Remove the player from the list of another potential target
        Follower.follows.each(
            f -> !f.followed.uuid().equals(target.player.uuid()),
            f -> f.removeFollower(p -> p.uuid().equals(player.uuid()))
        );

        // Wars the player that he is following the target
        Players.ok(player, "You are now following the player '[white]@[green]'.", target.player.name);

      } else {
        boolean removed = Follower.follows.contains(f -> {
          boolean r = f.removeFollower(p -> p.uuid().equals(player.uuid()));
          if (r) Players.ok(player, "You have stopped following the player '[white]@[green]'.", f.followed.name);
          return r;
        });
        
        if (!removed) {
          Players.warn(player, "You are currently following nobody.");
          return;
        }
        
        // Removes all task with no followers
        defaultMode.removeNotFollowed();
      }
    });
    
    handler.<Player>register("follow-stop", "[player...]", "Remove all players currently following a target.", 
    (args, player) -> {
      // Only admins can do that
      if (!player.admin) {
        Players.errCommandUseDenied(player);
        return;
      }
      
      Player target;
      if (args.length >= 1) {
        Players t = Players.find(args);
        
        if (!t.found) {
          Players.errPlayerNotFound(player);
          return;
        }
        
        target = t.player;
      } else target = player;
      
      
      Follower follow = Follower.follows.find(f -> f.followed.uuid().equals(target.uuid()));
        
      if (follow == null) {
        if (target == player) Players.err(player, "You're currently followed by no one.");
        else Players.err(player, "'[white]@[scarlet' is currently followed by no one.", target.name);
        return;
      }
    
      follow.following.each(p -> 
        Players.warn(p, "The target requested to not be followed! ([white]@[orange])", follow.followed.name));
      follow.following.clear();
      defaultMode.removeNotFollowed();      
      Players.ok(player, "Follow stopped and followers has been notified.");
    });
    
    handler.<Player>register("follow-mode", "[mode] [force]", "Get/Set the default follow mode.", 
    (args, player) -> {
      if (args.length >= 1) {
        // Only admins can do that
        if (!player.admin) {
          Players.errArgUseDenied(player);
          return;
        }        
        
        FollowMode mode = FollowMode.get(args[0]);
        
        if (mode == null) {
          Players.err(player, "Mode not found");
          return;
          
        } else if (args.length == 2 && !args[1].equals("force")) {
          Players.err(player, "Second argument must be 'force'.");
          Players.warn(player, "Note: 'force' argument is to change the mode "
                             + "of all players currently following a target. Not only new targets.");
          return;
        }
        
        defaultMode = mode;
        Core.settings.put("player-follow-mode", defaultMode.name());
        Players.ok(player, "Follow mode set to [cyan]@.", defaultMode.name());
        
        // Change the mode of all target if 'force' argument is provided
        if (args.length == 2) {
          arc.struct.Seq<Follower> current = Follower.follows.copy();
          Follower.follows.clear();
          current.each(f -> {
            f.stop();
            Follower newMode = defaultMode.newFollow(f.followed);
            newMode.lock.lock();
            newMode.following = f.following.copy();
            newMode.lock.unlock();
          });
          // Little clean
          defaultMode.removeNotFollowed();
          Players.ok(player, "Forced new mode to all players.");
        }
        
      } else {
        Players.info(player, "Default mode is [cyan]@[] follow.", defaultMode.name());
        String message = "Available modes: \n";
        for (FollowMode mode : FollowMode.values())
          message += "  - [cyan]" + mode.name() + "[]\n";
        Players.info(player, message);
      }
    });
  }
}