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

import arc.math.geom.Vec2;
import arc.struct.Seq;

import mindustry.gen.Player;


public abstract class Follower {
  public static final java.util.concurrent.ExecutorService exec = arc.util.Threads.unboundedExecutor("PlayerFollow", 1);
  public static final Seq<Follower> follows = new Seq<>();
  public static int loopSleep = 10; //ms

  public Seq<Player> following = new Seq<>();
  public volatile boolean taskRunning = true;
  
  public final Player followed;
  public final Vec2 leader = new Vec2();
  public final java.util.concurrent.Future<?> task;
  public final Object lock = new Object();
  
  public Follower(Player player) {
    followed = player;
    task = exec.submit(() -> {
      Player follower;
      Vec2 dest;
      
      while (taskRunning) {
        try { Thread.sleep(loopSleep); } 
        catch (InterruptedException e) { break; }

        synchronized (lock) {
          leader.set(followed);
          updatePos();
          
          for (int i=0; i<following.size; i++) {
            // Stop the task if requested
            if (!taskRunning) break;
            // if is null, probably leaved the game
            else if ((follower = following.get(i)) == null) continue;
            // if followed player leaved, stop the task
            else if (followed == null) break;
            
            dest = updateFollower(i, follower);
            follower.unit().set(dest);
            follower.set(dest);
            mindustry.gen.Call.setPosition(follower.con, dest.x, dest.y);              
          }          
        }
      }   
      
      taskRunning = false;
    });
  }
  
  public boolean stop() {
    if (!taskRunning && task.isDone()) return true;

    synchronized (lock) {
      taskRunning = false;
    }

    // Wait a little
    try { task.get(500, java.util.concurrent.TimeUnit.MILLISECONDS); } 
    catch (Exception ex) {}
    
    if (!task.isDone()) {
      synchronized (lock) {
        task.cancel(true);
      }

      // Wait a little
      try { task.get(200, java.util.concurrent.TimeUnit.MILLISECONDS); } 
      catch (Exception ex) {}
    }
   
    return task.isDone();
  }
  
  public void addFollower(Player player) {
    synchronized (lock) {
      following.add(player);
    }
  }
  
  public boolean removeFollower(Player player) {
    synchronized (lock) {
      return following.remove(player);
    }
  }
  
  public boolean removeFollower(arc.func.Boolf<Player> pred) {
    synchronized (lock) {
      for(int i = 0; i < following.size; i++){
        if (pred.get(following.get(i))) {
          following.remove(i);
          return true;
        }
      }
      return false;      
    }
  }
  
  /** Can be overridden to do things before computing followers positions */
  public void updatePos() {}

  public abstract Vec2 updateFollower(int index, Player player);
}
