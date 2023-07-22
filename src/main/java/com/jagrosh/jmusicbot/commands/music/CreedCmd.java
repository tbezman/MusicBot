/*
 * Copyright 2016 John Grosh <john.a.grosh@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot.commands.music;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.menu.ButtonMenu;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.commands.MusicCommand;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.exceptions.PermissionException;

import java.util.concurrent.TimeUnit;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class CreedCmd extends MusicCommand {

    private final static String LOAD = "\uD83D\uDCE5"; // 📥
    private final static String CANCEL = "\uD83D\uDEAB"; // 🚫

    public CreedCmd(Bot bot) {
        super(bot);
        this.name = "creed";
        this.help = "may the creed be with you";
        this.beListening = true;
        this.bePlaying = false;
        this.aliases = bot.getConfig().getAliases(this.name);
    }

    @Override
    public void doCommand(CommandEvent event) {
        event.reply("May the creed be with you", m ->
                bot.getPlayerManager().loadItemOrdered(event.getGuild(), "https://www.youtube.com/watch?v=2nN5evsWeZ4", new ResultHandler(m, event, false))
        );

    }

    public class ResultHandler implements AudioLoadResultHandler {
        private final Message m;
        private final CommandEvent event;
        private final boolean ytsearch;

        private ResultHandler(Message m, CommandEvent event, boolean ytsearch) {
            this.m = m;
            this.event = event;
            this.ytsearch = ytsearch;
        }

        private void loadSingle(AudioTrack track, AudioPlaylist playlist) {
            if (bot.getConfig().isTooLong(track)) {
                m.editMessage(FormatUtil.filter(event.getClient().getWarning() + " This track (**" + track.getInfo().title + "**) is longer than the allowed maximum: `"
                        + FormatUtil.formatTime(track.getDuration()) + "` > `" + FormatUtil.formatTime(bot.getConfig().getMaxSeconds() * 1000) + "`")).queue();
                return;
            }
            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
            int pos = handler.addTrack(new QueuedTrack(track, event.getAuthor())) + 1;
            String addMsg = FormatUtil.filter(event.getClient().getSuccess() + " Added **" + track.getInfo().title
                    + "** (`" + FormatUtil.formatTime(track.getDuration()) + "`) " + (pos == 0 ? "to begin playing" : " to the queue at position " + pos));
            if (playlist == null || !event.getSelfMember().hasPermission(event.getTextChannel(), Permission.MESSAGE_ADD_REACTION))
                m.editMessage(addMsg).queue();
            else {
                new ButtonMenu.Builder()
                        .setText(addMsg + "\n" + event.getClient().getWarning() + " This track has a playlist of **" + playlist.getTracks().size() + "** tracks attached. Select " + LOAD + " to load playlist.")
                        .setChoices(LOAD, CANCEL)
                        .setEventWaiter(bot.getWaiter())
                        .setTimeout(30, TimeUnit.SECONDS)
                        .setAction(re ->
                        {
                            if (re.getName().equals(LOAD))
                                m.editMessage(addMsg + "\n" + event.getClient().getSuccess() + " Loaded **" + loadPlaylist(playlist, track) + "** additional tracks!").queue();
                            else
                                m.editMessage(addMsg).queue();
                        }).setFinalAction(m ->
                        {
                            try {
                                m.clearReactions().queue();
                            } catch (PermissionException ignore) {
                            }
                        }).build().display(m);
            }
        }

        private int loadPlaylist(AudioPlaylist playlist, AudioTrack exclude) {
            int[] count = {0};
            playlist.getTracks().stream().forEach((track) -> {
                if (!bot.getConfig().isTooLong(track) && !track.equals(exclude)) {
                    AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
                    handler.addTrack(new QueuedTrack(track, event.getAuthor()));
                    count[0]++;
                }
            });
            return count[0];
        }

        @Override
        public void trackLoaded(AudioTrack track) {
            loadSingle(track, null);
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist) {
            if (playlist.getTracks().size() == 1 || playlist.isSearchResult()) {
                AudioTrack single = playlist.getSelectedTrack() == null ? playlist.getTracks().get(0) : playlist.getSelectedTrack();
                loadSingle(single, null);
            } else if (playlist.getSelectedTrack() != null) {
                AudioTrack single = playlist.getSelectedTrack();
                loadSingle(single, playlist);
            } else {
                int count = loadPlaylist(playlist, null);
                if (playlist.getTracks().size() == 0) {
                    m.editMessage(FormatUtil.filter(event.getClient().getWarning() + " The playlist " + (playlist.getName() == null ? "" : "(**" + playlist.getName()
                            + "**) ") + " could not be loaded or contained 0 entries")).queue();
                } else if (count == 0) {
                    m.editMessage(FormatUtil.filter(event.getClient().getWarning() + " All entries in this playlist " + (playlist.getName() == null ? "" : "(**" + playlist.getName()
                            + "**) ") + "were longer than the allowed maximum (`" + bot.getConfig().getMaxTime() + "`)")).queue();
                } else {
                    m.editMessage(FormatUtil.filter(event.getClient().getSuccess() + " Found "
                            + (playlist.getName() == null ? "a playlist" : "playlist **" + playlist.getName() + "**") + " with `"
                            + playlist.getTracks().size() + "` entries; added to the queue!"
                            + (count < playlist.getTracks().size() ? "\n" + event.getClient().getWarning() + " Tracks longer than the allowed maximum (`"
                            + bot.getConfig().getMaxTime() + "`) have been omitted." : ""))).queue();
                }
            }
        }

        @Override
        public void noMatches() {
            if (ytsearch)
                m.editMessage(FormatUtil.filter(event.getClient().getWarning() + " No results found for `" + event.getArgs() + "`.")).queue();
            else
                bot.getPlayerManager().loadItemOrdered(event.getGuild(), "ytsearch:" + event.getArgs(), new ResultHandler(m, event, true));
        }

        @Override
        public void loadFailed(FriendlyException throwable) {
            if (throwable.severity == FriendlyException.Severity.COMMON)
                m.editMessage(event.getClient().getError() + " Error loading: " + throwable.getMessage()).queue();
            else
                m.editMessage(event.getClient().getError() + " Error loading track.").queue();
        }

    }
}




