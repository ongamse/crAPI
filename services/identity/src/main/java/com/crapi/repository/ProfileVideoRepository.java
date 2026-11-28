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
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProfileVideoRepository extends JpaRepository<ProfileVideo, Long> {
  ProfileVideo findByUser_id(Long id);

  ProfileVideo findByIdAndUser_id(Long id, Long userId);

  /**
   * Resolves a video for the requesting user: the video must be owned by the requester, or the
   * requester must hold the given (admin) role. Authorization is part of the query itself.
   */
  @Query(
      "SELECT v FROM ProfileVideo v WHERE v.id = :videoId AND (v.user.id = :requesterId OR EXISTS"
          + " (SELECT u.id FROM User u WHERE u.id = :requesterId AND u.role = :adminRole))")
  ProfileVideo findByIdForRequester(
      @Param("videoId") Long videoId,
      @Param("requesterId") Long requesterId,
      @Param("adminRole") ERole adminRole);
}
