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
import com.crapi.entity.ChangeEmailRequest;
import com.crapi.entity.ProfileVideo;
import com.crapi.entity.User;
import com.crapi.entity.UserDetails;
import com.crapi.enums.ERole;
import com.crapi.exception.CRAPIExceptionHandler;
import com.crapi.model.*;
import com.crapi.repository.ProfileVideoRepository;
import com.crapi.repository.UserDetailsRepository;
import com.crapi.service.UserService;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@RunWith(MockitoJUnitRunner.class)
public class ProfileServiceImplTest {

  @InjectMocks private ProfileServiceImpl profileService;

  @Mock private UserService userService;
  @Mock private ProfileVideoRepository profileVideoRepository;
  @Mock private UserDetailsRepository userDetailsRepository;

  @Test
  @DisplayName("uploadProfilePictureSuccessFullWithSetPictureCalledAndUserDetailsNotNull")
  public void uploadProfilePictureSuccessFull() {
    String expectedProfilePictureContent = "Sample text";
    MockMultipartFile multipartFile =
        new MockMultipartFile(
            "sample",
            "sample.txt",
            MediaType.TEXT_PLAIN_VALUE,
            expectedProfilePictureContent.getBytes());
    User user = getDummyUser();
    UserDetails userDetails = getDummyUserDetails();
    Mockito.when(userService.getUserFromToken(Mockito.any())).thenReturn(user);
    Mockito.when(userDetailsRepository.findByUser_id(Mockito.anyLong())).thenReturn(userDetails);
    Mockito.doReturn(userDetails).when(userDetailsRepository).save(Mockito.any());
    UserDetails actualUserDetails =
        profileService.uploadProfilePicture(multipartFile, getMockHttpRequest());
    String actualProfilePictureContent =
        new String(actualUserDetails.getPicture(), StandardCharsets.UTF_8);
    Mockito.verify(userDetailsRepository, Mockito.times(1)).save(Mockito.any());
    Assertions.assertEquals(expectedProfilePictureContent, actualProfilePictureContent);
  }

  @Test
  @DisplayName("uploadProfilePictureUserDetailsNull")
  public void uploadProfilePictureWithUserDetailsNull() {
    String expectedProfilePictureContent = "Sample text";
    MockMultipartFile multipartFile =
        new MockMultipartFile(
            "sample",
            "sample.txt",
            MediaType.TEXT_PLAIN_VALUE,
            expectedProfilePictureContent.getBytes());
    UserDetails userDetails = getDummyUserDetails();
    User user = getDummyUser();
    Mockito.when(userService.getUserFromToken(Mockito.any())).thenReturn(user);
    Mockito.when(userDetailsRepository.findByUser_id(Mockito.anyLong())).thenReturn(null);
    Mockito.doReturn(userDetails).when(userDetailsRepository).save(Mockito.any());
    UserDetails actualUserDetails =
        profileService.uploadProfilePicture(multipartFile, getMockHttpRequest());
    Mockito.verify(userDetailsRepository, Mockito.times(1)).save(Mockito.any());
    String actualProfilePictureContent =
        new String(actualUserDetails.getPicture(), StandardCharsets.UTF_8);
    Assertions.assertEquals(expectedProfilePictureContent, actualProfilePictureContent);
    Assertions.assertEquals(100.0, actualUserDetails.getAvailable_credit());
  }

  @Test
  @DisplayName("uploadProfileVideoSuccessFullWhenProfileVideoExists")
  public void uploadProfileVideoSuccessFullProfileVideoExists() {
    ProfileVideo profileVideo = getDummyProfileVideo();
    User user = getDummyUser();
    Mockito.when(userService.getUserFromToken(Mockito.any())).thenReturn(user);
    Mockito.when(profileVideoRepository.findByUser_id(Mockito.anyLong())).thenReturn(profileVideo);
    Mockito.when(profileVideoRepository.save(profileVideo)).thenReturn(profileVideo);
    ProfileVideo actualProfileVideo =
        profileService.uploadProfileVideo(getDummyMulitpartFile(), getMockHttpRequest());
    Mockito.verify(profileVideoRepository, Mockito.times(1)).save(Mockito.any());
    Assertions.assertSame(profileVideo, actualProfileVideo);
  }

  @Test
  @DisplayName("uploadProfileVideoSuccessFullWhenProfileVideoDoesNotExist")
  public void uploadProfileVideoSuccessFullProfileVideoDoesNotExistCheckNewObject() {
    ProfileVideo profileVideo = getDummyProfileVideo();
    User user = getDummyUser();
    Mockito.when(userService.getUserFromToken(Mockito.any())).thenReturn(user);
    Mockito.when(profileVideoRepository.findByUser_id(Mockito.anyLong())).thenReturn(null);
    ProfileVideo actualProfileVideo =
        profileService.uploadProfileVideo(getDummyMulitpartFile(), getMockHttpRequest());
    Mockito.verify(profileVideoRepository, Mockito.times(1)).save(Mockito.any());
    Assertions.assertNotSame(profileVideo, actualProfileVideo);
  }

  @Test(expected = CRAPIExceptionHandler.class)
  @DisplayName("uploadProfileVideoFailsDueToProfileObjectVideoNotFound")
  public void uploadProfileVideoWhenProfileVideoNotFound() {
    User user = getDummyUser();
    Mockito.when(userService.getUserFromToken(Mockito.any())).thenReturn(user);
    Mockito.when(profileVideoRepository.findByIdAndUser_id(Mockito.anyLong(), Mockito.anyLong()))
        .thenReturn(Optional.empty());
    profileService.updateProfileVideo(1L, getDummyVideoForm(), getMockHttpRequest());
    Mockito.verify(profileVideoRepository, Mockito.times(0)).save(Mockito.any());
  }

  @Test
  @DisplayName("updateProfileVideoSuccessFullWhenVideoFormConversionParamNotNull")
  public void updateProfileVideoSuccessFullConversionParamaNotNull() {
    VideoForm videoForm = getDummyVideoForm();
    User user = getDummyUser();
    ProfileVideo profileVideo = getDummyProfileVideo();
    Mockito.when(userService.getUserFromToken(Mockito.any())).thenReturn(user);
    Mockito.when(profileVideoRepository.findByIdAndUser_id(1L, user.getId()))
        .thenReturn(Optional.of(profileVideo));
    Mockito.when(profileVideoRepository.save(Mockito.any())).thenReturn(profileVideo);
    ProfileVideo profileVideoResponse =
        profileService.updateProfileVideo(1L, videoForm, getMockHttpRequest());
    Mockito.verify(profileVideoRepository, Mockito.times(1)).save(Mockito.any());
    Assertions.assertEquals(profileVideo.getVideo_name(), profileVideoResponse.getVideo_name());
  }

  @Test
  @DisplayName("updateProfileVideoSuccessFullWhenVideoFormConversionParamNull")
  public void updateProfileVideoSuccessFullConversionParamNull() {
    VideoForm videoForm = getDummyVideoForm();
    User user = getDummyUser();
    ProfileVideo profileVideo = getDummyProfileVideo();
    videoForm.setConversion_params(null);
    Mockito.when(userService.getUserFromToken(Mockito.any())).thenReturn(user);
    Mockito.when(profileVideoRepository.findByIdAndUser_id(1L, user.getId()))
        .thenReturn(Optional.of(profileVideo));
    Mockito.when(profileVideoRepository.save(Mockito.any())).thenReturn(profileVideo);
    ProfileVideo profileVideoResponse =
        profileService.updateProfileVideo(1L, videoForm, getMockHttpRequest());
    Mockito.verify(profileVideoRepository, Mockito.times(1)).save(Mockito.any());
    Assertions.assertEquals(profileVideo.getVideo_name(), profileVideoResponse.getVideo_name());
  }

  @Test(expected = CRAPIExceptionHandler.class)
  @DisplayName("changeVideoNameFailWithProfileVideoNotFound")
  public void changeVideoNameFailWithProfileVideoNull() {
    VideoForm videoForm = getDummyVideoForm();
    User user = getDummyUser();
    String expectedMessage = UserMessage.SORRY_DIDNT_GET_PROFILE + user.getEmail();
    Mockito.when(userService.getUserFromToken(Mockito.any())).thenReturn(user);
    Mockito.when(profileVideoRepository.findByIdAndUser_id(Mockito.anyLong(), Mockito.anyLong()))
        .thenReturn(Optional.empty());
    ProfileVideo profileVideoResponse =
        profileService.updateProfileVideo(1L, videoForm, getMockHttpRequest());
    Mockito.verify(profileVideoRepository, Mockito.times(0)).save(Mockito.any());
  }

  @Test
  @DisplayName("deleteProfileVideoSuccess")
  public void deleteProfileVideoSuccess() {
    VideoForm videoForm = getDummyVideoForm();
    ProfileVideo profileVideo = getDummyProfileVideo();
    Mockito.when(userService.getUserFromToken(Mockito.any())).thenReturn(getDummyUser());
    Mockito.when(profileVideoRepository.findByIdAndUser_id(1L, 1L))
        .thenReturn(Optional.of(profileVideo));
    CRAPIResponse crapiAPIResponse =
        profileService.deleteProfileVideo(videoForm.getId(), getMockHttpRequest());
    Mockito.verify(profileVideoRepository, Mockito.times(0)).save(Mockito.any());
    Assertions.assertEquals(HttpStatus.FORBIDDEN.value(), crapiAPIResponse.getStatus());
    Assertions.assertEquals(UserMessage.THIS_IS_ADMIN_FUNCTION, crapiAPIResponse.getMessage());
  }

  @Test(expected = CRAPIExceptionHandler.class)
  @DisplayName("deleteProfileVideoFailWhenVideoBelongsToAnotherUser")
  public void deleteProfileVideoFailWhenVideoBelongsToAnotherUser() {
    VideoForm videoForm = getDummyVideoForm();
    Mockito.when(userService.getUserFromToken(Mockito.any())).thenReturn(getOtherUser());
    Mockito.when(profileVideoRepository.findByIdAndUser_id(1L, 2L)).thenReturn(Optional.empty());
    profileService.deleteProfileVideo(videoForm.getId(), getMockHttpRequest());
  }

  @Test
  @DisplayName("deleteProfileAdminVideoSuccess")
  public void deleteProfileAdminVideoSuccess() {
    VideoForm videoForm = getDummyVideoForm();
    ProfileVideo profileVideo = getDummyProfileVideo();
    Mockito.when(userService.getUserFromToken(Mockito.any())).thenReturn(getAdminUser());
    Mockito.when(profileVideoRepository.findById(Mockito.anyLong()))
        .thenReturn(Optional.of(profileVideo));
    // Mockito.when(profileVideoRepository.save(Mockito.any())).thenReturn(profileVideo);
    CRAPIResponse crapiAPIResponse =
        profileService.deleteAdminProfileVideo(videoForm.getId(), getMockHttpRequest());
    // Mockito.verify(profileVideoRepository, Mockito.times(0)).save(Mockito.any());
    Assertions.assertEquals(HttpStatus.OK.value(), crapiAPIResponse.getStatus());
    Assertions.assertEquals(
        UserMessage.VIDEO_DELETED_SUCCESS_MESSAGE, crapiAPIResponse.getMessage());
  }

  @Test(expected = CRAPIExceptionHandler.class)
  @DisplayName("deleteProfileAdminVideoFailWhenUserIsNotAdmin")
  public void deleteProfileAdminVideoFailWhenUserIsNotAdmin() {
    VideoForm videoForm = getDummyVideoForm();
    Mockito.when(userService.getUserFromToken(Mockito.any())).thenReturn(getDummyUser());
    try {
      profileService.deleteAdminProfileVideo(videoForm.getId(), getMockHttpRequest());
    } finally {
      Mockito.verify(profileVideoRepository, Mockito.times(0)).delete(Mockito.any());
    }
  }

  @Test(expected = CRAPIExceptionHandler.class)
  @DisplayName("updateProfileVideoFailWhenVideoIdDoesNotMatchUserVideo")
  public void updateProfileVideoFailWhenVideoIdDoesNotMatchUserVideo() {
    Mockito.when(userService.getUserFromToken(Mockito.any())).thenReturn(getDummyUser());
    Mockito.when(profileVideoRepository.findByIdAndUser_id(999L, 1L)).thenReturn(Optional.empty());
    try {
      profileService.updateProfileVideo(999L, getDummyVideoForm(), getMockHttpRequest());
    } finally {
      Mockito.verify(profileVideoRepository, Mockito.times(0)).save(Mockito.any());
    }
  }

  @Test
  @DisplayName("convertVideoFailWhenVideoBelongsToAnotherUser")
  public void convertVideoFailWhenVideoBelongsToAnotherUser() {
    ReflectionTestUtils.setField(profileService, "enable_shell_injection", false);
    Long videoId = Long.valueOf(2);
    MockHttpServletRequest request = getMockHttpRequest();
    request.addHeader(
        HttpHeaders.HOST, "localhost:" + String.valueOf(System.getenv("SERVER_PORT")));
    Mockito.when(userService.getUserFromToken(Mockito.any())).thenReturn(getOtherUser());
    Mockito.when(profileVideoRepository.findByIdAndUser_id(videoId, 2L))
        .thenReturn(Optional.empty());
    CRAPIResponse crapiAPIResponse = profileService.convertVideo(videoId, request);
    // A video owned by someone else is indistinguishable from a missing one.
    Assertions.assertEquals(
        UserMessage.CONVERT_VIDEO_INTERNAL_ERROR, crapiAPIResponse.getMessage());
    Assertions.assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), crapiAPIResponse.getStatus());
  }

  @Test(expected = CRAPIExceptionHandler.class)
  @DisplayName("deleteProfileVideoFailWithUserNotFound")
  public void deleteProfileVideoFailWithUserNotFoundThrowsException() {
    VideoForm videoForm = getDummyVideoForm();
    Mockito.when(userService.getUserFromToken(Mockito.any())).thenReturn(getDummyUser());
    Mockito.when(profileVideoRepository.findByIdAndUser_id(Mockito.anyLong(), Mockito.anyLong()))
        .thenReturn(Optional.empty());
    CRAPIResponse crapiAPIResponse =
        profileService.deleteProfileVideo(videoForm.getId(), getMockHttpRequest());
    Mockito.verify(profileVideoRepository, Mockito.times(0)).save(Mockito.any());
  }

  @Test
  @DisplayName("convertVideoResponseWhenNotValidSocketAddress")
  public void convertVideoResponseWhenNotValidSocketAddress() {
    Long videoId = Long.valueOf(2);
    MockHttpServletRequest request = getMockHttpRequest();
    request.addHeader(
        HttpHeaders.HOST, "120.1.2.3:" + String.valueOf(System.getenv("SERVER_PORT")));
    request.addHeader(
        "x-forwarded-host", "120.1.2.3:" + String.valueOf(System.getenv("SERVER_PORT")));
    CRAPIResponse crapiAPIResponse = profileService.convertVideo(videoId, request);
    Assertions.assertEquals(
        UserMessage.CONVERT_VIDEO_INTERNAL_USE_ONLY, crapiAPIResponse.getMessage());
    Assertions.assertEquals(HttpStatus.FORBIDDEN.value(), crapiAPIResponse.getStatus());
  }

  @Test
  @DisplayName("convertVideoFailResponseWhenVideoIdNull")
  public void convertVideoFailResponseWhenVideoIdNull() {
    Long videoId = null;
    MockHttpServletRequest request = getMockHttpRequest();
    request.addHeader(
        HttpHeaders.HOST, "127.0.0.1:" + String.valueOf(System.getenv("SERVER_PORT")));
    CRAPIResponse crapiAPIResponse = profileService.convertVideo(videoId, request);
    Assertions.assertEquals(
        UserMessage.CONVERT_VIDEO_PARAM_IS_MISSING, crapiAPIResponse.getMessage());
    Assertions.assertEquals(HttpStatus.BAD_REQUEST.value(), crapiAPIResponse.getStatus());
  }

  @Test
  @DisplayName("convertVideoFailWhenVideoObjectNotFound")
  public void convertVideoFailWhenVideoObjectNotFound() {
    Long videoId = Long.valueOf(2);
    MockHttpServletRequest request = getMockHttpRequest();
    request.addHeader(
        HttpHeaders.HOST, "localhost:" + String.valueOf(System.getenv("SERVER_PORT")));
    Mockito.when(userService.getUserFromToken(Mockito.any())).thenReturn(getDummyUser());
    Mockito.when(profileVideoRepository.findByIdAndUser_id(videoId, 1L))
        .thenReturn(Optional.empty());
    CRAPIResponse crapiAPIResponse = profileService.convertVideo(videoId, request);
    Assertions.assertEquals(UserMessage.ERROR_EXECUTING_STRING, crapiAPIResponse.getMessage());
    Assertions.assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), crapiAPIResponse.getStatus());
  }

  @Test
  @DisplayName("convertVideoSuccessWithConvertVideoBashCommandTriggered")
  public void convertVideoSuccessWithConvertVideoBashCommandTriggered() {
    ReflectionTestUtils.setField(profileService, "enable_shell_injection", false);
    ProfileVideo profileVideo = getDummyProfileVideo();
    profileVideo.setConversion_params("-v codec h264");
    Long videoId = Long.valueOf(2);
    MockHttpServletRequest request = getMockHttpRequest();
    request.addHeader(
        HttpHeaders.HOST, "localhost:" + String.valueOf(System.getenv("SERVER_PORT")));
    Mockito.when(userService.getUserFromToken(Mockito.any())).thenReturn(getDummyUser());
    Mockito.when(profileVideoRepository.findByIdAndUser_id(videoId, 1L))
        .thenReturn(Optional.of(profileVideo));
    CRAPIResponse crapiAPIResponse = profileService.convertVideo(videoId, request);
    Assertions.assertEquals(
        UserMessage.CONVERT_VIDEO_BASH_COMMAND_TRIGGERED, crapiAPIResponse.getMessage());
    Assertions.assertEquals(HttpStatus.OK.value(), crapiAPIResponse.getStatus());
  }

  @Test
  @DisplayName("convertVideoSuccessWithWonGame")
  public void convertVideoSuccessWithWonGame() {
    ReflectionTestUtils.setField(profileService, "enable_shell_injection", false);
    ProfileVideo profileVideo = getDummyProfileVideo();
    Long videoId = Long.valueOf(2);
    MockHttpServletRequest request = getMockHttpRequest();
    request.addHeader(
        HttpHeaders.HOST, "localhost:" + String.valueOf(System.getenv("SERVER_PORT")));
    Mockito.when(userService.getUserFromToken(Mockito.any())).thenReturn(getDummyUser());
    Mockito.when(profileVideoRepository.findByIdAndUser_id(videoId, 1L))
        .thenReturn(Optional.of(profileVideo));
    CRAPIResponse crapiAPIResponse = profileService.convertVideo(videoId, request);
    Assertions.assertEquals(UserMessage.YOU_WON_THE_GAME, crapiAPIResponse.getMessage());
    Assertions.assertEquals(HttpStatus.OK.value(), crapiAPIResponse.getStatus());
  }

  @Test
  @DisplayName("convertVideoSuccessWithVideoConversion")
  public void convertVideoSuccessWithVideoConversion() {
    ReflectionTestUtils.setField(profileService, "enable_shell_injection", false);
    ProfileVideo profileVideo = getDummyProfileVideo();
    profileVideo.setConversion_params("-v codec h264 && ping 8.8.8.8");
    Long videoId = Long.valueOf(2);
    MockHttpServletRequest request = getMockHttpRequest();
    request.addHeader(
        HttpHeaders.HOST, "localhost:" + String.valueOf(System.getenv("SERVER_PORT")));
    Mockito.when(userService.getUserFromToken(Mockito.any())).thenReturn(getDummyUser());
    Mockito.when(profileVideoRepository.findByIdAndUser_id(videoId, 1L))
        .thenReturn(Optional.of(profileVideo));
    CRAPIResponse crapiAPIResponse = profileService.convertVideo(videoId, request);
    Assertions.assertEquals(UserMessage.CONVERSION_VIDEO_OK, crapiAPIResponse.getMessage());
    Assertions.assertEquals(HttpStatus.OK.value(), crapiAPIResponse.getStatus());
  }

  private VideoForm getDummyVideoForm() {
    VideoForm videoForm = new VideoForm();
    videoForm.setConversion_params("Dummy Conversion Params");
    videoForm.setId(1l);
    videoForm.setVideo_url("http://dummyvideourl.com");
    videoForm.setVideoName("Dummy Video");
    return videoForm;
  }

  private LoginWithEmailToken getDummyLoginWithEmailToken() {
    LoginWithEmailToken loginWithEmailToken = new LoginWithEmailToken();
    loginWithEmailToken.setEmail("user@email.com");
    loginWithEmailToken.setToken("dummyToken");
    return loginWithEmailToken;
  }

  private ChangeEmailRequest getDummyChangeEmailRequest() {
    ChangeEmailRequest changeEmailRequest = new ChangeEmailRequest();
    changeEmailRequest.setEmailToken("dummy token");
    changeEmailRequest.setNewEmail("new@email.com");
    changeEmailRequest.setOldEmail("old@email.com");
    changeEmailRequest.setStatus("DUMMY");
    changeEmailRequest.setUser(getDummyUser());
    changeEmailRequest.setId(1l);
    return changeEmailRequest;
  }

  private ChangeEmailForm getDummyChangeEmailForm() {
    ChangeEmailForm changeEmailForm = new ChangeEmailForm();
    changeEmailForm.setToken("dummyToken");
    changeEmailForm.setNew_email("new@email.com");
    changeEmailForm.setOld_email("old@email.com");
    return changeEmailForm;
  }

  private ProfileVideo getDummyProfileVideo() {
    ProfileVideo profileVideo = new ProfileVideo();
    profileVideo.setVideo_name("New Video");
    profileVideo.setConversion_params("Dummy Conversion Params");
    profileVideo.setUser(getDummyUser());
    profileVideo.setId(1l);
    profileVideo.setVideo(new byte[] {1, 0, 1});
    return profileVideo;
  }

  private SignUpForm getDummySignUpForm() {
    SignUpForm signUpForm = new SignUpForm(1l, "Name", "email@example.com", "9999999");
    signUpForm.setPassword("myPass");
    return signUpForm;
  }

  private UserDetails getDummyUserDetails() {
    UserDetails userDetails = new UserDetails();
    userDetails.setUser(getDummyUser());
    userDetails.setAvailable_credit(200.89);
    userDetails.setName("User1 Details");
    userDetails.setPicture(new byte[] {0, 1, 0});
    return userDetails;
  }

  private User getDummyUser() {
    User user = new User("email@example.com", "9798789212", "Pass", ERole.ROLE_USER);
    user.setId(1l);
    return user;
  }

  private User getOtherUser() {
    User user = new User("other@example.com", "9798789213", "Pass", ERole.ROLE_USER);
    user.setId(2l);
    return user;
  }

  private User getAdminUser() {
    User user = new User("admin@example.com", "9798789214", "Pass", ERole.ROLE_ADMIN);
    user.setId(3l);
    return user;
  }

  private LoginForm getDummyLoginForm() {
    LoginForm loginForm = new LoginForm();
    loginForm.setPassword("password");
    loginForm.setNumber("9798789212");
    loginForm.setEmail("email@example.com");
    return loginForm;
  }

  private MockHttpServletRequest getMockHttpRequest() {
    return new MockHttpServletRequest();
  }

  private MockMultipartFile getDummyMulitpartFile() {
    return new MockMultipartFile(
        "sample", "sample.txt", MediaType.TEXT_PLAIN_VALUE, "Sample text".getBytes());
  }
}
