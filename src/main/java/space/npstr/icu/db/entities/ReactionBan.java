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

import java.io.Serializable;
import java.util.Objects;
import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji;
import space.npstr.sqlsauce.entities.SaucedEntity;
import space.npstr.sqlsauce.fp.types.EntityKey;

/**
 * Created by napster on 01.05.19.
 */
@Entity
@Table(name = "reaction_ban")
@Cacheable(value = false) //always fetch the most recent roles for a user
public class ReactionBan extends SaucedEntity<ReactionBan.ChannelEmoteComposite, ReactionBan> {

    @SuppressWarnings("NullableProblems") //never null if correctly initialized by Hibernate / sqlsauce
    @EmbeddedId
    private ChannelEmoteComposite id;

    //for jpa / database wrapper
    ReactionBan() {
    }

    public static EntityKey<ChannelEmoteComposite, ReactionBan> key(Channel channel, EmojiUnion emoji) {
        return switch (emoji.getType()) {
            case CUSTOM -> key(channel, emoji.asCustom());
            case UNICODE -> EntityKey.of(new ChannelEmoteComposite(channel, emoji.asUnicode()), ReactionBan.class);
        };
    }

    public static EntityKey<ChannelEmoteComposite, ReactionBan> key(Channel channel, CustomEmoji emoji) {
        return EntityKey.of(new ChannelEmoteComposite(channel, emoji), ReactionBan.class);
    }

    public static EntityKey<ChannelEmoteComposite, ReactionBan> key(Channel channel, String string) {
        return EntityKey.of(new ChannelEmoteComposite(channel.getIdLong(), string), ReactionBan.class);
    }

    @Override
    public ReactionBan setId(ChannelEmoteComposite id) {
        this.id = id;
        return this;
    }

    @Override
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

        private static final long serialVersionUID = 1L;

        @Column(name = "channel_id", nullable = false)
        private long channelId;

        @Column(name = "emote", nullable = false)
        private String emote = ""; //either emoji unicode or snowflake id of discord emote

        //for jpa & the database wrapper
        ChannelEmoteComposite() {
        }

        public ChannelEmoteComposite(Channel channel, UnicodeEmoji emoji) {
            this(channel.getIdLong(), emoji.getName());
        }

        public ChannelEmoteComposite(Channel channel, CustomEmoji emoji) {
            this(channel.getIdLong(), emoji.getId());
        }

        public ChannelEmoteComposite(long channelId, String emote) {
            this.channelId = channelId;
            this.emote = emote;
        }

        public long getChannelId() {
            return channelId;
        }

        public void setChannelId(long channelId) {
            this.channelId = channelId;
        }

        public String getEmote() {
            return emote;
        }

        public void setEmote(String emote) {
            this.emote = emote;
        }

        @Override
        public int hashCode() {
            return Objects.hash(channelId, emote);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChannelEmoteComposite)) return false;
            ChannelEmoteComposite other = (ChannelEmoteComposite) o;
            return this.channelId == other.channelId && this.emote.equals(other.emote);
        }

        @Override
        public String toString() {
            return ChannelEmoteComposite.class.getSimpleName() + String.format("(C %s, E %s)", channelId, emote);
        }
    }
}
