/*
 * Copyright 2020-2022 RW-HPS Team and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/RW-HPS/RW-HPS/blob/master/LICENSE
 */

package com.github.dr.rwserver.net.netconnectprotocol.realize

import com.github.dr.rwserver.Main
import com.github.dr.rwserver.core.Call
import com.github.dr.rwserver.core.thread.TimeTaskData
import com.github.dr.rwserver.data.global.Data
import com.github.dr.rwserver.data.global.NetStaticData
import com.github.dr.rwserver.data.player.Player
import com.github.dr.rwserver.game.event.EventType.*
import com.github.dr.rwserver.io.GameInputStream
import com.github.dr.rwserver.io.GameOutputStream
import com.github.dr.rwserver.io.output.CompressOutputStream
import com.github.dr.rwserver.io.packet.GameCommandPacket
import com.github.dr.rwserver.io.packet.Packet
import com.github.dr.rwserver.net.core.ConnectionAgreement
import com.github.dr.rwserver.net.core.server.AbstractNetConnect
import com.github.dr.rwserver.net.core.server.AbstractNetConnectServer
import com.github.dr.rwserver.util.ExtractUtil
import com.github.dr.rwserver.util.IsUtil
import com.github.dr.rwserver.util.PacketType
import com.github.dr.rwserver.util.RandomUtil
import com.github.dr.rwserver.util.alone.annotations.MainProtocolImplementation
import com.github.dr.rwserver.util.encryption.Game
import com.github.dr.rwserver.util.game.CommandHandler
import com.github.dr.rwserver.util.game.CommandHandler.CommandResponse
import com.github.dr.rwserver.util.game.Events
import com.github.dr.rwserver.util.log.Log
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.min

/**
 * @author Dr
 * @date 2020/9/5 17:02:33
 */

@MainProtocolImplementation
open class GameVersionServer(connectionAgreement: ConnectionAgreement) : AbstractNetConnect(connectionAgreement), AbstractNetConnectServer {
    protected val supportedVersion: Int = 151
    
    private val sync = ReentrantLock(true)

    /** 玩家连接校验 */
    private var connectKey: String? = null

    /** 玩家  */
    override lateinit var player: Player

    override val version: String
        get() = "1.14 RW-HPS"

    override fun sendSystemMessage(msg: String) {
        if (!player.noSay) {
            try {
                sendPacket(NetStaticData.protocolData.abstractNetPacket.getSystemMessagePacket(msg))
            } catch (e: IOException) {
                Log.error("[Player] Send System Chat Error", e)
            }
        }
    }

    override fun sendChatMessage(msg: String, sendBy: String, team: Int) {
        try {
            sendPacket(NetStaticData.protocolData.abstractNetPacket.getChatMessagePacket(msg, sendBy, team))
        } catch (e: IOException) {
            Log.error("[Player] Send Player Chat Error", e)
        }
    }

    @Throws(IOException::class)
    override fun sendServerInfo(utilData: Boolean) {
        val o = GameOutputStream()
        o.writeString(Data.SERVER_ID)
        o.writeInt(supportedVersion)
        /* 地图 */
        o.writeInt(Data.game.maps.mapType.ordinal)
        o.writeString(Data.game.maps.mapPlayer + Data.game.maps.mapName)
        o.writeInt(Data.game.credits)
        o.writeInt(Data.game.mist)
        o.writeBoolean(true)
        o.writeInt(1)
        o.writeByte(7)
        o.writeBoolean(false)
        /* Admin Ui */
        o.writeBoolean(player.isAdmin)
        o.writeInt(Data.config.MaxUnit)
        o.writeInt(Data.config.MaxUnit)
        o.writeInt(Data.game.initUnit)
        o.writeFloat(Data.game.income)
        /* NO Nukes */
        o.writeBoolean(Data.game.noNukes)
        o.writeBoolean(false)
        o.writeBoolean(utilData)
        if (utilData) {
            o.flushEncodeData(Data.utilData)
        }

        /* 共享控制 */
        o.writeBoolean(Data.game.sharedControl)
        o.writeBoolean(false)
        o.writeBoolean(false)
        // 允许观众
        o.writeBoolean(true)
        o.writeBoolean(false)
        sendPacket(o.createPacket(PacketType.PACKET_SERVER_INFO))
    }

    override fun sendTeamData(gzip: CompressOutputStream) {
        try {
            val o = GameOutputStream()
            /* Player position */
            o.writeInt(player.site)
            o.writeBoolean(Data.game.isStartGame)
            /* Largest player */
            o.writeInt(Data.game.maxPlayer)
            o.flushEncodeData(gzip)
            /* 迷雾 */
            o.writeInt(Data.game.mist)
            o.writeInt(Data.game.credits)
            o.writeBoolean(true)
            /* AI Difficulty ?*/
            o.writeInt(1)
            o.writeByte(5)
            o.writeInt(Data.config.MaxUnit)
            o.writeInt(Data.config.MaxUnit)
            /* 初始单位 */
            o.writeInt(Data.game.initUnit)
            /* 倍速 */
            o.writeFloat(Data.game.income)
            /* NO Nukes */
            o.writeBoolean(Data.game.noNukes)
            o.writeBoolean(false)
            o.writeBoolean(false)
            /* 共享控制 */
            o.writeBoolean(Data.game.sharedControl)
            /* 游戏暂停 */
            o.writeBoolean(false)
            sendPacket(o.createPacket(PacketType.PACKET_TEAM_LIST))
        } catch (e: IOException) {
            Log.error("Team", e)
        }
    }

    override fun sendSurrender() {
        Data.game.playerManage.updateControlIdentifier()
        try {
            val out = GameOutputStream()

            // Player Site
            out.writeByte(player.site)

            // Is Unit Action
            out.writeBoolean(false)

            // unknown booleans
            out.writeBoolean(false)
            // Is Cancel the operation
            out.writeBoolean(false)

            // unknown
            out.writeInt(-1)
            out.writeInt(-1)
            out.writeBoolean(false)
            out.writeBoolean(false)

            // Unit count
            out.writeInt(0)

            out.writeBoolean(false)
            out.writeBoolean(false)

            out.writeLong(-1)
            // build Unit
            out.writeString("-1")

            out.writeBoolean(false)
            // multi person control check code
            out.writeShort(Data.game.playerManage.sharedControlPlayer.toShort())

            // System action
            out.writeBoolean(true)
            out.writeByte(0)
            out.writeFloat(0)
            out.writeFloat(0)
            // action type
            out.writeInt(100)

            // Unit Count
            out.writeInt(0)
            // unknown
            out.writeBoolean(false)

            val cmd = GameCommandPacket(player.site, out.getByteArray())
            Data.game.gameCommandCache.offer(cmd)
            Call.sendSystemMessage(Data.i18NBundle.getinput("player.surrender", player.name))
        } catch (ignored: Exception) {
        }
    }

    override fun sendKick(reason: String) {
        val o = GameOutputStream()
        o.writeString(reason)
        sendPacket(o.createPacket(PacketType.PACKET_KICK))
        disconnect()
    }

    override fun sendPing() {
        try {
            sendPacket(NetStaticData.protocolData.abstractNetPacket.getPingPacket(player))
        } catch (e: IOException) {
            numberOfRetries++
        }
    }

    @Throws(IOException::class)
    override fun sendStartGame() {
        sendServerInfo(true)
        sendPacket(NetStaticData.protocolData.abstractNetPacket.getStartGamePacket())
        if (IsUtil.notIsBlank(Data.config.StartAd)) {
            sendSystemMessage(Data.config.StartAd)
        }
    }

    @Throws(IOException::class)
    override fun receiveChat(p: Packet) {
        GameInputStream(p).use { stream ->
            val message: String = stream.readString()
            var response: CommandResponse? = null

            Log.clog("[{0}]: {1}", player.name, message)

            // Afk Stop
            if (player.isAdmin && TimeTaskData.PlayerAfkTask != null) {
                TimeTaskData.stopPlayerAfkTask()
                Call.sendSystemMessage(Data.i18NBundle.getinput("afk.clear", player.name))
            }

            // Msg Command
            if (message.startsWith(".") || message.startsWith("-") || message.startsWith("_")) {
                val strEnd = min(message.length, 3)
                // 提取出消息前三位 判定是否为QC命令
                response = if ("qc" == message.substring(1, strEnd)) {
                    Data.CLIENT_COMMAND.handleMessage("/" + message.substring(5), player)
                } else {
                    Data.CLIENT_COMMAND.handleMessage("/" + message.substring(1), player)
                }
            }

            if (response == null || response.type == CommandHandler.ResponseType.noCommand) {
                if (message.length > Data.config.MaxMessageLen) {
                    sendSystemMessage(Data.i18NBundle.getinput("message.maxLen"))
                    return
                }
                Data.core.admin.filterMessage(player, message)?.let { filterMessage: String ->
                    Call.sendMessage(player, filterMessage)
                    Events.fire(PlayerChatEvent(player, filterMessage))
                }
            } else if (response.type != CommandHandler.ResponseType.valid) {
                val text: String =  when (response.type) {
                    CommandHandler.ResponseType.manyArguments -> {
                        "Too many arguments. Usage: " + response.command.text + " " + response.command.paramText
                    }
                    CommandHandler.ResponseType.fewArguments -> {
                        "Too few arguments. Usage: " + response.command.text + " " + response.command.paramText
                    }
                    else -> {
                        "Unknown command. Check .help"
                    }
                }
                player.sendSystemMessage(text)
            } else {
                //
            }
        }
    }

    @Throws(IOException::class)
    override fun receiveCommand(p: Packet) {
        //PlayerOperationUnitEvent
        sync.lock()
        try {
            GameInputStream(GameInputStream(p).getDecodeBytes()).use { inStream ->
                val outStream = GameOutputStream()
                outStream.writeByte(inStream.readByte())
                val boolean1 = inStream.readBoolean()
                outStream.writeBoolean(boolean1)
                if (boolean1) {
                    outStream.writeInt(inStream.readInt())
                    val int1 = inStream.readInt()
                    //Log.error(int1)
                    outStream.writeInt(int1)
                    if (int1 == -2) {
                        val nameUnit = inStream.readString()
                        //Log.error(nameUnit)
                        outStream.writeString(nameUnit)
                    }
                    outStream.transferToFixedLength(inStream,28)
                    outStream.writeIsString(inStream)
                }
                outStream.transferToFixedLength(inStream,10)
                val boolean3 = inStream.readBoolean()
                outStream.writeBoolean(boolean3)
                if (boolean3) {
                    outStream.transferToFixedLength(inStream,8)
                }
                outStream.writeBoolean(inStream.readBoolean())
                val int2 = inStream.readInt()
                outStream.writeInt(int2)
                for (i in 0 until int2) {
                    outStream.transferToFixedLength(inStream,8)
                }
                val boolean4 = inStream.readBoolean()
                outStream.writeBoolean(boolean4)
                if (boolean4) {
                    outStream.writeByte(inStream.readByte())
                }
                val boolean5 = inStream.readBoolean()
                outStream.writeBoolean(boolean5)
                if (boolean5) {
                    if (player.getData<String>("Summon") != null) {
                        gameSummon(player.getData<String>("Summon")!!,inStream.readFloat(),inStream.readFloat())
                        player.removeData("Summon")
                        return
                    } else {
                        outStream.transferToFixedLength(inStream,8)
                    }
                }
                outStream.transferToFixedLength(inStream,8)
                outStream.writeString(inStream.readString())
                //outStream.writeBoolean(inStream.readBoolean())
                outStream.writeByte(inStream.readByte())
                inStream.readShort()
                outStream.writeShort(Data.game.playerManage.sharedControlPlayer.toShort())
                outStream.transferTo(inStream)
                Data.game.gameCommandCache.offer(GameCommandPacket(player.site, outStream.getPacketBytes()))
            }
        } catch (e: Exception) {
            Log.error(e)
        } finally {
            sync.unlock()
        }
    }

    @Throws(IOException::class)
    override fun getGameSaveData(packet: Packet): ByteArray {
        GameInputStream(packet).use { stream ->
            val o = GameOutputStream()
            o.writeByte(stream.readByte())
            o.writeInt(stream.readInt())
            o.writeInt(stream.readInt())
            o.writeFloat(stream.readFloat())
            o.writeFloat(stream.readFloat())
            o.writeBoolean(false)
            o.writeBoolean(false)
            stream.readBoolean()
            stream.readBoolean()
            stream.readString()
            return stream.readStreamBytes()
        }
    }

    //@Throws(IOException::class)
    override fun getPlayerInfo(p: Packet): Boolean {
        try {
            GameInputStream(p).use { stream ->
                stream.readString()
                Log.debug(stream.readInt())
                Log.debug(stream.readInt())
                Log.debug(stream.readInt())
                var name = stream.readString()
                Log.debug("name", name)
                val passwd = stream.isReadString()
                Log.debug("passwd", passwd)
                stream.readString()
                val uuid = stream.readString()
                Log.debug("uuid", uuid)
                Log.debug("?", stream.readInt())
                val token = stream.readString()
                Log.debug("token", token)
                Log.debug(token, connectKey!!)

                /*
                if (!token.equals(playerConnectKey)) {
                    sendKick("You Open Mod?");
                    return false;
                }*/

                val playerConnectPasswdCheck = PlayerConnectPasswdCheckEvent(this, passwd)
                Events.fire(playerConnectPasswdCheck)
                if (playerConnectPasswdCheck.result) {
                    return true
                }
                if (IsUtil.notIsBlank(playerConnectPasswdCheck.name)) {
                    name = playerConnectPasswdCheck.name
                }

                val playerJoinName = PlayerJoinNameEvent(name)
                Events.fire(playerJoinName)
                if (IsUtil.notIsBlank(playerJoinName.resultName)) {
                    name = playerJoinName.resultName
                }

                inputPassword = false
                val re = AtomicBoolean(false)
                if (Data.game.isStartGame) {
                    Data.game.playerManage.playerAll.each({ i: Player -> i.uuid == uuid }) { e: Player ->
                        re.set(true)
                        this.player = e
                        player.con = this
                        Data.game.playerManage.playerGroup.add(e)
                    }
                    if (!re.get()) {
                        if (IsUtil.isBlank(Data.config.StartPlayerAd)) {
                            sendKick("游戏已经开局 请等待 # The game has started, please wait")
                        } else {
                            sendKick(Data.config.StartPlayerAd)
                        }
                        return false
                    }
                } else {
                    if (Data.game.playerManage.playerGroup.size() >= Data.game.maxPlayer) {
                        if (IsUtil.isBlank(Data.config.MaxPlayerAd)) {
                            sendKick("服务器没有位置 # The server has no free location")
                        } else {
                            sendKick(Data.config.MaxPlayerAd)
                        }
                        return false
                    }
                    val localeUtil = Data.i18NBundleMap["CN"]
                    /*
                    if (Data.game.ipCheckMultiLanguageSupport) {
                        val rec = Data.ip2Location.IPQuery(connectionAgreement.ip)
                        if ("OK" != rec.status) {
                            localeUtil = Data.localeUtilMap[rec.countryShort]
                        }
                    }
                     */
                    player = Data.game.playerManage.addPlayer(this, uuid, name, localeUtil)
                }

                player.sendTeamData()
                sendServerInfo(true)

                if (IsUtil.notIsBlank(Data.config.EnterAd)) {
                    sendSystemMessage(Data.config.EnterAd)
                }
                if (re.get()) {
                    reConnect()
                }

                connectionAgreement.add(NetStaticData.groupNet)

                Events.fire(PlayerJoinEvent(player))

                return true
            }
        } finally {
            connectKey = null
        }
    }

    @Throws(IOException::class)
    override fun registerConnection(p: Packet) {
        // 生成随机Key;
        val keyLen = 6
        val key = RandomUtil.getRandomIntString(keyLen).toInt()
        connectKey = Game.connectKey(key)
        GameInputStream(p).use { stream ->
            // Game Pkg Name
            stream.readString()
            // 返回都是1 有啥用
            stream.readInt()
            stream.readInt()
            stream.readInt()
            val o = GameOutputStream()
            o.writeString(Data.SERVER_ID)
            o.writeInt(1)
            o.writeInt(supportedVersion)
            o.writeInt(supportedVersion)
            o.writeString("com.corrodinggames.rts.server")
            o.writeString(Data.core.serverConnectUuid)
            o.writeInt(key)
            sendPacket(o.createPacket(PacketType.PACKET_REGISTER_CONNECTION))
        }
    }

    override fun gameSummon(unit: String, x: Float, y: Float) {
        sync.lock()
        try {
            Data.game.gameCommandCache.offer(NetStaticData.protocolData.abstractNetPacket.gameSummonPacket(player.site,unit,x, y))
        } catch (e: Exception) {
            Log.error(e)
        } finally {
            sync.unlock()
        }
    }

    override fun sendErrorPasswd() {
        try {
            val o = GameOutputStream()
            o.writeInt(0)
            sendPacket(o.createPacket(PacketType.PACKET_PASSWD_ERROR))
            inputPassword = true
        } catch (e: Exception) {
            Log.error("[Player] sendErrorPasswd", e)
        }
    }

    override fun disconnect() {
        if (super.isDis) {
            return
        }
        super.isDis = true
        if (this::player.isInitialized) {
            Data.game.playerManage.playerGroup.remove(player)
            // DisConnect
            player.ping = -1
            if (!Data.game.isStartGame) {
                Data.game.playerManage.playerAll.remove(player)
                player.clear()
                Data.game.playerManage.removePlayerArray(player.site)
            }
            Events.fire(PlayerLeaveEvent(player))
        }
        super.close(NetStaticData.groupNet)
    }

    override fun sendGameSave(packet: Packet) {
        sendPacket(packet)
    }

    override fun reConnect() {
        try {
            if (!Data.config.ReConnect) {
                sendKick("不支持重连 # Does not support reconnection")
                return
            }
            if (player.reConnectData.checkStatus()) {
                player.kickPlayer("不要一直尝试重连",300)
                return
            }
            player.reConnectData.count++
            super.isDis = false
            sendPacket(NetStaticData.protocolData.abstractNetPacket.getStartGamePacket())
            sync()
        } catch (e: Exception) {
            Log.error("[Player] Send GameSave ReConnect Error", e)
        }
    }

    override fun sync() {
        if (Data.game.gamePaused) {
            player.sendSystemMessage("目前已有同步任务 请等待")
            return
        }
        try {
            Data.game.gamePaused = true
            Call.sendSystemMessage("玩家同步中 请耐心等待 不要退出 期间会短暂卡住！！ 需要30s-60s")
            // 批量诱骗
            NetStaticData.groupNet.broadcast(NetStaticData.protocolData.abstractNetPacket.getDeceiveGameSave())

            try {
                synchronized(Data.game.gameSaveWaitObject) {
                    Data.game.gameSaveWaitObject.wait(1000 * 30)

                    try {
                        NetStaticData.groupNet.broadcast(Data.game.gameSaveCache!!.convertGameSaveDataPacket())
                    } catch (e: IOException) {
                        Log.error(e)
                    }
                }
            } catch (ex: Exception) {
                connectionAgreement.close(NetStaticData.groupNet)
            } finally {
                Data.game.gameSaveCache = null
                Data.game.gamePaused = false
            }
        } catch (e: Exception) {
            Log.error("[Player] Send GameSave ReConnect Error", e)
        }
    }

    /**
     * (WARN) This part temporarily refuses to provide analysis and needs to wait for the right time
     * @author Dr
     */
    override fun sendRelayServerInfo() {
        try {
            val bytes = ExtractUtil.hexToByteArray("00 00 00 00 01 00 00 00 97 00")
            sendPacket(Packet(163,bytes))
        } catch (e: Exception) {
            Log.error(e)
        }
    }

    /**
     * (WARN) This part temporarily refuses to provide analysis and needs to wait for the right time
     * @author Dr
     */
    override fun sendRelayServerCheck() {
        try {
            val bytes = ExtractUtil.hexToByteArray("00 00 00 01 00 00 00 01 00 00 00 06 52 57 2d 48 50 53 00 06 52 57 2d 48 50 53 00 00 00 01 00")
            sendPacket(Packet(151,bytes))
        } catch (e: Exception) {
            Log.error(e)
        }
    }

    /**
     * (WARN) This part temporarily refuses to provide analysis and needs to wait for the right time
     * @author Dr
     */
    override fun sendRelayServerId() {
        try {
            val bytes = ExtractUtil.hexToByteArray("01 01 01 01 00 24 33 30 34 66 30 34 30 34 2d 63 36 35 38 2d 34 65 33 34 2d 39 35 62 33 2d 39 39 36 66 65 32 62 36 36 61 38 65 01 00 01 00 28 7b 7b 52 57 2d 48 50 53 20 52 65 6c 61 79 7d 7d 2e 52 6f 6f 6d 20 49 44 20 3a 20 41 75 74 6f 20 52 65 61 64 20 4d 6f 64 01")
            sendPacket(Packet(170,bytes))
        } catch (e: Exception) {
            Log.error(e)
        }
    }

    /*

     */

    /**
     * (WARN) This part temporarily refuses to provide analysis and needs to wait for the right time
     * @author Dr
     */
    override fun sendRelayPlayerInfo() {
        try {
            val bytes = ExtractUtil.hexToByteArray("00 00 00 00 02 00 24 62 64 66 30 34 35 35 66 2d 30 62 66 35 2d 34 34 37 31 2d 39 61 65 39 2d 34  64 36 31 35 32 35 31 30 38 37 39 00")
            sendPacket(Packet(172, bytes))
            val bytes2 = ExtractUtil.hexToByteArray("00 00 00 02 00 00 00 34 00 00 00 2c 00 00 00 a0 00 19 63 6f 6d 2e 63 6f 72 72 6f 64 69 6e 67 67  61 6d 65 73 2e 72 74 73 2e 71 7a 00 00 00 03 00  00 00 97 00 00 00 01 00 00 02 00 00")
            sendPacket(Packet(174, bytes2))
        } catch (e: Exception) {
            Log.error(e)
        }
    }

    /**
     * (WARN) This part temporarily refuses to provide analysis and needs to wait for the right time
     * @author Dr
     */
    override fun sendRelayPlayerConnectPacket(packet: Packet) {
        try {
            GameInputStream(packet).use { inStream ->
                val o = GameOutputStream()
                inStream.skip(4)
                val dataOutputStream = inStream.getStream()
                dataOutputStream.readString()
                dataOutputStream.skip(12)
                dataOutputStream.readString()
                dataOutputStream.readString()

                val out = GameOutputStream()
                val bytes = ExtractUtil.hexToByteArray("00 16 63 6f 6d 2e 63 6f 72 72 6f 64 69 6e 67 67 61 6d 65 73 2e 72 74 73 00 00 00 04 00 00 00 97 00 00 00 97 00 0c 4d 6f 64 20 52 65 61 64 20 42 6f 74 00 00 1b 63 6f 6d 2e 63 6f 72 72 6f 64 69 6e 67 67 61 6d 65 73 2e 72 74 73 2e 6a 61 76 61 00 13 52 57 2d 48 50 53 20 4d 6f 64 20 52 65 61 64 20 42 6f 74 47 6e a1 5a")
                out.writeBytes(bytes)
                out.writeString(Game.connectKey(dataOutputStream.readInt()))
                val packet1Bytes = out.getPacketBytes()

                o.writeInt(2)
                o.writeInt(packet1Bytes.size+8)
                o.writeInt(packet1Bytes.size)
                o.writeInt(110)
                o.writeBytes(packet1Bytes)
                sendPacket(o.createPacket(174))
            }
        } catch (e: Exception) {
            Log.error(e)
        }
    }

    /**
     * (WARN) This part temporarily refuses to provide analysis and needs to wait for the right time
     * @author Dr
     */
    override fun getRelayUnitData(packet: Packet) {
        try {
            GameInputStream(packet).use { inStream ->
                inStream.readInt()
                when (inStream.readInt()) {
                    106 -> {
                        inStream.skip(4)
                        inStream.readString()
                        inStream.skip(8)
                        inStream.readString()
                        inStream.skip(8)
                        inStream.readBoolean()
                        inStream.skip(26)
                        inStream.readString()
                        //
                        val stream2 = DataInputStream(ByteArrayInputStream(inStream.readStreamBytes()))
                        stream2.readInt()
                        val count = stream2.readInt()
                        val data = StringBuilder()
                        Data.core.unitBase64.clear()
                        for (i in 0 until count) {
                            data.delete(0, data.length)
                            data.append(stream2.readUTF()).append("%#%").append(stream2.readInt())
                            stream2.readBoolean()
                            if (stream2.readBoolean()) {
                                data.append("%#%").append(stream2.readUTF())
                            }
                            stream2.skip(16)
                            Data.core.unitBase64.add(data.toString())
                        }
                        Data.config.OneReadUnitList = false
                        Data.core.save()
                        sendPacket(NetStaticData.protocolData.abstractNetPacket.getSystemMessagePacket("MOD读取完成，请重新进入"))
                        Main.loadUnitList()
                    }
                    161 -> sendRelayPlayerConnectPacket(packet)
                    else -> return
                }
            }
        } catch (e: Exception) {
            Log.error(e)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }

        if (player != (other as GameVersionServer).player) {
            return false
        }

        return false
    }

    override fun hashCode(): Int {
        return player.hashCode()
    }


}