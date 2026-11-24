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

package com.crapi.controller;

import com.crapi.constant.UserMessage;
import com.crapi.entity.ProfileVideo;
import com.crapi.entity.UserDetails;
import com.crapi.enums.ERole;
import com.crapi.exception.CRAPIExceptionHandler;
import com.crapi.model.CRAPIResponse;
import com.crapi.model.VideoForm;
import com.crapi.service.ProfileService;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/identity")
public class ProfileController {

  @Autowired ProfileService profileService;

  /**
   * @param videoId
   * @param request
   * @return get profile video details.
   */
  @GetMapping("/api/v2/user/videos/{video_id}")
  public ResponseEntity<?> getProfileVideo(
      @PathVariable("video_id") Long videoId, HttpServletRequest request) {
    ProfileVideo profileVideo = profileService.getProfileVideo(videoId, request);
    if (profileVideo != null) {
      return ResponseEntity.status(HttpStatus.OK).body(profileVideo);
    } else {
      return ResponseEntity.status(HttpStatus.NO_CONTENT)
          .body(new CRAPIResponse(UserMessage.VIDEO_NOT_FOUND));
    }
  }

  /**
   * @param file
   * @param request
   * @return its save and update user profile picture for token user and return updated user profile
   *     object
   */
  @PostMapping(value = "/api/v2/user/pictures")
  public ResponseEntity<?> updateProfilePicture(
      @RequestPart("file") MultipartFile file, HttpServletRequest request) {
    UserDetails profilePictureLink = profileService.uploadProfilePicture(file, request);
    if (profilePictureLink != null) {
      profilePictureLink.setUser(null);
      return ResponseEntity.status(HttpStatus.OK).body(profilePictureLink);
    } else
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(new CRAPIResponse(UserMessage.INTERNAL_SERVER_ERROR));
  }

  /**
   * @param file
   * @param request
   * @returns update user profile video for token user and return updated user profile object user
   *     video allowed up-to 10MB
   */
  @PostMapping(value = "/api/v2/user/videos")
  public ResponseEntity<?> uploadProfileVideo(
      @RequestPart("file") MultipartFile file, HttpServletRequest request) {
    ProfileVideo profileVideoLink = profileService.uploadProfileVideo(file, request);
    if (profileVideoLink != null) {
      return ResponseEntity.status(HttpStatus.OK).body(profileVideoLink);
    } else
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(new CRAPIResponse(UserMessage.INTERNAL_SERVER_ERROR));
  }

  /**
   * @param videoId
   * @param videoForm
   * @param principal authenticated Spring Security principal
   * @return updated profile video name and return update success message.
   */
  @PutMapping("/api/v2/user/videos/{video_id}")
  public ResponseEntity<?> updateProfileVideo(
      @PathVariable("video_id") Long videoId,
      @RequestBody VideoForm videoForm,
      Principal principal) {
    ProfileVideo profileVideo =
        profileService.updateProfileVideo(videoId, videoForm, principal.getName());
    if (profileVideo != null) return ResponseEntity.status(HttpStatus.OK).body(profileVideo);
    else
      return ResponseEntity.status(HttpStatus.NO_CONTENT)
          .body(new CRAPIResponse(UserMessage.SORRY_DIDNT_GET_PROFILE));
  }

  /**
   * @param videoId
   * @param principal authenticated Spring Security principal
   * @return message that user can't delete user video it give hint to user for BFLA -
   *     Vulnerabilities
   */
  @DeleteMapping("/api/v2/user/videos/{video_id}")
  public ResponseEntity<?> deleteVideo(
      @PathVariable("video_id") Long videoId, Principal principal) {
    CRAPIResponse deleteProfileResponse =
        profileService.deleteProfileVideo(videoId, principal.getName());
    if (deleteProfileResponse != null && deleteProfileResponse.getStatus() == 200) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(deleteProfileResponse);
    } else {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(deleteProfileResponse);
    }
  }

  /**
   * @param videoId
   * @param authentication authenticated Spring Security principal
   * @return delete user video from database and return message BFLA - Vulnerabilities
   */
  @DeleteMapping("/api/v2/admin/videos/{video_id}")
  public ResponseEntity<CRAPIResponse> deleteVideoBOLA(
      @PathVariable("video_id") Long videoId, Authentication authentication) {
    // Admin-only endpoint: the role comes from the authenticated principal's authorities.
    boolean isAdmin =
        authentication != null
            && authentication.getAuthorities().stream()
                .anyMatch(authority -> ERole.ROLE_ADMIN.name().equals(authority.getAuthority()));
    if (!isAdmin) {
      throw new CRAPIExceptionHandler(UserMessage.THIS_IS_ADMIN_FUNCTION, 403);
    }
    CRAPIResponse deleteProfileResponse =
        profileService.deleteAdminProfileVideo(videoId, authentication.getName());
    if (deleteProfileResponse != null && deleteProfileResponse.getStatus() == 200) {
      return ResponseEntity.status(HttpStatus.OK).body(deleteProfileResponse);
    }
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(deleteProfileResponse);
  }

  /**
   * @param video_id
   * @return Shell Injection - convert video its read the conversion param from the database and
   *     perform the operation according to conversion param this api will be accessed by locally
   *     only
   */
  @GetMapping("api/v2/user/videos/convert_video")
  public ResponseEntity<?> convertVideoEndPoint(
      @RequestParam(required = false) Long video_id,
      Principal principal,
      HttpServletRequest request) {
    CRAPIResponse convertVideoResponse =
        profileService.convertVideo(video_id, principal.getName(), request);
    if (convertVideoResponse != null && convertVideoResponse.getStatus() == 200) {
      return ResponseEntity.status(HttpStatus.OK).body(convertVideoResponse);
    } else if (convertVideoResponse != null && convertVideoResponse.getStatus() != 200) {
      return ResponseEntity.status(HttpStatus.valueOf(convertVideoResponse.getStatus()))
          .body(convertVideoResponse);
    }
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(convertVideoResponse);
  }
}
