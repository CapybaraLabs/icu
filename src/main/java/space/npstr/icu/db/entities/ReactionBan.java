/*
 * Copyright (C) 2017 - 2023 Dennis Neufeld
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

package space.npstr.icu.db.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji;

/**
 * Created by napster on 01.05.19.
 */
@Entity
@Table(name = "reaction_ban")
public class ReactionBan {

    @EmbeddedId
    private ChannelEmoteComposite id;

    public static ChannelEmoteComposite key(Channel channel, EmojiUnion emoji) {
        return switch (emoji.getType()) {
            case CUSTOM -> key(channel, emoji.asCustom());
            case UNICODE -> new ChannelEmoteComposite(channel, emoji.asUnicode());
        };
    }

    public static ChannelEmoteComposite key(Channel channel, CustomEmoji emoji) {
        return new ChannelEmoteComposite(channel, emoji);
    }

    public static ChannelEmoteComposite key(Channel channel, String string) {
        return new ChannelEmoteComposite(channel.getIdLong(), string);
    }

    //for jpa / database wrapper
    public ReactionBan() {}

    ReactionBan(ChannelEmoteComposite id) {
        this.id = id;
    }

    public ChannelEmoteComposite getId() {
        return this.id;
    }

    /**
     * Created by napster on 01.05.19.
     * <p>
     * Composite primary key for Channel x Emote
     */
    @Embeddable
    public static class ChannelEmoteComposite implements Serializable {

        @Column(name = "channel_id", nullable = false)
        private long channelId;

        @Column(name = "emote", nullable = false)
        private String emote = ""; //either emoji unicode or snowflake id of discord emote

        //for jpa & the database wrapper
        public ChannelEmoteComposite() {}

        ChannelEmoteComposite(Channel channel, UnicodeEmoji emoji) {
            this(channel.getIdLong(), emoji.getName());
        }

        ChannelEmoteComposite(Channel channel, CustomEmoji emoji) {
            this(channel.getIdLong(), emoji.getId());
        }

        ChannelEmoteComposite(long channelId, String emote) {
            this.channelId = channelId;
            this.emote = emote;
        }

        public long getChannelId() {
            return channelId;
        }

        public String getEmote() {
            return emote;
        }

        @Override
        public int hashCode() {
            return Objects.hash(channelId, emote);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChannelEmoteComposite other)) return false;
            return this.channelId == other.channelId && this.emote.equals(other.emote);
        }

        @Override
        public String toString() {
            return ChannelEmoteComposite.class.getSimpleName() + String.format("(C %s, E %s)", channelId, emote);
        }
    }
}
