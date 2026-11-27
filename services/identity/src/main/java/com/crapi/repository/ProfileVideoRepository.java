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
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProfileVideoRepository extends JpaRepository<ProfileVideo, Long> {
  ProfileVideo findByUser_id(Long id);

  /** Fetch a video by id only when it belongs to the given user (single scoped lookup). */
  Optional<ProfileVideo> findByIdAndUser_id(Long id, Long userId);

  /**
   * Fetch a video by id only when it belongs to the given user, or when the caller is an admin.
   * Ownership is evaluated in the query itself so a foreign id is never loaded for a non-admin.
   */
  @Query(
      "SELECT v FROM ProfileVideo v WHERE v.id = :id AND (v.user.id = :userId OR :admin = true)")
  Optional<ProfileVideo> findByIdForOwnerOrAdmin(
      @Param("id") Long id, @Param("userId") Long userId, @Param("admin") boolean admin);
}
