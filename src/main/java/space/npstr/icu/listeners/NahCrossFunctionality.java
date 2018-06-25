/*
 * Copyright (C) 2017 - 2018 Dennis Neufeld
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package space.npstr.icu.listeners;

import net.dv8tion.jda.core.entities.Emote;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.icu.Config;

import java.util.Arrays;

/**
 * Created by napster on 14.06.18.
 * <p>
 * Enhancing the NoAdsHere bot
 */
public class NahCrossFunctionality extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(NahCrossFunctionality.class);

    private static final long NAH_BOT_ID = 316009507446718465L; //NoAdsHere. Hardcoded cause there is only one such official bot for now.

    private static final String[] REACTION_TRIGGER_CONTENT_DELETED = {
            "Advertisement is not allowed in this Server!",
    };

    private static final String[] REACTION_TRIGGER_CONTENT_KICKED = {
            "has been kicked for Advertisement!",
    };

    private static final String[] REACTION_TRIGGER_CONTENT_BANNED = {
            "has been banned for Advertisement!",
    };

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!Config.C.nahEnhancement) {
            return;
        }
        if (event.getAuthor().getIdLong() != NAH_BOT_ID) {
            return;
        }

        long emoteId = 0L;

        if (Arrays.stream(REACTION_TRIGGER_CONTENT_DELETED)
                .anyMatch(trigger -> event.getMessage().getContentRaw().contains(trigger))) {
            emoteId = Config.C.nahReactionEmoteIdDeleted;
        } else if (Arrays.stream(REACTION_TRIGGER_CONTENT_KICKED)
                .anyMatch(trigger -> event.getMessage().getContentRaw().contains(trigger))) {
            emoteId = Config.C.nahReactionEmoteIdKicked;
        } else if (Arrays.stream(REACTION_TRIGGER_CONTENT_BANNED)
                .anyMatch(trigger -> event.getMessage().getContentRaw().contains(trigger))) {
            emoteId = Config.C.nahReactionEmoteIdBanned;
        }

        if (emoteId <= 0L) {
            return;
        }

        final Emote reactionEmote = event.getJDA().getEmoteById(emoteId);
        if (reactionEmote == null) {
            log.warn("Failed to look up nah reaction emote by id " + emoteId);
            return;
        }

        event.getMessage().addReaction(reactionEmote).queue();
    }
}
