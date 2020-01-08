import io.kamax.matrix.client.MatrixPasswordCredentials
import io.kamax.matrix.client._MatrixClient
import io.kamax.matrix.client._SyncData
import io.kamax.matrix.client.regular.MatrixHttpClient
import io.kamax.matrix.client.regular.SyncOptions
import io.kamax.matrix.hs._MatrixRoom
import io.kamax.matrix.json.event.MatrixJsonRoomMessageEvent
import net.engio.mbassy.listener.Handler
import org.kitteh.irc.client.library.Client
import org.kitteh.irc.client.library.event.channel.ChannelJoinEvent
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent
import java.io.File

const val EVENT_TYPE_MESSAGE = "m.room.message"
const val TOKEN_FILE_NAME = "token.txt"
const val CREDENTIALS_FILE_NAME = "credentials.txt"
const val IRC_CHANNEL_FILE_NAME = "irc_channel.txt"
const val IRC_USER_NAME_SUFFIX = "@matrix"
const val IRC_USER_NAME = "the_future"
const val DELIMITER = ":"

private val users = hashMapOf<String, Client>()
private var matrixUserName: String? = null
private var ircChannelName: String? = null
private var ircServerAddress: String? = null

private var ircClient: Client? = null
private var matrixClient: MatrixHttpClient? = null
private var matrixRoomId: String = ""

fun main(args: Array<String>) {
    val credentialsFileContent = File(CREDENTIALS_FILE_NAME).readText().trim()
    matrixUserName = credentialsFileContent.split(DELIMITER)[0]
    val matrixPassword = credentialsFileContent.split(DELIMITER)[1]

    val ircFileContent = File(IRC_CHANNEL_FILE_NAME).readText().trim()
    ircChannelName = ircFileContent.split(DELIMITER)[0]
    ircServerAddress = ircFileContent.split(DELIMITER)[1]

    matrixClient = MatrixHttpClient("matrix.org")
    matrixClient?.apply {
        discoverSettings()
        login(MatrixPasswordCredentials(matrixUserName, matrixPassword))
    }

    ircClient = Client.builder()
        .nick(IRC_USER_NAME)
        .server()
        .host(ircServerAddress!!)
        .then()
        .buildAndConnect()
    ircClient?.apply {
        eventManager.registerEventListener(ChannelJoinEventListener())
        eventManager.registerEventListener(ChannelMessageEventListener())
        addChannel(ircChannelName)
    }

    val tokenFile = File(TOKEN_FILE_NAME)
    val tokenFileContent = tokenFile.readText().trim()
    var syncToken: String? = if (tokenFileContent.isNotEmpty()) tokenFileContent else null

    // We sync until the process is interrupted via Ctrl+C or a signal
    matrixClient?.run {
        while (!Thread.currentThread().isInterrupted) {
            val data = this.sync(SyncOptions.build().setSince(syncToken).get())

            data.rooms.joined.forEach { joinedRoom ->
                matrixRoomId = joinedRoom.id
                val room = this.getRoom(joinedRoom.id)

                processRoomUsers(room)
                processRoomMessages(this, joinedRoom)
            }

            syncToken = data.nextBatchToken()
            tokenFile.writeText(syncToken!!)
        }
    }
}

class ChannelJoinEventListener {
    @Handler
    fun onUserJoinChannel(event: ChannelJoinEvent) {
        println("User ${event.actor.nick} joined the IRC channel")
        if (!event.actor.nick.contains(IRC_USER_NAME_SUFFIX)) {
            // legacy user joined, forward to matrix
            //matrixClient?.getRoom(matrixRoomId)?.sendText("${event.actor.nick} joined the legacy channel.")
        }
    }
}

class ChannelMessageEventListener {
    @Handler
    fun onChannelMessage(event: ChannelMessageEvent) {
        println("Processing IRC channel message \"${event.message}\" from user \"${event.actor.nick}\"")
        if (!event.actor.nick.contains(IRC_USER_NAME_SUFFIX)) {
            println("Is a message from legacy user, forwarding to matrix")
            // message from legacy user, forward to matrix
            matrixClient?.getRoom(matrixRoomId)?.sendText("${event.actor.nick}: ${event.message}")
        }
    }
}

fun processRoomUsers(room: _MatrixRoom) {
    room.joinedUsers.forEach { userProfile ->
        if (userProfile.name.isPresent && !users.containsKey(userProfile.name.get())) {
            val userName = userProfile.name.get()
            println("added new user \"${userName}\"")

            val client = Client.builder()
                .nick(userName + IRC_USER_NAME_SUFFIX)
                .server()
                .host(ircServerAddress!!)
                .then()
                .buildAndConnect()
            client.addChannel(ircChannelName)

            users[userName] = client
        }
    }
}

fun processRoomMessages(client: _MatrixClient, joinedRoom: _SyncData.JoinedRoom) {
    val room = client.getRoom(joinedRoom.id)
    println("checking room \"${room.name.get()}\"")

    joinedRoom.timeline.events.forEach { event ->
        if (event.type == EVENT_TYPE_MESSAGE) {
            val message = MatrixJsonRoomMessageEvent(event.json)
            val content = message.body
            val userName = message.sender.id
            val shortName = userName.split(":").first().removePrefix("@")
            if (shortName == matrixUserName) {
                println("ignoring message \"$content\" from myself.")
            } else {
                println("processed message \"$content\" from user \"$shortName\"")
                forwardMessageToIRC(shortName, content)
            }
        }
    }
}

fun forwardMessageToIRC(userName: String, content: String) {
    println("Trying to send message \"$content\" to IRC channel $ircChannelName")
    users[userName]?.run {
        this.sendMessage(ircChannelName!!, content)
        println("Successfully sent message \"$content\" to IRC channel $ircChannelName from user $userName")
    }
}