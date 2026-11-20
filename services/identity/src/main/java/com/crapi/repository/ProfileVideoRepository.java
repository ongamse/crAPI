/*
 * Licensed under the Apache License, Version 2.0 (the “License”);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an “AS IS” BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.crapi.repository;

import com.crapi.entity.ProfileVideo;
import com.crapi.enums.ERole;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProfileVideoRepository extends JpaRepository<ProfileVideo, Long> {
  ProfileVideo findByUser_id(Long id);

  /** Owner scoped lookup: the video is only returned when it belongs to the given user. */
  Optional<ProfileVideo> findByIdAndUser_id(Long id, Long userId);

  /**
   * Role scoped lookup: the video is only returned when the acting user (by id) exists and holds
   * the given role.
   */
  @Query(
      "SELECT v FROM ProfileVideo v WHERE v.id = :videoId AND EXISTS "
          + "(SELECT u.id FROM User u WHERE u.id = :userId AND u.role = :role)")
  Optional<ProfileVideo> findByIdIfUserHasRole(
      @Param("videoId") Long videoId, @Param("userId") Long userId, @Param("role") ERole role);
}
