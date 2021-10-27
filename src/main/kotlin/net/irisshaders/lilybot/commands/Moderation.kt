@file:OptIn(ExperimentalTime::class)

package net.irisshaders.lilybot.commands

import com.kotlindiscord.kord.extensions.DISCORD_BLACK
import com.kotlindiscord.kord.extensions.DISCORD_BLURPLE
import com.kotlindiscord.kord.extensions.DISCORD_GREEN
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.*
import com.kotlindiscord.kord.extensions.components.components
import com.kotlindiscord.kord.extensions.components.ephemeralButton
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.ban
import dev.kord.core.behavior.channel.GuildMessageChannelBehavior
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.getChannelOf
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.rest.builder.message.create.embed
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import net.irisshaders.lilybot.database.DatabaseManager
import net.irisshaders.lilybot.utils.ACTION_LOG
import net.irisshaders.lilybot.utils.MODERATORS
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.replace
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import kotlin.system.exitProcess
import kotlin.time.ExperimentalTime

/**
 * @author NoComment1105
 * @author IMS212
 */
class Moderation : Extension() {
    override val name = "moderation"

    override suspend fun setup() {
        // Clear command
        ephemeralSlashCommand(::ClearArgs) {  // Ephemeral slash commands have private responses
            name = "clear"
            description = "Clears messages."

            allowRole(MODERATORS)

            action {
                val actionLog = guild?.getChannel(ACTION_LOG) as GuildMessageChannelBehavior
                val messageAmount = arguments.messages
                val messageHolder = arrayListOf<Snowflake>()
                val textChannel = channel as GuildMessageChannelBehavior

                channel.getMessagesBefore(channel.messages.last().id, Integer.min(messageAmount, 100)).filterNotNull()
                    .onEach {
                        messageHolder.add(it.id)
                    }.catch {
                        it.printStackTrace()
                        println("error")
                    }.collect()

                textChannel.bulkDelete(messageHolder)

                respond {
                    embed {
                        color = DISCORD_BLACK
                        title = "$messageAmount messages have been cleared."
                        description = "Action occurred in ${textChannel.mention}."
                        timestamp = Clock.System.now()
                    }
                }

                actionLog.createEmbed {
                    color = DISCORD_BLACK
                    title = "$messageAmount messages have been cleared by ${user.asUser().username}."
                    description = "Action occurred in ${textChannel.mention}."
                    timestamp = Clock.System.now()
                }
            }
        }

        //Ban command
        ephemeralSlashCommand(::BanArgs) {  // Ephemeral slash commands have private responses
            name = "ban"
            description = "Bans a user."

            allowRole(MODERATORS)

            action {
                val actionLog = guild?.getChannel(ACTION_LOG) as GuildMessageChannelBehavior

                guild?.ban(arguments.userArgument.id, builder = {
                    this.reason = "Requested by " + user.asUser().username
                    this.deleteMessagesDays = arguments.messages
                })

                respond {
                    embed {
                        color = DISCORD_BLACK
                        title = "Banned a user"
                        description = "Banned ${arguments.userArgument.mention}!"
                        timestamp = Clock.System.now()
                    }
                }

                actionLog.createEmbed {
                    color = DISCORD_BLACK
                    title = "Banned a user"
                    description = "${user.asUser().username} banned ${arguments.userArgument.mention}!"
                    timestamp = Clock.System.now()
                }
            }
        }

        // Unban command
        ephemeralSlashCommand(::UnbanArgs) { // Ephemeral slash commands have private responses
            name = "unban"
            description = "Unbans a user"

            allowRole(MODERATORS)

            action {
                val actionLog = guild?.getChannel(ACTION_LOG) as GuildMessageChannelBehavior

                guild?.unban(arguments.userArgument.id)

                respond {
                    embed {
                        color = DISCORD_GREEN
                        title = "Unbanned a user"
                        description = "Unbanned ${arguments.userArgument.mention}!"
                        timestamp = Clock.System.now()
                    }
                }

                actionLog.createEmbed {
                    color = DISCORD_GREEN
                    title = "Unbanned a user"
                    description = "${user.asUser().username} unbanned ${arguments.userArgument.mention}!"
                    timestamp = Clock.System.now()
                }
            }
        }

        //Soft ban command
        ephemeralSlashCommand(::SoftBanArgs) {
            name = "softban"
            description = "Softbans a user"

            allowRole(MODERATORS)

            action {
                val actionLog = guild?.getChannel(ACTION_LOG) as GuildMessageChannelBehavior

                guild?.ban(arguments.userArgument.id, builder = {
                    this.reason = "Soft ban requested by ${user.asUser().username}"
                    this.deleteMessagesDays = arguments.messages
                })

                respond {
                    embed {
                        color = DISCORD_BLACK
                        title = "Soft-banned a user"
                        description = "Soft-banned ${arguments.userArgument.mention}!"
                        timestamp = Clock.System.now()
                    }
                }

                actionLog.createEmbed {
                    color = DISCORD_BLACK
                    title = "Soft-banned a user"
                    description = "Soft-banned ${arguments.userArgument.mention}"
                    timestamp = Clock.System.now()
                }

                guild?.unban(arguments.userArgument.id)
            }
        }

        //Kick command
        ephemeralSlashCommand(::KickArgs) {  // Ephemeral slash commands have private responses
            name = "kick"
            description = "Kicks a user."

            allowRole(MODERATORS)

            action {
                val actionLog = guild?.getChannel(ACTION_LOG) as GuildMessageChannelBehavior

                guild?.kick(arguments.userArgument.id, "Requested by " + user.asUser().username)

                respond {
                    embed {
                        color = DISCORD_BLACK
                        title = "Kicked a user"
                        description = "Kicked ${arguments.userArgument.mention}!"
                        timestamp = Clock.System.now()
                    }
                }

                actionLog.createEmbed {
                    color = DISCORD_BLACK
                    title = "Kicked a user"
                    description = "Kicked ${arguments.userArgument.mention}!"
                    timestamp = Clock.System.now()
                }
            }
        }

        ephemeralSlashCommand(::SayArgs) {
            name = "say"
            description = "Say something through Lily."

            allowRole(MODERATORS)

            action {
                val actionLog = guild?.getChannel(ACTION_LOG) as GuildMessageChannelBehavior

                if (arguments.embedMessage) {
                    channel.createEmbed {
                        color = DISCORD_BLURPLE
                        description = arguments.messageArgument
                        timestamp = Clock.System.now()
                    }
                } else {
                    channel.createMessage {
                        content = arguments.messageArgument
                    }
                }

                respond { content = "Command used" }

                actionLog.createEmbed {
                    color = DISCORD_BLACK
                    title = "Message sent"
                    description = "${user.asUser().username} used /say to say ${arguments.messageArgument} "
                    timestamp = Clock.System.now()
                }
            }
        }

        //Shutdown command
        ephemeralSlashCommand {  // Ephemeral slash commands have private responses
            name = "shutdown"
            description = "Shuts down the bot."

            allowRole(MODERATORS)

            action {
                respond {
                    embed {
                        title = "Shutdown"
                        description = "Are you sure you would like to shut down?"
                    }

                    components {
                        ephemeralButton {
                            label = "Yes"
                            style = ButtonStyle.Success

                            action {
                                respond { content = "Shutting down..." }
                                kord.shutdown()
                                exitProcess(0)
                            }
                        }

                        ephemeralButton {
                            label = "No"
                            style = ButtonStyle.Danger

                            action {
                                respond { content = "Shutdown aborted." }
                            }
                        }
                    }
                }
            }
        }

        ephemeralSlashCommand(::WarnArgs) {
            name = "warn"
            description = "Warn a member for any infractions."

            allowRole(MODERATORS)

            action {
                val userId = arguments.userArgument.id.asString
                val userTag = arguments.userArgument.tag
                val warnPoints = arguments.warnPoints
                val actionLog = guild!!.getChannelOf<GuildMessageChannel>(ACTION_LOG)
                var databasePoints: String? = null

                newSuspendedTransaction {
                    DatabaseManager.Warn.insertIgnore {
                        it[id] = userId
                        it[points] = "0"
                    }

                    databasePoints = DatabaseManager.Warn.select {
                        DatabaseManager.Warn.id eq userId
                    }.single()[DatabaseManager.Warn.points]

                    DatabaseManager.Warn.replace {
                        it[id] = id
                        it[points] = (warnPoints + Integer.parseInt(databasePoints)).toString()
                    }
                }

                respond {
                    embed {
                        title = "$userTag was warned with $warnPoints points."
                        color = DISCORD_BLACK

                        field {
                            name = "Total Points:"
                            value = databasePoints.toString()
                            inline = false
                        }

                        field {
                            name = "Points added:"
                            value = warnPoints.toString()
                            inline = false
                        }
                    }
                }

                actionLog.createEmbed {
                    title = "${arguments.userArgument.tag} was warned with ${arguments.warnPoints} points."
                    color = DISCORD_BLACK
                    field {
                        name = "Total Points:"
                        // value =
                        inline = false
                    }
                    field {
                        name = "Points added:"
                        // value =
                        inline = false
                    }
                }
            }
        }
    }

    inner class ClearArgs : Arguments() {
        val messages by int("messages", "Messages")
    }

    inner class KickArgs : Arguments() {
        val userArgument by user("kickUser", "Person to kick")
    }

    inner class BanArgs : Arguments() {
        val userArgument by user("banUser", "Person to ban")
        val messages by int("messages", "Messages")
    }

    inner class UnbanArgs : Arguments() {
        val userArgument by user("unbanUserId", "Person Unbanned")
    }

    inner class SoftBanArgs : Arguments() {
        val userArgument by user("softBanUser", "Person to Soft ban")
        val messages by defaultingInt("messages", "Messages", 3)
    }

    inner class MuteArgs : Arguments() {
        val userArgument by user("muteUser", "Person to mute")
        val duration by defaultingInt("duration", "Duration of Mute", 6)

        val reason by defaultingString(
            "reason",
            "Reason for Mute",
            "No Reason Provided"
        )
    }

    inner class WarnArgs : Arguments() {
        val userArgument by user("warnUser", "Person to Warn")
        val warnPoints by defaultingInt("points", "Amount of points to add", 10)

        val reason by defaultingString(
            "reason",
            "Reason for Warn",
            "No Reason Provided"
        )
    }

    inner class SayArgs : Arguments() {
        val messageArgument by string("message", "Message contents")
        val embedMessage by boolean("embed", "Would you like to send as embed")
    }
}