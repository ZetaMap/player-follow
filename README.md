# Player Follow
Allow players to follow each others. <br>
Use ``/follow [player-name|unitID|UUID] [mode...]`` command to follow a player by his name or his UUID and, without arguments, to un-follow it.

For admins, the ``/follow-mode [mode] [force]`` command can be used to change the default follow mode or, without arguments, display the current mode and available modes. And 'force' players to change the follow mode with the ``force`` argument.

Another command for admins is the ``/follow-stop [player|unitID|UUID...]`` command, which can be used to stop a player from being followed by other players, for annoying reasons or others.


### Feedback
Open an issue if you have a suggestion.


### Releases
Prebuild relases can be found [here](https://github.com/ZetaMap/player-follow/releases)


### Building a Jar 
Just execute the ``gradlew build`` command and the plugin will compile automatically.


### Installing
Simply place the output jar from the step above in your server's `config/mods` directory and restart the server. <br>
List your currently installed plugins by running the `mods` command.


### Contributors
All the follow modes has been develops in Python (for minimal example), with the help of @xorblo-doitus, and recoded in Java by me. <br>
There is the repo containing Python scripts: https://github.com/xorblo-doitus/queue_leu_leu
