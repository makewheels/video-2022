package com.github.makewheels.video2022.etc.check;

import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.file.constants.FileStatus;
import com.github.makewheels.video2022.playlist.item.request.add.AddMode;
import com.github.makewheels.video2022.playlist.item.request.delete.DeleteMode;
import com.github.makewheels.video2022.playlist.item.request.move.MoveMode;
import com.github.makewheels.video2022.playlist.list.bean.IdBean;
import com.github.makewheels.video2022.playlist.list.bean.Playlist;
import com.github.makewheels.video2022.springboot.exception.VideoException;
import com.github.makewheels.video2022.system.response.ErrorCode;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.bean.dto.CreateVideoDTO;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.bean.entity.Watch;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import com.github.makewheels.video2022.video.constants.VideoType;
import com.github.makewheels.video2022.video.constants.Visibility;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CheckServiceTest extends BaseIntegrationTest {

    @Autowired
    private CheckService checkService;

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    @AfterEach
    void tearDown() {
        UserHolder.remove();
    }

    // ========== Helper methods ==========

    private User createAndSaveUser() {
        User user = new User();
        return mongoTemplate.save(user);
    }

    private Video createAndSaveVideo(String uploaderId) {
        Video video = new Video();
        video.setUploaderId(uploaderId);
        return mongoTemplate.save(video);
    }

    private Video createAndSaveVideoWithWatch(String uploaderId, String watchId) {
        Video video = new Video();
        video.setUploaderId(uploaderId);
        Watch watch = new Watch();
        watch.setWatchId(watchId);
        video.setWatch(watch);
        return mongoTemplate.save(video);
    }

    private Playlist createAndSavePlaylist(String ownerId, boolean deleted) {
        Playlist playlist = new Playlist();
        playlist.setOwnerId(ownerId);
        playlist.setDeleted(deleted);
        playlist.setVideoList(new ArrayList<>());
        return mongoTemplate.save(playlist);
    }

    private Playlist addVideoToPlaylist(Playlist playlist, String videoId) {
        IdBean idBean = new IdBean();
        idBean.setPlayItemId(new ObjectId().toHexString());
        idBean.setVideoId(videoId);
        List<IdBean> videoList = playlist.getVideoList();
        if (videoList == null) {
            videoList = new ArrayList<>();
            playlist.setVideoList(videoList);
        }
        videoList.add(idBean);
        return mongoTemplate.save(playlist);
    }

    private File createAndSaveFile(String uploaderId, String fileStatus) {
        File file = new File();
        file.setUploaderId(uploaderId);
        file.setFileStatus(fileStatus);
        return mongoTemplate.save(file);
    }

    // ========== User checks ==========

    @Nested
    class CheckUserExistTests {
        @Test
        void shouldPass_whenUserExists() {
            User user = createAndSaveUser();
            assertDoesNotThrow(() -> checkService.checkUserExist(user.getId()));
        }

        @Test
        void shouldThrow_whenUserNotFound() {
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkUserExist(new ObjectId().toHexString()));
            assertEquals(ErrorCode.USER_NOT_EXIST, ex.getErrorCode());
        }

        @Test
        void shouldThrow_whenUserIdIsNull() {
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkUserExist(null));
            assertEquals(ErrorCode.USER_NOT_EXIST, ex.getErrorCode());
        }
    }

    @Nested
    class CheckUserHolderExistTests {
        @Test
        void shouldPass_whenUserHolderIsSet() {
            User user = new User();
            user.setId("test-user-id");
            UserHolder.set(user);
            assertDoesNotThrow(() -> checkService.checkUserHolderExist());
        }

        @Test
        void shouldThrow_whenUserHolderIsEmpty() {
            UserHolder.remove();
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkUserHolderExist());
            assertEquals(ErrorCode.USER_NOT_LOGIN, ex.getErrorCode());
        }
    }

    // ========== Video checks ==========

    @Nested
    class CheckVideoExistTests {
        @Test
        void shouldPass_whenVideoExists() {
            Video video = createAndSaveVideo("some-uploader");
            assertDoesNotThrow(() -> checkService.checkVideoExist(video.getId()));
        }

        @Test
        void shouldThrow_whenVideoNotFound() {
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkVideoExist(new ObjectId().toHexString()));
            assertEquals(ErrorCode.VIDEO_NOT_EXIST, ex.getErrorCode());
        }

        @Test
        void shouldThrow_whenVideoIdIsNull() {
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkVideoExist(null));
            assertEquals(ErrorCode.VIDEO_NOT_EXIST, ex.getErrorCode());
        }
    }

    @Nested
    class CheckCreateVideoDTOTests {
        @Test
        void shouldPass_whenUserUploadWithValidParams() {
            CreateVideoDTO dto = new CreateVideoDTO();
            dto.setVideoType(VideoType.USER_UPLOAD);
            dto.setRawFilename("test.mp4");
            dto.setSize(1024L);
            assertDoesNotThrow(() -> checkService.checkCreateVideoDTO(dto));
        }

        @Test
        void shouldPass_whenYoutubeType() {
            CreateVideoDTO dto = new CreateVideoDTO();
            dto.setVideoType(VideoType.YOUTUBE);
            assertDoesNotThrow(() -> checkService.checkCreateVideoDTO(dto));
        }

        @Test
        void shouldThrow_whenVideoTypeIsEmpty() {
            CreateVideoDTO dto = new CreateVideoDTO();
            dto.setVideoType("");
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkCreateVideoDTO(dto));
            assertEquals(ErrorCode.VIDEO_CREATE_ARG_ILLEGAL, ex.getErrorCode());
        }

        @Test
        void shouldThrow_whenVideoTypeIsNull() {
            CreateVideoDTO dto = new CreateVideoDTO();
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkCreateVideoDTO(dto));
            assertEquals(ErrorCode.VIDEO_CREATE_ARG_ILLEGAL, ex.getErrorCode());
        }

        @Test
        void shouldThrow_whenUserUploadMissingRawFilename() {
            CreateVideoDTO dto = new CreateVideoDTO();
            dto.setVideoType(VideoType.USER_UPLOAD);
            dto.setSize(1024L);
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkCreateVideoDTO(dto));
            assertEquals(ErrorCode.VIDEO_CREATE_ARG_ILLEGAL, ex.getErrorCode());
        }

        @Test
        void shouldThrow_whenUserUploadEmptyRawFilename() {
            CreateVideoDTO dto = new CreateVideoDTO();
            dto.setVideoType(VideoType.USER_UPLOAD);
            dto.setRawFilename("");
            dto.setSize(1024L);
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkCreateVideoDTO(dto));
            assertEquals(ErrorCode.VIDEO_CREATE_ARG_ILLEGAL, ex.getErrorCode());
        }

        @Test
        void shouldThrow_whenUserUploadSizeIsNull() {
            CreateVideoDTO dto = new CreateVideoDTO();
            dto.setVideoType(VideoType.USER_UPLOAD);
            dto.setRawFilename("test.mp4");
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkCreateVideoDTO(dto));
            assertEquals(ErrorCode.VIDEO_CREATE_ARG_ILLEGAL, ex.getErrorCode());
        }

        @Test
        void shouldThrow_whenUserUploadSizeIsZero() {
            CreateVideoDTO dto = new CreateVideoDTO();
            dto.setVideoType(VideoType.USER_UPLOAD);
            dto.setRawFilename("test.mp4");
            dto.setSize(0L);
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkCreateVideoDTO(dto));
            assertEquals(ErrorCode.VIDEO_CREATE_ARG_ILLEGAL, ex.getErrorCode());
        }
    }

    @Nested
    class CheckWatchIdExistTests {
        @Test
        void shouldPass_whenWatchIdExists() {
            String watchId = new ObjectId().toHexString();
            createAndSaveVideoWithWatch("uploader", watchId);
            assertDoesNotThrow(() -> checkService.checkWatchIdExist(watchId));
        }

        @Test
        void shouldThrow_whenWatchIdNotFound() {
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkWatchIdExist("nonexistent-watch-id"));
            assertEquals(ErrorCode.VIDEO_NOT_EXIST, ex.getErrorCode());
        }
    }

    @Nested
    class CheckVideoIsNotReadyTests {
        @Test
        void shouldPass_whenVideoStatusIsCreated() {
            Video video = new Video();
            video.setStatus(VideoStatus.CREATED);
            assertDoesNotThrow(() -> checkService.checkVideoIsNotReady(video));
        }

        @Test
        void shouldPass_whenVideoStatusIsTranscoding() {
            Video video = new Video();
            video.setStatus(VideoStatus.TRANSCODING);
            assertDoesNotThrow(() -> checkService.checkVideoIsNotReady(video));
        }

        @Test
        void shouldThrow_whenVideoStatusIsReady() {
            Video video = new Video();
            video.setStatus(VideoStatus.READY);
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkVideoIsNotReady(video));
            assertEquals(ErrorCode.VIDEO_IS_READY, ex.getErrorCode());
        }
    }

    @Nested
    class CheckVideoBelongsToUserTests {
        @Test
        void shouldPass_whenVideoOwnedByUser() {
            User user = createAndSaveUser();
            Video video = createAndSaveVideo(user.getId());
            assertDoesNotThrow(() ->
                    checkService.checkVideoBelongsToUser(video.getId(), user.getId()));
        }

        @Test
        void shouldThrow_whenVideoNotOwnedByUser() {
            User owner = createAndSaveUser();
            User other = createAndSaveUser();
            Video video = createAndSaveVideo(owner.getId());
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkVideoBelongsToUser(video.getId(), other.getId()));
            assertEquals(ErrorCode.VIDEO_AND_UPLOADER_NOT_MATCH, ex.getErrorCode());
        }

        @Test
        void shouldThrow_whenVideoNotFound() {
            User user = createAndSaveUser();
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkVideoBelongsToUser(
                            new ObjectId().toHexString(), user.getId()));
            assertEquals(ErrorCode.VIDEO_NOT_EXIST, ex.getErrorCode());
        }

        @Test
        void shouldThrow_whenUserNotFound() {
            User realUser = createAndSaveUser();
            Video video = createAndSaveVideo(realUser.getId());
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkVideoBelongsToUser(
                            video.getId(), new ObjectId().toHexString()));
            assertEquals(ErrorCode.USER_NOT_EXIST, ex.getErrorCode());
        }
    }

    // ========== Visibility and mode checks ==========

    @Nested
    class CheckVideoVisibilityTests {
        @Test
        void shouldPass_whenPublic() {
            assertDoesNotThrow(() -> checkService.checkVideoVisibility(Visibility.PUBLIC));
        }

        @Test
        void shouldPass_whenUnlisted() {
            assertDoesNotThrow(() -> checkService.checkVideoVisibility(Visibility.UNLISTED));
        }

        @Test
        void shouldPass_whenPrivate() {
            assertDoesNotThrow(() -> checkService.checkVideoVisibility(Visibility.PRIVATE));
        }

        @Test
        void shouldThrow_whenInvalidVisibility() {
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkVideoVisibility("INVALID"));
            assertEquals(ErrorCode.FAIL, ex.getErrorCode());
        }

        @Test
        void shouldThrow_whenVisibilityIsNull() {
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkVideoVisibility(null));
            assertEquals(ErrorCode.FAIL, ex.getErrorCode());
        }
    }

    @Nested
    class CheckPlaylistAddModeTests {
        @Test
        void shouldPass_whenAddToTop() {
            assertDoesNotThrow(() -> checkService.checkPlaylistAddMode(AddMode.ADD_TO_TOP));
        }

        @Test
        void shouldPass_whenAddToBottom() {
            assertDoesNotThrow(() -> checkService.checkPlaylistAddMode(AddMode.ADD_TO_BOTTOM));
        }

        @Test
        void shouldThrow_whenInvalidAddMode() {
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkPlaylistAddMode("INVALID_MODE"));
            assertEquals(ErrorCode.FAIL, ex.getErrorCode());
        }

        @Test
        void shouldThrow_whenAddModeIsNull() {
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkPlaylistAddMode(null));
            assertEquals(ErrorCode.FAIL, ex.getErrorCode());
        }
    }

    @Nested
    class CheckDeletePlayItemModeTests {
        @Test
        void shouldPass_forAllValidDeleteModes() {
            for (String mode : DeleteMode.ALL) {
                assertDoesNotThrow(() -> checkService.checkDeletePlayItemMode(mode),
                        "Should accept valid deleteMode: " + mode);
            }
        }

        @Test
        void shouldThrow_whenInvalidDeleteMode() {
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkDeletePlayItemMode("INVALID"));
            assertEquals(ErrorCode.FAIL, ex.getErrorCode());
        }

        @Test
        void shouldThrow_whenDeleteModeIsNull() {
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkDeletePlayItemMode(null));
            assertEquals(ErrorCode.FAIL, ex.getErrorCode());
        }
    }

    @Nested
    class CheckPlaylistItemMoveModeTests {
        @Test
        void shouldPass_forAllValidMoveModes() {
            for (String mode : MoveMode.ALL) {
                assertDoesNotThrow(() -> checkService.checkPlaylistItemMoveMode(mode),
                        "Should accept valid moveMode: " + mode);
            }
        }

        @Test
        void shouldThrow_whenInvalidMoveMode() {
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkPlaylistItemMoveMode("INVALID"));
            assertEquals(ErrorCode.FAIL, ex.getErrorCode());
        }

        @Test
        void shouldThrow_whenMoveModeIsNull() {
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkPlaylistItemMoveMode(null));
            assertEquals(ErrorCode.FAIL, ex.getErrorCode());
        }
    }

    // ========== Playlist checks ==========

    @Nested
    class CheckPlaylistExistTests {
        @Test
        void shouldPass_whenPlaylistExistsAndNotDeleted() {
            User user = createAndSaveUser();
            Playlist playlist = createAndSavePlaylist(user.getId(), false);
            assertDoesNotThrow(() -> checkService.checkPlaylistExist(playlist.getId()));
        }

        @Test
        void shouldThrow_whenPlaylistNotFound() {
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkPlaylistExist(new ObjectId().toHexString()));
            assertEquals(ErrorCode.PLAYLIST_NOT_EXIST, ex.getErrorCode());
        }

        @Test
        void shouldThrow_whenPlaylistIsDeleted() {
            User user = createAndSaveUser();
            Playlist playlist = createAndSavePlaylist(user.getId(), true);
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkPlaylistExist(playlist.getId()));
            assertEquals(ErrorCode.PLAYLIST_DELETED, ex.getErrorCode());
        }
    }

    @Nested
    class CheckPlaylistOwnerTests {
        @Test
        void shouldPass_whenOwnerMatches() {
            User user = createAndSaveUser();
            Playlist playlist = createAndSavePlaylist(user.getId(), false);
            assertDoesNotThrow(() ->
                    checkService.checkPlaylistOwner(playlist.getId(), user.getId()));
        }

        @Test
        void shouldThrow_whenOwnerDoesNotMatch() {
            User owner = createAndSaveUser();
            User other = createAndSaveUser();
            Playlist playlist = createAndSavePlaylist(owner.getId(), false);
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkPlaylistOwner(playlist.getId(), other.getId()));
            assertEquals(ErrorCode.PLAYLIST_AND_USER_NOT_MATCH, ex.getErrorCode());
        }

        @Test
        void shouldThrow_whenPlaylistNotFound() {
            User user = createAndSaveUser();
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkPlaylistOwner(
                            new ObjectId().toHexString(), user.getId()));
            assertEquals(ErrorCode.PLAYLIST_NOT_EXIST, ex.getErrorCode());
        }

        @Test
        void shouldThrow_whenUserNotFound() {
            User realOwner = createAndSaveUser();
            Playlist playlist = createAndSavePlaylist(realOwner.getId(), false);
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkPlaylistOwner(
                            playlist.getId(), new ObjectId().toHexString()));
            assertEquals(ErrorCode.USER_NOT_EXIST, ex.getErrorCode());
        }
    }

    @Nested
    class CheckPlaylistCanDeleteTests {
        @Test
        void shouldPass_whenPlaylistIsDeletedAndOwnedByUser() {
            User user = createAndSaveUser();
            Playlist playlist = createAndSavePlaylist(user.getId(), true);
            assertDoesNotThrow(() ->
                    checkService.checkPlaylistCanDelete(playlist.getId(), user.getId()));
        }

        @Test
        void shouldThrow_whenPlaylistNotFound() {
            User user = createAndSaveUser();
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkPlaylistCanDelete(
                            new ObjectId().toHexString(), user.getId()));
            assertEquals(ErrorCode.PLAYLIST_NOT_EXIST, ex.getErrorCode());
        }

        @Test
        void shouldThrow_whenPlaylistNotOwnedByUser() {
            User owner = createAndSaveUser();
            User other = createAndSaveUser();
            Playlist playlist = createAndSavePlaylist(owner.getId(), true);
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkPlaylistCanDelete(
                            playlist.getId(), other.getId()));
            assertEquals(ErrorCode.PLAYLIST_AND_USER_NOT_MATCH, ex.getErrorCode());
        }

        @Test
        void shouldThrow_whenPlaylistIsNotDeleted() {
            User user = createAndSaveUser();
            Playlist playlist = createAndSavePlaylist(user.getId(), false);
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkPlaylistCanDelete(
                            playlist.getId(), user.getId()));
            assertEquals(ErrorCode.FAIL, ex.getErrorCode());
        }

        @Test
        void shouldThrow_whenUserNotFound() {
            User realOwner = createAndSaveUser();
            Playlist playlist = createAndSavePlaylist(realOwner.getId(), true);
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkPlaylistCanDelete(
                            playlist.getId(), new ObjectId().toHexString()));
            assertEquals(ErrorCode.USER_NOT_EXIST, ex.getErrorCode());
        }
    }

    // ========== Playlist-video membership checks ==========

    @Nested
    class GetVideoIdsTests {
        @Test
        void shouldReturnVideoIds_whenPlaylistHasVideos() {
            Playlist playlist = new Playlist();
            IdBean bean1 = new IdBean();
            bean1.setVideoId("v1");
            bean1.setPlayItemId("p1");
            IdBean bean2 = new IdBean();
            bean2.setVideoId("v2");
            bean2.setPlayItemId("p2");
            playlist.setVideoList(Arrays.asList(bean1, bean2));

            List<String> videoIds = checkService.getVideoIds(playlist);
            assertEquals(2, videoIds.size());
            assertTrue(videoIds.contains("v1"));
            assertTrue(videoIds.contains("v2"));
        }

        @Test
        void shouldReturnEmptyList_whenVideoListIsNull() {
            Playlist playlist = new Playlist();
            playlist.setVideoList(null);
            List<String> videoIds = checkService.getVideoIds(playlist);
            assertNotNull(videoIds);
            assertTrue(videoIds.isEmpty());
        }

        @Test
        void shouldReturnEmptyList_whenVideoListIsEmpty() {
            Playlist playlist = new Playlist();
            playlist.setVideoList(new ArrayList<>());
            List<String> videoIds = checkService.getVideoIds(playlist);
            assertNotNull(videoIds);
            assertTrue(videoIds.isEmpty());
        }
    }

    @Nested
    class ContainsVideoIdTests {
        @Test
        void shouldReturnTrue_whenVideoIdExists() {
            Playlist playlist = new Playlist();
            IdBean bean = new IdBean();
            bean.setVideoId("v1");
            bean.setPlayItemId("p1");
            playlist.setVideoList(Collections.singletonList(bean));

            assertTrue(checkService.containsVideoId(playlist, "v1"));
        }

        @Test
        void shouldReturnFalse_whenVideoIdNotFound() {
            Playlist playlist = new Playlist();
            playlist.setVideoList(new ArrayList<>());
            assertFalse(checkService.containsVideoId(playlist, "v1"));
        }
    }

    @Nested
    class ContainsPlayItemIdTests {
        @Test
        void shouldReturnTrue_whenPlayItemIdMatchesVideoId() {
            // Note: containsPlayItemId uses getVideoIds internally,
            // so it checks against videoId field, not playItemId
            Playlist playlist = new Playlist();
            IdBean bean = new IdBean();
            bean.setVideoId("item-id");
            bean.setPlayItemId("p1");
            playlist.setVideoList(Collections.singletonList(bean));

            assertTrue(checkService.containsPlayItemId(playlist, "item-id"));
        }

        @Test
        void shouldReturnFalse_whenNotFound() {
            Playlist playlist = new Playlist();
            playlist.setVideoList(new ArrayList<>());
            assertFalse(checkService.containsPlayItemId(playlist, "nonexistent"));
        }
    }

    @Nested
    class IsVideoBelongsToPlaylistTests {
        @Test
        void shouldReturnTrue_whenVideoInPlaylist() {
            User user = createAndSaveUser();
            Video video = createAndSaveVideo(user.getId());
            Playlist playlist = createAndSavePlaylist(user.getId(), false);
            addVideoToPlaylist(playlist, video.getId());

            assertTrue(checkService.isVideoBelongsToPlaylist(
                    playlist.getId(), video.getId()));
        }

        @Test
        void shouldReturnFalse_whenVideoNotInPlaylist() {
            User user = createAndSaveUser();
            Video video = createAndSaveVideo(user.getId());
            Playlist playlist = createAndSavePlaylist(user.getId(), false);

            assertFalse(checkService.isVideoBelongsToPlaylist(
                    playlist.getId(), video.getId()));
        }

        @Test
        void shouldThrow_whenPlaylistNotFound() {
            User user = createAndSaveUser();
            Video video = createAndSaveVideo(user.getId());
            assertThrows(VideoException.class,
                    () -> checkService.isVideoBelongsToPlaylist(
                            new ObjectId().toHexString(), video.getId()));
        }

        @Test
        void shouldThrow_whenVideoNotFound() {
            User user = createAndSaveUser();
            Playlist playlist = createAndSavePlaylist(user.getId(), false);
            assertThrows(VideoException.class,
                    () -> checkService.isVideoBelongsToPlaylist(
                            playlist.getId(), new ObjectId().toHexString()));
        }
    }

    @Nested
    class CheckVideoNotBelongToPlaylistTests {
        @Test
        void shouldPass_whenNoVideoInPlaylist() {
            User user = createAndSaveUser();
            Video v1 = createAndSaveVideo(user.getId());
            Video v2 = createAndSaveVideo(user.getId());
            Playlist playlist = createAndSavePlaylist(user.getId(), false);

            assertDoesNotThrow(() ->
                    checkService.checkVideoNotBelongToPlaylist(
                            playlist.getId(), Arrays.asList(v1.getId(), v2.getId())));
        }

        @Test
        void shouldThrow_whenVideoAlreadyInPlaylist() {
            User user = createAndSaveUser();
            Video video = createAndSaveVideo(user.getId());
            Playlist playlist = createAndSavePlaylist(user.getId(), false);
            addVideoToPlaylist(playlist, video.getId());

            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkVideoNotBelongToPlaylist(
                            playlist.getId(), Collections.singletonList(video.getId())));
            assertEquals(ErrorCode.FAIL, ex.getErrorCode());
        }
    }

    @Nested
    class CheckVideoBelongToPlaylistSingleTests {
        @Test
        void shouldPass_whenVideoInPlaylist() {
            User user = createAndSaveUser();
            Video video = createAndSaveVideo(user.getId());
            Playlist playlist = createAndSavePlaylist(user.getId(), false);
            addVideoToPlaylist(playlist, video.getId());

            assertDoesNotThrow(() ->
                    checkService.checkVideoBelongToPlaylist(
                            playlist.getId(), video.getId()));
        }

        @Test
        void shouldThrow_whenVideoNotInPlaylist() {
            User user = createAndSaveUser();
            Video video = createAndSaveVideo(user.getId());
            Playlist playlist = createAndSavePlaylist(user.getId(), false);

            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkVideoBelongToPlaylist(
                            playlist.getId(), video.getId()));
            assertEquals(ErrorCode.FAIL, ex.getErrorCode());
        }
    }

    @Nested
    class CheckVideoBelongToPlaylistBatchTests {
        @Test
        void shouldPass_whenAllVideosInPlaylist() {
            User user = createAndSaveUser();
            Video v1 = createAndSaveVideo(user.getId());
            Video v2 = createAndSaveVideo(user.getId());
            Playlist playlist = createAndSavePlaylist(user.getId(), false);
            addVideoToPlaylist(playlist, v1.getId());
            addVideoToPlaylist(playlist, v2.getId());

            assertDoesNotThrow(() ->
                    checkService.checkVideoBelongToPlaylist(
                            playlist.getId(), Arrays.asList(v1.getId(), v2.getId())));
        }

        @Test
        void shouldThrow_whenOneVideoNotInPlaylist() {
            User user = createAndSaveUser();
            Video v1 = createAndSaveVideo(user.getId());
            Video v2 = createAndSaveVideo(user.getId());
            Playlist playlist = createAndSavePlaylist(user.getId(), false);
            addVideoToPlaylist(playlist, v1.getId());

            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkVideoBelongToPlaylist(
                            playlist.getId(), Arrays.asList(v1.getId(), v2.getId())));
            assertEquals(ErrorCode.FAIL, ex.getErrorCode());
        }
    }

    // ========== File checks ==========

    @Nested
    class CheckFileExistTests {
        @Test
        void shouldPass_whenFileExists() {
            File file = createAndSaveFile("uploader", FileStatus.CREATED);
            assertDoesNotThrow(() -> checkService.checkFileExist(file.getId()));
        }

        @Test
        void shouldThrow_whenFileNotFound() {
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkFileExist(new ObjectId().toHexString()));
            assertEquals(ErrorCode.FILE_NOT_EXIST, ex.getErrorCode());
        }

        @Test
        void shouldThrow_whenFileIdIsNull() {
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkFileExist(null));
            assertEquals(ErrorCode.FILE_NOT_EXIST, ex.getErrorCode());
        }
    }

    @Nested
    class CheckFileIsReadyTests {
        @Test
        void shouldPass_whenFileIsReady() {
            File file = new File();
            file.setFileStatus(FileStatus.READY);
            assertDoesNotThrow(() -> checkService.checkFileIsReady(file));
        }

        @Test
        void shouldThrow_whenFileIsCreated() {
            File file = new File();
            file.setFileStatus(FileStatus.CREATED);
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkFileIsReady(file));
            assertEquals(ErrorCode.FILE_NOT_READY, ex.getErrorCode());
        }

        @Test
        void shouldThrow_whenFileStatusIsNull() {
            File file = new File();
            file.setFileStatus(null);
            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkFileIsReady(file));
            assertEquals(ErrorCode.FILE_NOT_READY, ex.getErrorCode());
        }
    }

    @Nested
    class CheckFileBelongsToUserHolderTests {
        @Test
        void shouldPass_whenFileOwnedByCurrentUser() {
            User user = createAndSaveUser();
            UserHolder.set(user);
            File file = createAndSaveFile(user.getId(), FileStatus.CREATED);

            assertDoesNotThrow(() ->
                    checkService.checkFileBelongsToUserHolder(file.getId()));
        }

        @Test
        void shouldThrow_whenFileNotOwnedByCurrentUser() {
            User currentUser = createAndSaveUser();
            User otherUser = createAndSaveUser();
            UserHolder.set(currentUser);
            File file = createAndSaveFile(otherUser.getId(), FileStatus.CREATED);

            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkFileBelongsToUserHolder(file.getId()));
            assertEquals(ErrorCode.FILE_AND_USER_NOT_MATCH, ex.getErrorCode());
        }

        @Test
        void shouldThrow_whenUserHolderIsEmpty() {
            User user = createAndSaveUser();
            File file = createAndSaveFile(user.getId(), FileStatus.CREATED);
            UserHolder.remove();

            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkFileBelongsToUserHolder(file.getId()));
            assertEquals(ErrorCode.USER_NOT_LOGIN, ex.getErrorCode());
        }

        @Test
        void shouldThrow_whenFileNotFound() {
            User user = createAndSaveUser();
            UserHolder.set(user);

            VideoException ex = assertThrows(VideoException.class,
                    () -> checkService.checkFileBelongsToUserHolder(
                            new ObjectId().toHexString()));
            assertEquals(ErrorCode.FILE_NOT_EXIST, ex.getErrorCode());
        }
    }
}
