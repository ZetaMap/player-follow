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

package player_follow;

import arc.struct.Seq;
import arc.util.Strings;

import mindustry.gen.Groups;
import mindustry.gen.Player;


public class Players {
  public final Player player;
  public final String[] rest;
  public final boolean found;
  
  private Players(Player player, String argsRest) {
    String[] test = argsRest.trim().split(" ");
    
    this.player = player;
    this.rest = test.length == 1 && test[0].isEmpty() ? new String[]{} : test;
    this.found = this.player != null;
}
  
  public static void errPlayerNotFound(Player player) {
    err(player, "Player not found or doesn't exist!");
  }

  public static void errArgUseDenied(Player player) {
    err(player, "You are not allowed to use these arguments!");
  }
  public static void errCommandUseDenied(Player player) {
    err(player, "You are not allowed to use this command!");
  }

  public static void err(Player player, String fmt, Object... msg) {
    player.sendMessage("[scarlet]" + Strings.format(fmt, msg));
  }
  public static void info(Player player, String fmt, Object... msg) {
    player.sendMessage(Strings.format(fmt, msg));
  }
  public static void warn(Player player, String fmt, Object... msg) {
    player.sendMessage("[orange]" + Strings.format(fmt, msg));
  }
  public static void ok(Player player, String fmt, Object... msg) {
    player.sendMessage("[green]" + Strings.format(fmt, msg));
  }
  
  public static Players findByName(String[] args) { return findByName(String.join(" ", args)); }
  /** 
   * Try to find a player by name. (sorted by most larger name first to avoid non-targatable players) <br>
   * Non-targatable players are players that includes a command argument or informations of another player
   * at end of his nickname, to not be targeted by commands.
   */
  public static Players findByName(String arg) {
    class PlayerSortElement {
      public final Player player;
      public final String strippedName;
      public final int strippedNameSize;
      
      public PlayerSortElement(Player player) {
        this.player = player;
        this.strippedName = normalizeString(player.getInfo().lastName);
        this.strippedNameSize = this.strippedName.length();
      }
    }
    
    Seq<PlayerSortElement> temp = new Seq<>();
    Groups.player.each(p -> temp.add(new PlayerSortElement(p)));
    temp.sort(p -> p.strippedNameSize);
    temp.reverse();
    
    String args = normalizeString(arg) + " ";
    PlayerSortElement target = temp.find(p -> args.startsWith(p.strippedName + " "));
    
    return target == null ? new Players(null, args) : 
                            new Players(target.player, args.substring(target.strippedNameSize));
  }
  
  public static Players findByID(String[] args) { return findByID(String.join(" ", args)); }
  /** Try to find a player by unitID (a unitID looks like this: #000001) */
  public static Players findByID(String arg) {
    if (!arg.startsWith("#") || arg.length() < 2) return new Players(null, arg);
    int id = Strings.parseInt(arg.split(" ")[0].substring(1));
    if (id == Integer.MIN_VALUE) return new Players(null, arg);
    
    Player target = Groups.player.find(p -> p.id == id);
    
    return new Players(target, target == null ? arg : arg.substring(String.valueOf(id).length() + 1));
  }
  
  public static Players findByUUID(String[] args) { return findByUUID(String.join(" ", args)); }
  /** Try to find a player by UUID */
  public static Players findByUUID(String arg) {
    String args = arg + " ";
    Player target = Groups.player.find(p -> args.startsWith(p.uuid() + " "));
    
    return new Players(target, target == null ? args : args.substring(target.uuid().length()));
  }
  
  public static Players find(String[] args) { return find(String.join(" ", args)); }
  /** 
   * General function to find a player. <br>
   * First, will try to find by name, to avoid non-targatable players. 
   * (more infos in {@link #findByName(String)} <br>
   * After, with the unitID (like #012345). <br>
   * And finally, with the player UUID.
   */
  public static Players find(String arg) {
    Players target = Players.findByName(arg);
    if (target.found) return target;
    target = Players.findByID(arg);
    return target.found ? target : Players.findByUUID(arg);
  }
  
  /** Remove glyphs, colors and sides spaces. */
  public static String normalizeString(String str) {
    return Strings.stripColors(Strings.stripGlyphs(str)).trim();
  }
}