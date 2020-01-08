import io.kamax.matrix.client.MatrixPasswordCredentials
import io.kamax.matrix.client._MatrixClient
import io.kamax.matrix.client._SyncData
import io.kamax.matrix.client.regular.MatrixHttpClient
import io.kamax.matrix.client.regular.SyncOptions
import io.kamax.matrix.hs._MatrixRoom
import io.kamax.matrix.json.event.MatrixJsonRoomMessageEvent

const val EVENT_TYPE_MESSAGE = "m.room.message"

val matrixUserName = ""
val matrixPassword = ""

val users = hashMapOf<String, Unit>()

fun main(args: Array<String>) {

    val client = MatrixHttpClient("matrix.org")
    client.discoverSettings()
    client.login(MatrixPasswordCredentials(matrixUserName, matrixPassword))

    // We will update this after each sync call
    var syncToken: String? = null

    // We sync until the process is interrupted via Ctrl+C or a signal
    while (!Thread.currentThread().isInterrupted) {
        val data = client.sync(SyncOptions.build().setSince(syncToken).get())

        data.rooms.joined.forEach { joinedRoom ->
            val room = client.getRoom(joinedRoom.id)

            processRoomUsers(room)
            processRoomMessages(client, joinedRoom)
        }

        syncToken = data.nextBatchToken();
    }
}


fun processRoomUsers(room: _MatrixRoom) {
    room.joinedUsers.forEach { userProfile ->
        if (userProfile.name.isPresent && !users.containsKey(userProfile.name.get())) {
            users[userProfile.name.get()] = Unit
            println("added new user \"${userProfile.name.get()}\"")
            // TODO: create IRC user here
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
                forwardMessageToIRC(userName, content)
            }
        }
    }
}

fun forwardMessageToIRC(userName: String, content: String) {
    // TODO: forward message via correct IRC user/session here
}