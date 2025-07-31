# Player Follow
Allow players to follow each others.

Use the ``/follow [player-name|#unitID|UUID] [mode...]`` command to follow a player by his name or his UUID. <br>
Or without arguments to un-follow it.

For admins, the ``/follow-mode [mode] ['force']`` command can be used to change the default follow mode or, without arguments, to display the current default mode and available modes. <br>
The **'force'** argument will change the mode of all currently followed players, instead of only changing the default mode for newly followed players.

Another admin command is ``/follow-stop [player|#unitID|UUID...]``, which can be used to stop a player from being followed by other players, for annoying reasons or others.


### Building
Pre-build releases can be found in the [releases section](https://github.com/ZetaMap/player-follow/releases). <br>
But if you want to build the plugin yourself, you can run the command ``./gradlew :build``.


### Contributors
All the follow modes has been develops in Python, with the help of @xorblo-doitus, and ported to Java by me. <br>
Original repo: https://github.com/xorblo-doitus/queue_leu_leu
