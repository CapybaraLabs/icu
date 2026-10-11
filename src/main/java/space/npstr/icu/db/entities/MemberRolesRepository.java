/*
 * Copyright (C) 2018 - 2023 Dennis Neufeld
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

import java.util.Optional;
import net.dv8tion.jda.api.entities.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRolesRepository extends JpaRepository<MemberRoles, MemberRoles.MemberComposite> {

	/**
	 * Store the member's current roles and nickname, or delete the row if there is nothing to restore.
	 */
	default void saveOrDelete(Member member) {
		MemberRoles.MemberComposite id = MemberRoles.key(member);
		Optional<MemberRoles> existing = findById(id);
		MemberRoles memberRoles = existing.orElseGet(() -> new MemberRoles(id));
		memberRoles.set(member);
		if (!memberRoles.isEmpty()) {
			save(memberRoles);
		} else {
			existing.ifPresent(this::delete);
		}
	}

}
