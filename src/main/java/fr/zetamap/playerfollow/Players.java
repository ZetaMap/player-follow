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

import arc.struct.Seq;
import arc.util.Strings;
import arc.util.pooling.Pool;
import arc.util.pooling.Pools;

import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Administration.Config;


public class Players {
  private static final Pool<SearchResult> resultPool = Pools.get(SearchResult.class, SearchResult::new);

  
  public static void errPlayerNotFound(Player player) { err(player, "Player not found!"); }
  public static void errArgUseDenied(Player player) { err(player, "You are not allowed to use these arguments!"); }
  public static void errCommandUseDenied(Player player) { err(player, "You are not allowed to use this command!"); }

  public static void err(Player player, String fmt, Object... msg) { player.sendMessage("[scarlet]" + Strings.format(fmt, msg)); }
  public static void info(Player player, String fmt, Object... msg) { player.sendMessage(Strings.format(fmt, msg)); }
  public static void warn(Player player, String fmt, Object... msg) { player.sendMessage("[orange]" + Strings.format(fmt, msg)); }
  public static void ok(Player player, String fmt, Object... msg) { player.sendMessage("[green]" + Strings.format(fmt, msg)); }
  
  public static SearchResult findByName(String[] args) { return findByName(String.join(" ", args)); }
  /** 
   * Try to find a player by name. (sorted by most larger name first to avoid non-targatable players) <br>
   * Non-targatable players are players that includes a command argument or informations of another player
   * at end of his nickname, to not be targeted by commands.
   */
  public static SearchResult findByName(String arg) {
    Pool<SortElement> pool = Pools.get(SortElement.class, SortElement::new);
    Seq<SortElement> temp = new Seq<>(Groups.player.size());
    Groups.player.each(p -> temp.add(pool.obtain().set(p)));
    temp.sort(p -> p.strippedNameSize);

    // Process in reverse order, excluding the possibility of a player's name beginning with another player's name
    String args = normalizeName(arg)+" ";
    SearchResult result = null;
    for (int i=temp.size-1; i>=0; i--) {
      if ((result = temp.get(i).find(args)) != null) break;
    }
    pool.freeAll(temp);
    return result != null ? result : resultPool.obtain().set(null, args);
  }
  
  public static SearchResult findByID(String[] args) { return findByID(String.join(" ", args)); }
  /** Try to find a player by unitID (a unitID looks like this: #000001) */
  public static SearchResult findByID(String arg) {
    if (!arg.startsWith("#") || arg.length() < 2) return resultPool.obtain().set(null, arg);
    int id = Strings.parseInt(arg.split(" ")[0].substring(1));
    if (id == Integer.MIN_VALUE) return resultPool.obtain().set(null, arg);
    
    Player target = Groups.player.getByID(id);
    return resultPool.obtain().set(target, target == null ? arg : arg.substring(String.valueOf(id).length()+1));
  }
  
  public static SearchResult findByUUID(String[] args) { return findByUUID(String.join(" ", args)); }
  /** Try to find a player by UUID */
  public static SearchResult findByUUID(String arg) {
    String args = arg+" ";
    Player target = Groups.player.find(p -> args.startsWith(p.uuid()+" "));
    return resultPool.obtain().set(target, target == null ? args : args.substring(target.uuid().length()));
  }
  
  public static SearchResult find(String[] args) { return find(String.join(" ", args)); }
  /** 
   * General function to find a player. <br>
   * First, will try to find by name, to avoid non-targatable players. 
   * (more infos in {@link #findByName(String)} <br>
   * After, with the unitID (like #012345). <br>
   * And finally, with the player UUID.
   */
  public static SearchResult find(String arg) {
    SearchResult target = Players.findByName(arg);
    if (target.found) return target;
    target = Players.findByID(arg);
    return target.found ? target : Players.findByUUID(arg);
  }
  
  /** Remove glyphs, colors and sides spaces. */
  public static String normalizeName(String name) {
    return Strings.stripColors(Strings.stripGlyphs(name)).trim();
  }
  
  /** Verify player equality using their uuid. */
  public static boolean equals(Player player, Player other) {
    return player != null && (player == other || (Config.strict.bool() && player.uuid().equals(other.uuid())));
  }
  
  /** Verify player equality using their uuid. */
  public static arc.func.Boolf<Player> equals(Player player) {
    boolean strict = Config.strict.bool();
    return player == null ? p -> false : p -> player == p || (strict && (player.uuid().equals(p.uuid())));
  }
  
  
  /** Result of a player search. (pooled, so must be freed after use) */
  public static class SearchResult implements Pool.Poolable {
    public Player player;
    public String[] rest;
    public boolean found;
    
    public SearchResult set(Player player, String argsRest) {
      String[] args = argsRest.trim().split(" ");
      this.player = player;
      this.rest = args.length == 1 && args[0].isEmpty() ? new String[]{} : args;
      this.found = this.player != null;
      return this;
    }
    
    @Override
    public void reset() {
      player = null;
      rest = null;
      found = false;
    }    
    
    public void free() {
      resultPool.free(this);
    }
  }
  
  /** Class that helps comparing players by nickname without normalize them multiple times */
  static class SortElement implements Pool.Poolable {
    public Player player;
    public String strippedName, strippedInfoName;
    public int strippedNameSize = -1, strippedInfoNameSize = -1;
    
    /** A space will be added at end of @{@link #strippedName} and {@link #strippedInfoName} to helps the comparison. */
    public SortElement set(Player player) {
      this.player = player;
      this.strippedName = normalizeName(player.name) + " ";
      this.strippedInfoName = player.isLocal() ? null : normalizeName(player.getInfo().lastName) + " ";
      this.strippedNameSize = strippedName.length();
      this.strippedInfoNameSize = player.isLocal() ? -1 : strippedInfoName.length();
      return this;
    }
    
    /** A space must be added at end of {@code args}. */
    public SearchResult find(String args) {
      // First search using the player name, and after, 
      // using the player info name (in case of the server changed his name)
      if (args.startsWith(strippedName)) 
        return resultPool.obtain().set(player, args.substring(strippedNameSize));
      else if (strippedInfoName != null && args.startsWith(strippedInfoName)) 
        return resultPool.obtain().set(player, args.substring(strippedInfoNameSize));
      else 
        return null;
    }

    @Override
    public void reset() {
      player = null;
      strippedName = strippedInfoName = null;
      strippedNameSize = strippedInfoNameSize = -1;
    }
  }
}