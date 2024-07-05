/*
 * This file is part of Player Follow. The plugin that allow players to follow each others.
 *
 * MIT License
 *
 * Copyright (c) 2023 ZetaMap
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
import arc.util.Strings;
import mindustry.gen.Player;


public class Main extends mindustry.mod.Plugin {
  public static FollowMode followMode = FollowMode.simple;
  
  public Main() {
    // Load settings
    String mode = (String) Core.settings.get("player-follow-mode", followMode);
    FollowMode temp = FollowMode.valueOf(mode);
    if (temp == null) arc.util.Log.err("[PlayerFollow] mode '@' not found, using default mode.", mode);
    else followMode = temp;
    
    // Register an event to remove player from followed target
    arc.Events.on(mindustry.game.EventType.PlayerLeave.class, e -> {
      if (e.player != null) {
        Follower follow = Follower.follows.find(f -> f.followed.uuid().equals(e.player.uuid()));
        
        // If the player is being followed, warns anyone following him, and remove the associated task.
        if (follow != null) {
          follow.following.each(p -> p.sendMessage("[orange]The followed player disconnected! (" + follow.followed.name + "[orange])"));
          follow.stop();
          Follower.follows.remove(follow);
          
        // Else, see if the player is following anyone, to remove them from the list
        } else Follower.follows.each(f -> f.removeFollower(p -> p.uuid().equals(e.player.uuid())));
      }
    });
  }

  @Override
  public void registerClientCommands(arc.util.CommandHandler handler) {
    handler.<Player>register("follow", "[player...]", "Follow or unfollow a specific player.", (args, player) -> {
      if (args.length >= 1) {
        // Search the target
        Player found = mindustry.gen.Groups.player.find(p -> 
            Strings.stripColors(Strings.stripGlyphs(p.name)).trim().equals(args[0]) || p.uuid().equals(args[0]));

        if (found == null) {
          player.sendMessage("[scarlet]Unable to find the player");
          return;

        // Avoid self targeting
        } else if (found.uuid().equals(player.uuid())) {
          player.sendMessage("[scarlet]You cannot target yourself...");
          return;
        }

        Follower follow = Follower.follows.find(f -> f.followed.uuid().equals(found.uuid()));

        // Check whether this could create a follow loop
        if (Follower.follows.contains(f -> f.followed.uuid().equals(player.uuid()) && 
                                           f.following.contains(p -> p.uuid().equals(found.uuid())))) {
          player.sendMessage("[scarlet]You cannot follow a player who already follows you");
          return;

        // Add the player to the list of target
        // Or create a new one, if none exists
        } else if (follow == null) {
          follow = followMode.newFollow(found);
          follow.addFollower(player);

        // Checks whether the player is already following the target
        } else if (follow.following.contains(p -> p.uuid().equals(player.uuid()))) {
          player.sendMessage("[scarlet]You are already following this player");
          return;

        // Else, simply add the player to the list of target
        } else follow.addFollower(player);

        // Remove the player from the list of another potential target
        Follower.follows.each(
            f -> !f.followed.uuid().equals(found.uuid()),
            f -> f.removeFollower(p -> p.uuid().equals(player.uuid()))
        );

        // Wars the player that he is following the target
        player.sendMessage("[green]You are now following the player '" + found.name + "[green]'");

      } else {
        boolean removed = Follower.follows.contains(f -> {
          boolean r = f.removeFollower(p -> p.uuid().equals(player.uuid()));
          if (r) player.sendMessage("[green]You have stopped following the player '" + f.followed.name + "[green]'");
          return r;
        });
        
        if (!removed) {
          player.sendMessage("[orange]You are currently following nobody");
          return;
        }
      }
      
      // Removes all task with no followers
      followMode.removeNotFollowed();
    });
    
    handler.<Player>register("follow-mode", "[mode] [force]", "Get/Set the follow mode.", (args, player) -> {
      // Only admins can do that
      if (!player.admin) {
        player.sendMessage("(scarlet]You are not allowed to use this command!");
        return;
      }
      
      if (args.length >= 1) {
        // Get the mode
        FollowMode mode = FollowMode.valueOf(args[0]);
        
        if (mode == null) {
          player.sendMessage("[scarlet]Mode not found");
          return;
          
        } else if (args.length == 2 && !args[1].equals("force")) {
          player.sendMessage("[scarlet]Second argument must be 'force'.");
          player.sendMessage("[orange]Note: 'force' argument is to change the mode "
              + "of all players currently following a target. Not only new targets.");
          return;
        }
        
        FollowMode oldMode = followMode;
        followMode = mode;
        Core.settings.put("player-follow-mode", followMode.name());
        player.sendMessage("[green]Follow mode set to [cyan]" + followMode.name());
        
        // Change the mode of all target if 'force' argument is provided
        if (args.length == 2 && oldMode != followMode) {
          arc.struct.Seq<Follower> current = Follower.follows.copy();
          Follower.follows.clear();
          current.each(f -> {
            Follower newMode = followMode.newFollow(f.followed);
            newMode.lock.lock();
            newMode.following = f.following.copy();
            newMode.lock.unlock();
            f.stop();
          });
          // Little clean
          followMode.removeNotFollowed();
          player.sendMessage("[green]Forced new mode to all players");
        }
        
      } else {
        player.sendMessage("Current mode is [cyan]" + followMode.name() + "[] follow");
        String message = "Available modes: \n";
        for (FollowMode mode : FollowMode.values())
          message += "  - [cyan]" + mode.name() + "[]\n";
        player.sendMessage(message);
      }
    });
  }
}