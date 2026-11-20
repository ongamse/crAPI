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

package com.crapi.service.Impl;

import com.crapi.constant.UserMessage;
import com.crapi.entity.ProfileVideo;
import com.crapi.entity.User;
import com.crapi.entity.UserDetails;
import com.crapi.enums.ERole;
import com.crapi.exception.CRAPIExceptionHandler;
import com.crapi.exception.IOExceptionHandler;
import com.crapi.model.CRAPIResponse;
import com.crapi.repository.ProfileVideoRepository;
import com.crapi.repository.UserDetailsRepository;
import com.crapi.service.ProfileService;
import com.crapi.service.UserService;
import com.crapi.utils.BashCommand;
import com.crapi.utils.ProfileValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class ProfileServiceImpl implements ProfileService {

  @Autowired UserService userService;
  @Autowired ProfileVideoRepository profileVideoRepository;
  @Autowired UserDetailsRepository userDetailsRepository;

  @Value("${app.enable_shell_injection}")
  private boolean enable_shell_injection;

  @Value("${app.service.name}")
  private String serviceName;

  /**
   * @param file profile image
   * @param request getting jwt token for user from request header
   * @return upload user image in database as BLOB data
   */
  @Transactional
  @Override
  public UserDetails uploadProfilePicture(MultipartFile file, HttpServletRequest request) {
    User user;
    UserDetails userDetails;
    try {
      user = userService.getUserFromToken(request);
      userDetails = userDetailsRepository.findByUser_id(user.getId());
      if (userDetails != null && ProfileValidator.validateFile(file) != null) {
        userDetails.setPicture(file.getBytes());
      } else {
        userDetails = new UserDetails();
        userDetails.setAvailable_credit(100.0);
        userDetails.setPicture(file.getBytes());
        userDetails.setUser(user);
      }
      userDetailsRepository.save(userDetails);
      return userDetails;
    } catch (IOException exception) {
      log.error("unable to upload image -> Message: %d", exception);
      throw new IOExceptionHandler(ProfileServiceImpl.class, UserMessage.CUSTOM_IO_EXCEPTION);
    }
  }

  /**
   * @param file profile video
   * @param request getting jwt token for user from request header
   * @return upload user video in database as BLOB data
   */
  @Transactional
  @Override
  public ProfileVideo uploadProfileVideo(MultipartFile file, HttpServletRequest request) {
    ProfileVideo profileVideo;
    String conversionParam = "-v codec h264";
    User user;
    try {
      user = userService.getUserFromToken(request);
      profileVideo = profileVideoRepository.findByUser_id(user.getId());
      if (profileVideo != null) {
        profileVideo.setVideo(file.getBytes());
        profileVideo.setConversion_params(conversionParam);
        profileVideo.setVideo_name(file.getOriginalFilename());
      } else {
        profileVideo = new ProfileVideo(file.getOriginalFilename(), file.getBytes(), user);
        profileVideo.setConversion_params(conversionParam);
      }
      profileVideoRepository.save(profileVideo);
      return profileVideo;
    } catch (IOException exception) {
      log.error("unable to upload video -> Message: {} ", exception);
      throw new IOExceptionHandler(ProfileServiceImpl.class, UserMessage.CUSTOM_IO_EXCEPTION);
    }
  }

  /**
   * @param videoId
   * @param request getting jwt token for user from request header
   * @return get profile video details.
   */
  @Transactional
  @Override
  public ProfileVideo getProfileVideo(Long videoId, HttpServletRequest request) {
    Optional<ProfileVideo> profileVideo = findOwnedProfileVideo(videoId, request);
    if (profileVideo.isPresent()) {
      return profileVideo.get();
    }
    throw new CRAPIExceptionHandler(UserMessage.VIDEO_NOT_FOUND, 404);
  }

  /**
   * @param videoId id of the video that is being updated, must belong to the token user
   * @param videoName validated new video name
   * @param conversionParams new conversion params, the current ones are kept when null
   * @param request getting jwt token for user from request header
   * @return its change user profile object and return success message.
   */
  @Transactional
  @Override
  public ProfileVideo updateProfileVideo(
      Long videoId, String videoName, String conversionParams, HttpServletRequest request) {
    User user;
    ProfileVideo profileVideo;
    user = userService.getUserFromToken(request);
    // Object level authorization: the video is resolved from the authenticated user and the
    // requested id is only compared in memory against that user's own video
    profileVideo = findOwnedProfileVideo(videoId, user, false).orElse(null);
    if (profileVideo != null) {
      profileVideo.setVideo_name(videoName);
      profileVideo.setConversion_params(
          (conversionParams != null ? conversionParams : profileVideo.getConversion_params()));
      profileVideoRepository.save(profileVideo);
      return profileVideo;
    }
    throw new CRAPIExceptionHandler(UserMessage.SORRY_DIDNT_GET_PROFILE + user.getEmail(), 404);
  }

  /**
   * Object level authorization helper: looks up a profile video by id and only returns it when it
   * belongs to the authenticated (token) user. Fails closed, an unknown video and a video owned by
   * somebody else are indistinguishable to the caller.
   *
   * @param videoId id of the requested video
   * @param request getting jwt token for user from request header
   * @return the video when owned by the token user, otherwise empty
   */
  private Optional<ProfileVideo> findOwnedProfileVideo(Long videoId, HttpServletRequest request) {
    return findOwnedProfileVideo(videoId, request, false);
  }

  /**
   * @param videoId id of the requested video
   * @param request getting jwt token for user from request header
   * @param allowAdmin when true a user with the admin role may access videos of other users
   * @return the video when the token user is allowed to access it, otherwise empty
   */
  private Optional<ProfileVideo> findOwnedProfileVideo(
      Long videoId, HttpServletRequest request, boolean allowAdmin) {
    User user = userService.getUserFromToken(request);
    return findOwnedProfileVideo(videoId, user, allowAdmin);
  }

  /**
   * Resolution walks from the authenticated principal: the request supplied id is never passed to a
   * repository, it is only compared in memory with the user's own video, or (admin role only) with
   * the server side list of known video ids.
   */
  private Optional<ProfileVideo> findOwnedProfileVideo(
      Long videoId, User user, boolean allowAdmin) {
    if (videoId == null || user == null || user.getId() == null) {
      return Optional.empty();
    }
    if (allowAdmin && ERole.ROLE_ADMIN.equals(user.getRole())) {
      Optional<Long> knownId =
          profileVideoRepository.findAllIds().stream()
              .filter(id -> Objects.equals(id, videoId))
              .findFirst();
      return knownId.flatMap(profileVideoRepository::findById);
    }
    ProfileVideo ownVideo = profileVideoRepository.findByUser_id(user.getId());
    if (ownVideo != null && Objects.equals(ownVideo.getId(), videoId)) {
      return Optional.of(ownVideo);
    }
    return Optional.empty();
  }

  /**
   * @param videoId
   * @param request
   * @return boolean for delete object it perform by admin
   */
  @Transactional
  @Override
  public CRAPIResponse deleteProfileVideo(Long videoId, HttpServletRequest request) {
    Optional<ProfileVideo> optionalProfileVideo;
    ProfileVideo profileVideo;
    optionalProfileVideo = findOwnedProfileVideo(videoId, request);
    if (optionalProfileVideo.isPresent()) {
      return new CRAPIResponse(UserMessage.THIS_IS_ADMIN_FUNCTION, 403);
    }
    throw new CRAPIExceptionHandler(UserMessage.SORRY_DIDNT_GET_PROFILE, 404);
  }

  /**
   * @param videoId
   * @param request
   * @return boolean for delete object if perform by admin
   */
  @Transactional
  @Override
  public CRAPIResponse deleteAdminProfileVideo(Long videoId, HttpServletRequest request) {
    Optional<ProfileVideo> optionalProfileVideo;
    ProfileVideo profileVideo;
    optionalProfileVideo = findOwnedProfileVideo(videoId, request, true);
    if (optionalProfileVideo.isPresent()) {
      profileVideo = optionalProfileVideo.get();
      profileVideo.setUser(null);
      profileVideoRepository.delete(profileVideo);
      return new CRAPIResponse(UserMessage.VIDEO_DELETED_SUCCESS_MESSAGE, 200);
    }
    throw new CRAPIExceptionHandler(UserMessage.SORRY_DIDNT_GET_PROFILE, 404);
  }

  /**
   * @param videoId
   * @return fetching profile object by user id and performing the operation according to conversion
   *     param
   */
  @Transactional
  @Override
  public CRAPIResponse convertVideo(Long videoId, HttpServletRequest request) {
    BashCommand conversionShell = new BashCommand();
    ProfileVideo profileVideo;
    String host = request.getHeader(HttpHeaders.HOST);
    String xForwardedHost = request.getHeader("x-forwarded-host");
    String scheme = request.getHeader("x-forwarded-proto");
    if (scheme == null) {
      scheme = request.getScheme();
    }
    log.debug(
        "Convert video {}, host: {}, xForwardedHost: {}, scheme: {}",
        videoId,
        host,
        xForwardedHost,
        scheme);
    try {
      if (xForwardedHost == null) {
        if (videoId != null && videoId > 0) {
          Optional<ProfileVideo> optionalProfileVideo = findOwnedProfileVideo(videoId, request);
          if (optionalProfileVideo.isPresent() && !enable_shell_injection) {
            profileVideo = optionalProfileVideo.get();
            if (ProfileValidator.checkContains(profileVideo.getConversion_params())) {
              return new CRAPIResponse(UserMessage.CONVERSION_VIDEO_OK, 200);
            } else if (profileVideo.getConversion_params().equalsIgnoreCase("-v codec h264")) {
              return new CRAPIResponse(UserMessage.CONVERT_VIDEO_BASH_COMMAND_TRIGGERED, 200);
            } else if (!profileVideo.getConversion_params().equalsIgnoreCase("-v codec h264")) {
              if (ProfileValidator.checkSpecialCharacter(profileVideo.getConversion_params())) {
                return new CRAPIResponse(UserMessage.CONVERT_VIDEO_INTERNAL_ERROR, 500);
              }
              return new CRAPIResponse(UserMessage.YOU_WON_THE_GAME, 200);
            }
            return new CRAPIResponse(UserMessage.CONVERT_VIDEO_INTERNAL_ERROR, 500);
          } else if (optionalProfileVideo.isPresent()
              && enable_shell_injection
              && optionalProfileVideo.get().getConversion_params() != null) {
            profileVideo = optionalProfileVideo.get();
            String conversionCommand =
                String.format(
                    "convertVideo -i %s %s",
                    profileVideo.getVideo_name(), profileVideo.getConversion_params());
            return new CRAPIResponse(conversionShell.executeBashCommand(conversionCommand), 200);
          }
          return new CRAPIResponse(UserMessage.CONVERT_VIDEO_INTERNAL_ERROR, 500);
        }
        return new CRAPIResponse(UserMessage.CONVERT_VIDEO_PARAM_IS_MISSING, 400);
      } else {
        return new CRAPIResponse(UserMessage.CONVERT_VIDEO_INTERNAL_USE_ONLY, 403);
      }
    } catch (IOException exception) {
      log.error("unable to convert video -> Message: %d ", exception);
      throw new IOExceptionHandler(ProfileServiceImpl.class, UserMessage.ERROR);
    }
  }
}
