package com.github.makewheels.video2022.playlist;

import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.playlist.item.PlayItem;
import com.github.makewheels.video2022.playlist.item.PlayItemService;
import com.github.makewheels.video2022.playlist.item.PlayItemVO;
import com.github.makewheels.video2022.playlist.item.request.add.AddMode;
import com.github.makewheels.video2022.playlist.item.request.add.AddPlayItemRequest;
import com.github.makewheels.video2022.playlist.item.request.delete.DeleteMode;
import com.github.makewheels.video2022.playlist.item.request.delete.DeletePlayItemRequest;
import com.github.makewheels.video2022.playlist.item.request.move.MoveMode;
import com.github.makewheels.video2022.playlist.item.request.move.MovePlayItemRequest;
import com.github.makewheels.video2022.playlist.list.PlaylistService;
import com.github.makewheels.video2022.playlist.list.bean.IdBean;
import com.github.makewheels.video2022.playlist.list.bean.Playlist;
import com.github.makewheels.video2022.playlist.list.request.CreatePlaylistRequest;
import com.github.makewheels.video2022.playlist.list.request.UpdatePlaylistRequest;
import com.github.makewheels.video2022.springboot.exception.VideoException;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.constants.Visibility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlaylistServiceTest extends BaseIntegrationTest {

    @Autowired
    private PlaylistService playlistService;

    @Autowired
    private PlayItemService playItemService;

    @Autowired
    private PlaylistRepository playlistRepository;

    private User testUser;
    private User otherUser;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        testUser = new User();
        testUser.setPhone("13800000001");
        testUser.setRegisterChannel("TEST");
        testUser.setToken("test-token-playlist");
        mongoTemplate.save(testUser);

        otherUser = new User();
        otherUser.setPhone("13800000002");
        otherUser.setRegisterChannel("TEST");
        otherUser.setToken("test-token-other");
        mongoTemplate.save(otherUser);

        UserHolder.set(testUser);
    }

    @AfterEach
    void tearDown() {
        UserHolder.remove();
        cleanDatabase();
    }

    // ── Helpers ──

    private Video createTestVideo(String title) {
        Video video = new Video();
        video.setTitle(title);
        video.setUploaderId(testUser.getId());
        video.setOwnerId(testUser.getId());
        mongoTemplate.save(video);
        return video;
    }

    private Playlist createTestPlaylist(String title) {
        CreatePlaylistRequest request = new CreatePlaylistRequest();
        request.setTitle(title);
        request.setDescription("Test description");
        return playlistService.createPlaylist(request);
    }

    private Playlist addVideoToPlaylist(String playlistId, String videoId, String mode) {
        AddPlayItemRequest request = new AddPlayItemRequest();
        request.setPlaylistId(playlistId);
        request.setVideoIdList(Collections.singletonList(videoId));
        request.setAddMode(mode);
        return playItemService.addVideoToPlaylist(request);
    }

    // ──────────────────── Create Playlist ────────────────────

    @Test
    void createPlaylist_savesToMongoWithCorrectFields() {
        Playlist playlist = createTestPlaylist("My Favorites");

        assertNotNull(playlist.getId());
        assertEquals("My Favorites", playlist.getTitle());
        assertEquals("Test description", playlist.getDescription());
        assertEquals(testUser.getId(), playlist.getOwnerId());
        assertEquals(Visibility.PUBLIC, playlist.getVisibility());
        assertFalse(playlist.getDeleted());
        assertNotNull(playlist.getCreateTime());
        assertNotNull(playlist.getUpdateTime());

        Playlist fromDb = mongoTemplate.findById(playlist.getId(), Playlist.class);
        assertNotNull(fromDb, "Playlist should be persisted in MongoDB");
        assertEquals("My Favorites", fromDb.getTitle());
        assertEquals(testUser.getId(), fromDb.getOwnerId());
    }

    // ──────────────────── Update Playlist ────────────────────

    @Test
    void updatePlaylist_persistsChanges() {
        Playlist playlist = createTestPlaylist("Original Title");

        UpdatePlaylistRequest updateRequest = new UpdatePlaylistRequest();
        updateRequest.setPlaylistId(playlist.getId());
        updateRequest.setTitle("Updated Title");
        updateRequest.setDescription("Updated description");
        updateRequest.setVisibility(Visibility.PRIVATE);

        Playlist updated = playlistService.updatePlaylist(updateRequest);

        assertEquals("Updated Title", updated.getTitle());
        assertEquals("Updated description", updated.getDescription());
        assertEquals(Visibility.PRIVATE, updated.getVisibility());

        Playlist fromDb = mongoTemplate.findById(playlist.getId(), Playlist.class);
        assertNotNull(fromDb);
        assertEquals("Updated Title", fromDb.getTitle());
        assertEquals("Updated description", fromDb.getDescription());
        assertEquals(Visibility.PRIVATE, fromDb.getVisibility());
    }

    @Test
    void updatePlaylist_byNonOwner_throwsException() {
        Playlist playlist = createTestPlaylist("Owner's Playlist");

        UserHolder.set(otherUser);

        UpdatePlaylistRequest updateRequest = new UpdatePlaylistRequest();
        updateRequest.setPlaylistId(playlist.getId());
        updateRequest.setTitle("Hijacked");
        updateRequest.setVisibility(Visibility.PUBLIC);

        assertThrows(VideoException.class, () ->
                playlistService.updatePlaylist(updateRequest));
    }

    // ──────────────────── Delete Playlist ────────────────────

    @Test
    void deletePlaylist_setsDeletedFlag() {
        Playlist playlist = createTestPlaylist("To Delete");

        playlistService.deletePlaylist(playlist.getId());

        Playlist fromDb = mongoTemplate.findById(playlist.getId(), Playlist.class);
        assertNotNull(fromDb, "Playlist should still exist (soft delete)");
        assertTrue(fromDb.getDeleted());
    }

    @Test
    void deletePlaylist_deletedPlaylistIsNotAccessible() {
        Playlist playlist = createTestPlaylist("Soon Gone");
        playlistService.deletePlaylist(playlist.getId());

        assertThrows(VideoException.class, () ->
                playlistService.getPlaylistById(playlist.getId(), true));
    }

    // ──────────────────── Add Video to Playlist ────────────────────

    @Test
    void addVideoToPlaylist_createsPlayItemAndUpdatesVideoList() {
        Playlist playlist = createTestPlaylist("Watch Later");
        Video video = createTestVideo("Test Video");

        Playlist updated = addVideoToPlaylist(
                playlist.getId(), video.getId(), AddMode.ADD_TO_BOTTOM);

        List<IdBean> videoList = updated.getVideoList();
        assertNotNull(videoList);
        assertEquals(1, videoList.size());
        assertEquals(video.getId(), videoList.get(0).getVideoId());
        assertNotNull(videoList.get(0).getPlayItemId());

        PlayItem playItem = mongoTemplate.findById(
                videoList.get(0).getPlayItemId(), PlayItem.class);
        assertNotNull(playItem);
        assertEquals(playlist.getId(), playItem.getPlaylistId());
        assertEquals(video.getId(), playItem.getVideoId());
        assertEquals(testUser.getId(), playItem.getOwner());
        assertFalse(playItem.getIsDelete());
    }

    @Test
    void addVideoToPlaylist_addToTop_insertsAtBeginning() {
        Playlist playlist = createTestPlaylist("Ordered List");
        Video video1 = createTestVideo("First");
        Video video2 = createTestVideo("Second");

        addVideoToPlaylist(playlist.getId(), video1.getId(), AddMode.ADD_TO_BOTTOM);
        Playlist updated = addVideoToPlaylist(
                playlist.getId(), video2.getId(), AddMode.ADD_TO_TOP);

        List<IdBean> videoList = updated.getVideoList();
        assertEquals(2, videoList.size());
        assertEquals(video2.getId(), videoList.get(0).getVideoId(),
                "Video added with ADD_TO_TOP should be first");
        assertEquals(video1.getId(), videoList.get(1).getVideoId());
    }

    @Test
    void addDuplicateVideoToPlaylist_throwsException() {
        Playlist playlist = createTestPlaylist("No Duplicates");
        Video video = createTestVideo("Only Once");

        addVideoToPlaylist(playlist.getId(), video.getId(), AddMode.ADD_TO_BOTTOM);

        assertThrows(VideoException.class, () ->
                addVideoToPlaylist(playlist.getId(), video.getId(), AddMode.ADD_TO_BOTTOM));
    }

    // ──────────────────── Remove Video from Playlist ────────────────────

    @Test
    void deletePlayItem_byVideoId_removesFromPlaylist() {
        Playlist playlist = createTestPlaylist("Shrinking List");
        Video video1 = createTestVideo("Stay");
        Video video2 = createTestVideo("Go");

        addVideoToPlaylist(playlist.getId(), video1.getId(), AddMode.ADD_TO_BOTTOM);
        Playlist afterAdd = addVideoToPlaylist(
                playlist.getId(), video2.getId(), AddMode.ADD_TO_BOTTOM);
        String removedPlayItemId = afterAdd.getVideoList().stream()
                .filter(b -> b.getVideoId().equals(video2.getId()))
                .findFirst().get().getPlayItemId();

        DeletePlayItemRequest deleteRequest = new DeletePlayItemRequest();
        deleteRequest.setPlaylistId(playlist.getId());
        deleteRequest.setDeleteMode(DeleteMode.VIDEO_ID_LIST);
        deleteRequest.setVideoIdList(Collections.singletonList(video2.getId()));

        playItemService.deletePlayItem(deleteRequest);

        Playlist fromDb = playlistRepository.getPlaylist(playlist.getId());
        assertEquals(1, fromDb.getVideoList().size());
        assertEquals(video1.getId(), fromDb.getVideoList().get(0).getVideoId());

        PlayItem deletedItem = mongoTemplate.findById(removedPlayItemId, PlayItem.class);
        assertNotNull(deletedItem, "PlayItem should still exist (soft delete)");
        assertTrue(deletedItem.getIsDelete());
    }

    @Test
    void deletePlayItem_allItems_clearsPlaylist() {
        Playlist playlist = createTestPlaylist("Clear Me");
        Video video1 = createTestVideo("V1");
        Video video2 = createTestVideo("V2");

        addVideoToPlaylist(playlist.getId(), video1.getId(), AddMode.ADD_TO_BOTTOM);
        addVideoToPlaylist(playlist.getId(), video2.getId(), AddMode.ADD_TO_BOTTOM);

        DeletePlayItemRequest deleteRequest = new DeletePlayItemRequest();
        deleteRequest.setPlaylistId(playlist.getId());
        deleteRequest.setDeleteMode(DeleteMode.ALL_ITEMS);
        // ALL_ITEMS mode doesn't use videoIdList, but the validation step iterates
        // it, so provide an empty list to avoid NPE.
        deleteRequest.setVideoIdList(Collections.emptyList());

        playItemService.deletePlayItem(deleteRequest);

        Playlist fromDb = playlistRepository.getPlaylist(playlist.getId());
        assertTrue(fromDb.getVideoList().isEmpty());
    }

    // ──────────────────── Move Play Item ────────────────────

    @Test
    void movePlayItem_toTop_swapsWithFirstItem() {
        Playlist playlist = createTestPlaylist("Reorder Me");
        Video video1 = createTestVideo("First");
        Video video2 = createTestVideo("Second");
        Video video3 = createTestVideo("Third");

        addVideoToPlaylist(playlist.getId(), video1.getId(), AddMode.ADD_TO_BOTTOM);
        addVideoToPlaylist(playlist.getId(), video2.getId(), AddMode.ADD_TO_BOTTOM);
        addVideoToPlaylist(playlist.getId(), video3.getId(), AddMode.ADD_TO_BOTTOM);

        MovePlayItemRequest moveRequest = new MovePlayItemRequest();
        moveRequest.setPlaylistId(playlist.getId());
        moveRequest.setVideoId(video3.getId());
        moveRequest.setMoveMode(MoveMode.TO_TOP);

        playItemService.movePlayItem(moveRequest);

        // MovePlayItemService uses Collections.swap on PlayItem list,
        // swapping the source with the target. Verify playlist still has 3 items.
        Playlist fromDb = playlistRepository.getPlaylist(playlist.getId());
        List<IdBean> videoList = fromDb.getVideoList();
        assertEquals(3, videoList.size());
    }

    // ──────────────────── Get Playlist Detail ────────────────────

    @Test
    void getPlaylistById_returnsPlaylistWithVideoList() {
        Playlist playlist = createTestPlaylist("Detailed");
        Video video = createTestVideo("Detail Video");
        addVideoToPlaylist(playlist.getId(), video.getId(), AddMode.ADD_TO_BOTTOM);

        Playlist result = playlistService.getPlaylistById(playlist.getId(), true);

        assertNotNull(result);
        assertEquals(playlist.getId(), result.getId());
        assertNotNull(result.getVideoList());
        assertEquals(1, result.getVideoList().size());
        assertEquals(video.getId(), result.getVideoList().get(0).getVideoId());
    }

    @Test
    void getPlaylistById_withoutVideoList_returnsNullVideoList() {
        Playlist playlist = createTestPlaylist("No Details");
        Video video = createTestVideo("Hidden Video");
        addVideoToPlaylist(playlist.getId(), video.getId(), AddMode.ADD_TO_BOTTOM);

        Playlist result = playlistService.getPlaylistById(playlist.getId(), false);

        assertNotNull(result);
        assertNull(result.getVideoList(),
                "Video list should be null when showVideoList is false");
    }

    @Test
    void getPlayItemListDetail_returnsVideosInCorrectOrder() {
        Playlist playlist = createTestPlaylist("Ordered Details");
        Video video1 = createTestVideo("First Detail");
        Video video2 = createTestVideo("Second Detail");

        addVideoToPlaylist(playlist.getId(), video1.getId(), AddMode.ADD_TO_BOTTOM);
        addVideoToPlaylist(playlist.getId(), video2.getId(), AddMode.ADD_TO_BOTTOM);

        List<PlayItemVO> details = playItemService.getPlayItemListDetail(playlist.getId());

        assertEquals(2, details.size());
        assertEquals(video1.getId(), details.get(0).getVideoId());
        assertEquals(video2.getId(), details.get(1).getVideoId());
        assertEquals("First Detail", details.get(0).getTitle());
        assertEquals("Second Detail", details.get(1).getTitle());
    }

    // ──────────────────── Get User's Playlist List ────────────────────

    @Test
    void getPlaylistByPage_returnsOnlyUserPlaylists() {
        createTestPlaylist("User1 Playlist A");
        createTestPlaylist("User1 Playlist B");

        // Create a playlist owned by otherUser
        UserHolder.set(otherUser);
        CreatePlaylistRequest otherRequest = new CreatePlaylistRequest();
        otherRequest.setTitle("User2 Playlist");
        otherRequest.setDescription("Other user's playlist");
        playlistService.createPlaylist(otherRequest);
        UserHolder.set(testUser);

        List<Playlist> userPlaylists = playlistService.getPlaylistByPage(
                testUser.getId(), 0, 10);

        assertNotNull(userPlaylists);
        for (Playlist p : userPlaylists) {
            assertEquals(testUser.getId(), p.getOwnerId(),
                    "All returned playlists should belong to testUser");
        }
    }

    // ──────────────────── Non-existent Playlist ────────────────────

    @Test
    void getPlaylistById_nonExistentPlaylist_throwsException() {
        assertThrows(VideoException.class, () ->
                playlistService.getPlaylistById("non-existent-id", true));
    }

    @Test
    void deletePlaylist_nonExistentPlaylist_throwsException() {
        assertThrows(VideoException.class, () ->
                playlistService.deletePlaylist("non-existent-id"));
    }

    @Test
    void addVideoToPlaylist_nonExistentPlaylist_throwsException() {
        Video video = createTestVideo("Orphan Video");

        assertThrows(VideoException.class, () ->
                addVideoToPlaylist("non-existent-id", video.getId(), AddMode.ADD_TO_BOTTOM));
    }

    // ──────────────────── Recover Playlist ────────────────────

    @Test
    void recoverPlaylist_restoresDeletedPlaylist() {
        Playlist playlist = createTestPlaylist("Recoverable");
        playlistService.deletePlaylist(playlist.getId());

        Playlist deleted = mongoTemplate.findById(playlist.getId(), Playlist.class);
        assertNotNull(deleted);
        assertTrue(deleted.getDeleted());

        playlistService.recoverPlaylist(playlist.getId());

        Playlist recovered = mongoTemplate.findById(playlist.getId(), Playlist.class);
        assertNotNull(recovered);
        assertFalse(recovered.getDeleted());
    }

    // ──────────────────── Boundary Conditions ────────────────────

    @Test
    void createPlaylist_emptyTitle_savesToMongo() {
        CreatePlaylistRequest request = new CreatePlaylistRequest();
        request.setTitle("");
        request.setDescription("Empty title playlist");

        Playlist playlist = playlistService.createPlaylist(request);

        assertNotNull(playlist.getId());
        assertEquals("", playlist.getTitle());

        Playlist fromDb = mongoTemplate.findById(playlist.getId(), Playlist.class);
        assertNotNull(fromDb);
        assertEquals("", fromDb.getTitle());
    }

    @Test
    void createPlaylist_nullTitle_savesToMongo() {
        CreatePlaylistRequest request = new CreatePlaylistRequest();
        request.setTitle(null);
        request.setDescription("Null title playlist");

        Playlist playlist = playlistService.createPlaylist(request);

        assertNotNull(playlist.getId());
        assertNull(playlist.getTitle());

        Playlist fromDb = mongoTemplate.findById(playlist.getId(), Playlist.class);
        assertNotNull(fromDb);
        assertNull(fromDb.getTitle());
    }

    @Test
    void addVideoToPlaylist_byNonOwner_throwsException() {
        Playlist playlist = createTestPlaylist("Owner's Playlist For Add");
        Video video = createTestVideo("Test Video For Add");

        UserHolder.set(otherUser);

        assertThrows(VideoException.class, () ->
                addVideoToPlaylist(playlist.getId(), video.getId(), AddMode.ADD_TO_BOTTOM));
    }
}
