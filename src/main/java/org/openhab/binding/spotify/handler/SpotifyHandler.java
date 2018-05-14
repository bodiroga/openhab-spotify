/**
 * Copyright (c) 2014,2018 by the respective copyright holders.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.spotify.handler;

import static org.openhab.binding.spotify.SpotifyBindingConstants.*;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.library.types.NextPreviousType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.library.types.PlayPauseType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.eclipse.smarthome.core.thing.binding.builder.ThingBuilder;
import org.eclipse.smarthome.core.thing.type.ChannelType;
import org.eclipse.smarthome.core.thing.type.ChannelTypeUID;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.core.types.StateDescription;
import org.eclipse.smarthome.core.types.StateOption;
import org.openhab.binding.spotify.internal.AuthorizationCodeListener;
import org.openhab.binding.spotify.internal.PlaybackInformationCache;
import org.openhab.binding.spotify.internal.SpotifyConfiguration;
import org.openhab.binding.spotify.internal.SpotifyHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.SpotifyHttpManager;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import com.wrapper.spotify.model_objects.miscellaneous.CurrentlyPlayingContext;
import com.wrapper.spotify.model_objects.miscellaneous.Device;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.Paging;
import com.wrapper.spotify.model_objects.specification.PlaylistSimplified;
import com.wrapper.spotify.model_objects.specification.User;
import com.wrapper.spotify.requests.authorization.authorization_code.AuthorizationCodeRefreshRequest;
import com.wrapper.spotify.requests.authorization.authorization_code.AuthorizationCodeRequest;
import com.wrapper.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;
import com.wrapper.spotify.requests.data.player.GetInformationAboutUsersCurrentPlaybackRequest;
import com.wrapper.spotify.requests.data.player.GetUsersAvailableDevicesRequest;
import com.wrapper.spotify.requests.data.player.SeekToPositionInCurrentlyPlayingTrackRequest;
import com.wrapper.spotify.requests.data.playlists.GetListOfCurrentUsersPlaylistsRequest;
import com.wrapper.spotify.requests.data.users_profile.GetCurrentUsersProfileRequest;

/**
 * The {@link SpotifyHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Aitor Iturrioz - Initial contribution
 */
@NonNullByDefault
public class SpotifyHandler extends BaseThingHandler implements AuthorizationCodeListener {

    private final Logger logger = LoggerFactory.getLogger(SpotifyHandler.class);

    private final String STATE = "x4xkmn9pu3j6ukrs8n";
    private final String SCOPE = "user-read-playback-state,user-modify-playback-state,playlist-read-private";
    private final int TOKEN_REFRESH_ANTICIPATION = 60;

    private String clientId = "";
    private String clientSecret = "";

    private String redirectUriHost = "";
    private String refreshToken = "";
    private int redirectUriPort;
    private String redirectUriResource = "";
    private String redirectUri = "";

    private String authorizationCode = "";
    private int playbackRefreshInterval;
    private int devicesRefreshInterval;
    private int playlistsRefreshInterval;

    private PlaybackInformationCache playbackInfo = new PlaybackInformationCache();
    private SpotifyHandlerFactory factory;
    private Map<String, Device> savedDevices = new ConcurrentHashMap<>();
    private Map<String, PlaylistSimplified> savedPlaylists = new ConcurrentHashMap<>();

    @Nullable
    private SpotifyConfiguration config;
    @Nullable
    private SpotifyApi spotifyApi;
    @Nullable
    private SpotifyAuthorizationHandler spotifyAuthorizationHandler;
    @Nullable
    private User user;
    @Nullable
    private ScheduledFuture<?> devicesInfoPollingJob;
    @Nullable
    private ScheduledFuture<?> playbackInfoPollingJob;
    @Nullable
    private ScheduledFuture<?> usersPlaylistsPollingJob;

    public SpotifyHandler(Thing thing, SpotifyHandlerFactory factory) {
        super(thing);
        this.factory = factory;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command.equals(RefreshType.REFRESH)) {
            return;
        }
        logger.debug("New command '{}' received on channel '{}'", command, channelUID);

        String channel = channelUID.getId();

        switch (channel) {
            case CHANNEL_DEVICE_VOLUME:
                if (command instanceof PercentType) {
                    int volume = ((PercentType) command).intValue();
                    setPlaybackVolume(volume);
                }
                playbackInfoPollingRunnable.run();
                break;
            case CHANNEL_DEVICE_NAME:
                if (command instanceof StringType) {
                    String deviceName = ((StringType) command).toString();
                    transferPlayback(deviceName);
                }
                break;
            case CHANNEL_PLAYER_CONTROL:
                if (command instanceof PlayPauseType) {
                    if (command.equals(PlayPauseType.PLAY)) {
                        playTrack();
                    } else if (command.equals(PlayPauseType.PAUSE)) {
                        pauseTrack();
                    }
                } else if (command instanceof NextPreviousType) {
                    if (command.equals(NextPreviousType.NEXT)) {
                        nextTrack();
                    } else if (command.equals(NextPreviousType.PREVIOUS)) {
                        previousTrack();
                    }
                }
                playbackInfoPollingRunnable.run();
                break;
            case CHANNEL_USERS_PLAYLISTS:
                if (command instanceof StringType) {
                    String playlistName = ((StringType) command).toString();
                    startPlaylist(playlistName);
                }
                playbackInfoPollingRunnable.run();
            case CHANNEL_TRACK_PROGRESS:
                if (command instanceof PercentType) {
                    int newTrackPositionPercentage = ((PercentType) command).intValue();
                    int newTrackPositionMs = newTrackPositionPercentage * (playbackInfo.getTrackDuration() / 100);
                    seekToPosition(newTrackPositionMs);

                }
                playbackInfoPollingRunnable.run();
        }
    }

    @Override
    public void channelLinked(ChannelUID channelUID) {
        logger.debug("New channel '{}' linked", channelUID);

        String channel = channelUID.getId();

        switch (channel) {
            case CHANNEL_DEVICE_VOLUME:
                Integer deviceVolume = playbackInfo.getDeviceVolume();
                if (deviceVolume != null) {
                    setChannelValue(CHANNEL_DEVICE_VOLUME, new PercentType(deviceVolume));
                }
            case CHANNEL_DEVICE_NAME:
                String deviceName = playbackInfo.getDeviceName();
                if (deviceName != null) {
                    setChannelValue(CHANNEL_DEVICE_NAME, new StringType(deviceName));
                }
            case CHANNEL_PLAYER_CONTROL:
                Boolean isPlaying = playbackInfo.isPlaying();
                if (isPlaying != null) {
                    setChannelValue(CHANNEL_PLAYER_CONTROL, isPlaying ? PlayPauseType.PLAY : PlayPauseType.PAUSE);
                }
            case CHANNEL_TRACK_ARTIST:
                String trackArtist = playbackInfo.getTrackArtist();
                if (trackArtist != null) {
                    setChannelValue(CHANNEL_TRACK_ARTIST, new StringType(trackArtist));
                }
            case CHANNEL_TRACK_TITLE:
                String trackTitle = playbackInfo.getTrackTitle();
                if (trackTitle != null) {
                    setChannelValue(CHANNEL_TRACK_TITLE, new StringType(trackTitle));
                }
            case CHANNEL_TRACK_ALBUM:
                String trackAlbum = playbackInfo.getTrackAlbum();
                if (trackAlbum != null) {
                    setChannelValue(CHANNEL_TRACK_ALBUM, new StringType(trackAlbum));
                }
            case CHANNEL_TRACK_PROGRESS:
                Integer trackDuration = playbackInfo.getTrackDuration();
                Integer trackProgressMs = playbackInfo.getTrackProgressMs();
                if (trackDuration != null && trackProgressMs != null) {
                    Integer trackProgressPercentage = (100 * trackProgressMs) / trackDuration;
                    setChannelValue(CHANNEL_TRACK_PROGRESS, new PercentType(trackProgressPercentage));
                }
        }
    }

    @Override
    public void initialize() {
        logger.debug("Initializing Spotify acoount handler");

        config = getConfigAs(SpotifyConfiguration.class);

        clientId = config.clientId;
        clientSecret = config.clientSecret;
        playbackRefreshInterval = config.playbackRefreshInterval;
        devicesRefreshInterval = config.devicesRefreshInterval;
        playlistsRefreshInterval = config.playlistsRefreshInterval;
        redirectUriHost = config.redirectUriHost;
        redirectUriPort = config.redirectUriPort;
        redirectUriResource = config.redirectUriResource;
        refreshToken = config.refreshToken;
        redirectUri = String.format("http://%s:%s/%s", redirectUriHost, redirectUriPort, redirectUriResource);

        this.spotifyApi = new SpotifyApi.Builder().setClientId(clientId).setClientSecret(clientSecret)
                .setRedirectUri(SpotifyHttpManager.makeUri(this.redirectUri)).build();

        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE, "Manual configuration started");

        if (refreshToken != null && refreshToken.isEmpty()) {
            logger.debug("Refresh token is invalid, starting authorization flow");
            this.presentAuthorizationCodeUri();

            this.startAuthorizationCodeProcess();

        } else {
            logger.debug("Refresh token is valid, getting the accessToken");
            this.setRefreshToken(refreshToken);
        }
    }

    @Override
    public void dispose() {
        if (devicesInfoPollingJob != null) {
            devicesInfoPollingJob.cancel(true);
        }

        if (playbackInfoPollingJob != null) {
            playbackInfoPollingJob.cancel(true);
        }

        if (usersPlaylistsPollingJob != null) {
            usersPlaylistsPollingJob.cancel(true);
        }

        devicesInfoPollingJob = null;
        playbackInfoPollingJob = null;
        usersPlaylistsPollingJob = null;
    }

    private void presentAuthorizationCodeUri() {
        final AuthorizationCodeUriRequest authorizationCodeUriRequest = spotifyApi.authorizationCodeUri().state(STATE)
                .scope(SCOPE).build();

        final URI uri = authorizationCodeUriRequest.execute();

        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING, "Go to " + uri.toString());
    }

    // Authorization code methods
    private void startAuthorizationCodeProcess() {
        this.spotifyAuthorizationHandler = new SpotifyAuthorizationHandler(this.redirectUriPort);
        this.spotifyAuthorizationHandler.setResourceCallback(this.redirectUriResource, this);
        this.spotifyAuthorizationHandler.setState(STATE);
        this.spotifyAuthorizationHandler.start();
    }

    @Override
    public void setAuthorizationCode(String authCode) {
        logger.debug("Authorization code received: {}", authCode);
        this.authorizationCode = authCode;
        this.spotifyAuthorizationHandler.stop();

        String refreshToken = getRefreshTokenFromCode(this.authorizationCode);
        if (!refreshToken.isEmpty()) {
            this.setRefreshToken(refreshToken);
        }
    }

    // Refresh token methods
    private String getRefreshTokenFromCode(String authorizationCode) {
        AuthorizationCodeRequest authorizationCodeRequest = spotifyApi.authorizationCode(authorizationCode).build();
        try {
            AuthorizationCodeCredentials authorizationCodeCredentials = authorizationCodeRequest.execute();

            return authorizationCodeCredentials.getRefreshToken();
        } catch (IOException | SpotifyWebApiException e) {
            logger.error("Error: " + e.getMessage());
            return "";
        }
    }

    private void setRefreshToken(String refreshToken) {
        logger.debug("Refresh token received: {}", refreshToken);
        this.refreshToken = refreshToken;
        updateThingRefreshToken(refreshToken);
        spotifyApi.setRefreshToken(refreshToken);

        String accessToken = getAccessToken();
        if (!accessToken.isEmpty()) {
            this.setAccessToken(accessToken);
        }
    }

    // Access token methods
    private String getAccessToken() {
        final AuthorizationCodeRefreshRequest authorizationCodeRefreshRequest = spotifyApi.authorizationCodeRefresh()
                .build();

        try {
            final Future<AuthorizationCodeCredentials> authorizationCodeCredentialsFuture = authorizationCodeRefreshRequest
                    .executeAsync();

            final AuthorizationCodeCredentials authorizationCodeCredentials = authorizationCodeCredentialsFuture.get();

            int expireAccessToken = authorizationCodeCredentials.getExpiresIn();
            int tokenRefreshTime = expireAccessToken - TOKEN_REFRESH_ANTICIPATION;
            logger.debug("Access token expires in {} seconds. Refresh it in {} seconds", expireAccessToken,
                    tokenRefreshTime);
            scheduler.schedule(refreshAccessTokenRunnable, tokenRefreshTime, TimeUnit.SECONDS);
            return authorizationCodeCredentials.getAccessToken();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error: " + e.getCause().getMessage());
            return "";
        }
    }

    private Runnable refreshAccessTokenRunnable = new Runnable() {
        @Override
        public void run() {
            logger.debug("Refreshing access token...");

            try {
                String accessToken = getAccessToken();
                if (accessToken != null && !accessToken.isEmpty()) {
                    setAccessToken(accessToken);
                }
            } catch (Exception e) {
                logger.error("Error in refreshAccessTokenRunnable: {}", e.getMessage());
            }
        }
    };

    private void setAccessToken(String accessToken) {
        logger.debug("New access token received: {}", accessToken);
        spotifyApi.setAccessToken(accessToken);

        if (!getThing().getStatus().equals(ThingStatus.ONLINE)) {
            updateStatus(ThingStatus.ONLINE);
        }

        scheduler.schedule(usersInfoPollingRunnable, 0, TimeUnit.SECONDS);

        if (playbackInfoPollingJob != null) {
            playbackInfoPollingJob.cancel(true);
            playbackInfoPollingJob = null;
        }
        playbackInfoPollingJob = scheduler.scheduleWithFixedDelay(playbackInfoPollingRunnable, 5,
                playbackRefreshInterval, TimeUnit.SECONDS);

        if (devicesInfoPollingJob != null) {
            devicesInfoPollingJob.cancel(true);
            devicesInfoPollingJob = null;
        }
        devicesInfoPollingJob = scheduler.scheduleWithFixedDelay(devicesInfoPollingRunnable, 5, devicesRefreshInterval,
                TimeUnit.SECONDS);

        if (usersPlaylistsPollingJob != null) {
            usersPlaylistsPollingJob.cancel(true);
            usersPlaylistsPollingJob = null;
        }
        usersPlaylistsPollingJob = scheduler.scheduleWithFixedDelay(usersPlaylistsPollingRunnable, 5,
                playlistsRefreshInterval, TimeUnit.SECONDS);
    }

    private Runnable devicesInfoPollingRunnable = new Runnable() {
        @Override
        public void run() {
            logger.debug("Getting devices information from spotify API");

            GetUsersAvailableDevicesRequest getUsersAvailableDevicesRequest = spotifyApi.getUsersAvailableDevices()
                    .build();

            try {
                Future<Device[]> devicesFuture = getUsersAvailableDevicesRequest.executeAsync();

                Device[] devices = devicesFuture.get();

                Map<String, Device> newDevices = new ConcurrentHashMap<>();
                List<StateOption> states = new LinkedList<StateOption>();

                for (Device device : devices) {
                    states.add(new StateOption(device.getName(), device.getName()));
                    newDevices.put(device.getName(), device);
                }

                if (savedDevices.keySet().equals(newDevices.keySet())) {
                    logger.debug("No new devices, keeping the channel the same");
                    return;
                }

                ChannelTypeUID channelTypeUID = new ChannelTypeUID(getThing().getUID() + ":" + CHANNEL_DEVICE_NAME);

                ChannelType channelType = new ChannelType(channelTypeUID, false, "String", "Device name",
                        "Name of the active device", null, null,
                        new StateDescription(null, null, null, "%s", false, states), null);

                factory.addChannelType(channelType);

                ThingBuilder thingBuilder = editThing();

                Channel channel = ChannelBuilder
                        .create(new ChannelUID(getThing().getUID(), CHANNEL_DEVICE_NAME), "String")
                        .withType(channelTypeUID).build();

                // replace existing currentActivity with updated one
                List<Channel> currentChannels = getThing().getChannels();
                List<Channel> newChannels = new ArrayList<Channel>();
                for (Channel c : currentChannels) {
                    if (!c.getUID().equals(channel.getUID())) {
                        newChannels.add(c);
                    }
                }
                newChannels.add(channel);
                thingBuilder.withChannels(newChannels);

                updateThing(thingBuilder.build());

                savedDevices = newDevices;
            } catch (InterruptedException e) {
                logger.error("DeviceInfoPollingRunnable 'InterruptedException' error: {}", e.getMessage());
            } catch (ExecutionException e) {
                logger.error("DeviceInfoPollingRunnable 'ExecutionException' error: {}", e.getMessage());
            }
        }
    };

    private Runnable playbackInfoPollingRunnable = new Runnable() {
        @Override
        public void run() {
            logger.debug("Getting playback information from spotify API");

            GetInformationAboutUsersCurrentPlaybackRequest getInformationAboutUsersCurrentPlaybackRequest = spotifyApi
                    .getInformationAboutUsersCurrentPlayback().build();

            CurrentlyPlayingContext currentlyPlayingContext = null;

            try {
                Future<CurrentlyPlayingContext> currentlyPlayingContextFuture = getInformationAboutUsersCurrentPlaybackRequest
                        .executeAsync();

                currentlyPlayingContext = currentlyPlayingContextFuture.get();

            } catch (ExecutionException e) {
                logger.debug("Nothing playing 'ExecutionException': {}", e);
                return;
            } catch (InterruptedException e) {
                logger.debug("Nothing playing 'InterruptedException': {}", e);
                return;
            }

            // Track title
            String trackTitle = currentlyPlayingContext.getItem().getName();
            logger.debug("Received track 'title': {}", trackTitle);
            if (!trackTitle.equals(playbackInfo.getTrackTitle())) {
                playbackInfo.setTrackTitle(trackTitle);
                setChannelValue(CHANNEL_TRACK_TITLE, new StringType(trackTitle));
            }

            // Track artist
            List<String> artistNames = new ArrayList<>();
            for (ArtistSimplified artist : currentlyPlayingContext.getItem().getArtists()) {
                artistNames.add(artist.getName());
            }
            String trackArtist = String.join(",", artistNames);
            logger.debug("Received track 'artist': {}", trackArtist);
            if (!trackArtist.equals(playbackInfo.getTrackArtist())) {
                playbackInfo.setTrackArtist(trackArtist);
                setChannelValue(CHANNEL_TRACK_ARTIST, new StringType(trackArtist));
            }

            // Track album
            String trackAlbum = currentlyPlayingContext.getItem().getAlbum().getName();
            logger.debug("Received track 'album': {}", trackAlbum);
            if (!trackAlbum.equals(playbackInfo.getTrackAlbum())) {
                playbackInfo.setTrackAlbum(trackAlbum);
                setChannelValue(CHANNEL_TRACK_ALBUM, new StringType(trackAlbum));
            }

            // Track duration
            Integer trackDuration = currentlyPlayingContext.getItem().getDurationMs();
            if (!trackDuration.equals(playbackInfo.getTrackDuration())) {
                playbackInfo.setTrackDuration(trackDuration);
            }

            // Track progress (ms)
            Integer trackProgressMs = currentlyPlayingContext.getProgress_ms();
            if (!trackProgressMs.equals(playbackInfo.getTrackProgressMs())) {
                playbackInfo.setTrackProgressMs(trackProgressMs);
                logger.debug("Track duration: {}", trackDuration);
                logger.debug("Track progress: {}", trackProgressMs);
                Integer trackProgressPercentage = (100 * trackProgressMs) / trackDuration;
                setChannelValue(CHANNEL_TRACK_PROGRESS, new PercentType(trackProgressPercentage));
            }

            // Device name
            String deviceName = currentlyPlayingContext.getDevice().getName();
            logger.debug("Received device 'name': {}", deviceName);
            if (!deviceName.equals(playbackInfo.getDeviceName())) {
                playbackInfo.setDeviceName(deviceName);
                setChannelValue(CHANNEL_DEVICE_NAME, new StringType(deviceName));
            }

            // Device volume
            Integer deviceVolume = currentlyPlayingContext.getDevice().getVolume_percent();
            logger.debug("Received device 'volume': {}", deviceVolume);
            if (!deviceVolume.equals(playbackInfo.getDeviceVolume())) {
                playbackInfo.setDeviceVolume(deviceVolume);
                setChannelValue(CHANNEL_DEVICE_VOLUME, new PercentType(deviceVolume));
            }

            // is Playing
            Boolean isPlaying = currentlyPlayingContext.getIs_playing();
            logger.debug("Received 'isPlaying': {}", isPlaying);
            if (!isPlaying.equals(playbackInfo.isPlaying())) {
                playbackInfo.setIsPlaying(isPlaying);
                setChannelValue(CHANNEL_PLAYER_CONTROL, isPlaying ? PlayPauseType.PLAY : PlayPauseType.PAUSE);
            }

        }
    };

    private Runnable usersPlaylistsPollingRunnable = new Runnable() {
        @Override
        public void run() {
            logger.debug("Getting user's playlist from spotify API");

            GetListOfCurrentUsersPlaylistsRequest getListOfCurrentUsersPlaylistsRequest = spotifyApi
                    .getListOfCurrentUsersPlaylists().limit(50).offset(0).build();

            Future<Paging<PlaylistSimplified>> pagingFuture = getListOfCurrentUsersPlaylistsRequest.executeAsync();

            try {
                Paging<PlaylistSimplified> playlistSimplifiedPaging = pagingFuture.get();
                PlaylistSimplified[] playlistSimplified = playlistSimplifiedPaging.getItems();

                Map<String, PlaylistSimplified> newPlaylists = new ConcurrentHashMap<>();
                List<StateOption> states = new LinkedList<StateOption>();

                for (PlaylistSimplified playlist : playlistSimplified) {
                    states.add(new StateOption(playlist.getName(), playlist.getName()));
                    newPlaylists.put(playlist.getName(), playlist);
                }

                if (savedPlaylists.keySet().equals(newPlaylists.keySet())) {
                    logger.debug("No new playlists, keeping the channel the same");
                    return;
                }

                ChannelTypeUID channelTypeUID = new ChannelTypeUID(getThing().getUID() + ":" + CHANNEL_USERS_PLAYLISTS);

                ChannelType channelType = new ChannelType(channelTypeUID, false, "String", "User's playlists",
                        "User's list of playlist", null, null,
                        new StateDescription(null, null, null, "%s", false, states), null);

                factory.addChannelType(channelType);

                ThingBuilder thingBuilder = editThing();

                Channel channel = ChannelBuilder
                        .create(new ChannelUID(getThing().getUID(), CHANNEL_USERS_PLAYLISTS), "String")
                        .withType(channelTypeUID).build();

                // replace existing currentActivity with updated one
                List<Channel> currentChannels = getThing().getChannels();
                List<Channel> newChannels = new ArrayList<Channel>();
                for (Channel c : currentChannels) {
                    if (!c.getUID().equals(channel.getUID())) {
                        newChannels.add(c);
                    }
                }
                newChannels.add(channel);
                thingBuilder.withChannels(newChannels);

                updateThing(thingBuilder.build());

                savedPlaylists = newPlaylists;

            } catch (InterruptedException e) {
                logger.debug("usersPlaylistPollingRunnable error 'InterruptedException': {}", e.getMessage());
            } catch (ExecutionException e) {
                logger.debug("usersPlaylistPollingRunnable error 'ExecutionException': {}", e.getMessage());
            }

        }
    };

    private Runnable usersInfoPollingRunnable = new Runnable() {
        @Override
        public void run() {
            GetCurrentUsersProfileRequest getCurrentUsersProfileRequest = spotifyApi.getCurrentUsersProfile().build();

            Future<User> userFuture = getCurrentUsersProfileRequest.executeAsync();

            try {
                user = userFuture.get();
                logger.debug("User name: {}", user.getDisplayName());
            } catch (InterruptedException e) {
                logger.error("Error getting user information 'InterruptedException': {}", e.getMessage());
            } catch (ExecutionException e) {
                logger.error("Error getting user information:'InterruptedException' {}", e.getMessage());
            }
        }
    };

    private void setChannelValue(String channelName, State state) {
        if (getThing().getStatus().equals(ThingStatus.ONLINE)) {

            if (isLinked(channelName)) {
                Channel channel = getThing().getChannel(channelName);

                logger.trace("Updating status of spotify device {} channel {}.", getThing().getLabel(),
                        channel.getUID());
                updateState(channel.getUID(), state);
            }
        }
    }

    private void updateThingRefreshToken(String refreshToken) {
        Configuration configuration = editConfiguration();
        configuration.put(REFRESH_TOKEN_PARAMETER, refreshToken);
        updateConfiguration(configuration);
    }

    // Act upon the device

    private void transferPlayback(String newDeviceName) {
        String deviceId = savedDevices.get(newDeviceName).getId();
        JsonArray deviceIds = new JsonParser().parse(String.format("[\'%s\']", deviceId)).getAsJsonArray();
        try {
            spotifyApi.transferUsersPlayback(deviceIds).play(true).build().execute();
        } catch (SpotifyWebApiException e) {
            logger.error("Error transfering playback: {}", e.getMessage());
        } catch (IOException e) {
            logger.error("Error transfering playback: {}", e.getMessage());
        }
    }

    private void setPlaybackVolume(int volume) {
        try {
            String deviceName = playbackInfo.getDeviceName();
            String deviceId = savedDevices.get(deviceName).getId();
            spotifyApi.setVolumeForUsersPlayback(volume).device_id(deviceId).build().execute();
        } catch (SpotifyWebApiException e) {
            logger.error("Error setting plackback volume: {}", e.getMessage());
        } catch (IOException e) {
            logger.error("Error setting plackback volume: {}", e.getMessage());
        }
    }

    private void nextTrack() {
        try {
            String deviceName = playbackInfo.getDeviceName();
            String deviceId = savedDevices.get(deviceName).getId();
            spotifyApi.skipUsersPlaybackToNextTrack().device_id(deviceId).build().execute();
        } catch (SpotifyWebApiException e) {
            logger.error("Error playing next track: {}", e.getMessage());
        } catch (IOException e) {
            logger.error("Error playing next track: {}", e.getMessage());
        }
    }

    private void previousTrack() {
        try {
            String deviceName = playbackInfo.getDeviceName();
            String deviceId = savedDevices.get(deviceName).getId();
            spotifyApi.skipUsersPlaybackToPreviousTrack().device_id(deviceId).build().execute();
        } catch (SpotifyWebApiException e) {
            logger.error("Error playing previous track: {}", e.getMessage());
        } catch (IOException e) {
            logger.error("Error playing previous track: {}", e.getMessage());
        }
    }

    private void playTrack() {
        try {
            String deviceName = playbackInfo.getDeviceName();
            String deviceId = savedDevices.get(deviceName).getId();
            spotifyApi.startResumeUsersPlayback().device_id(deviceId).build().execute();
        } catch (SpotifyWebApiException e) {
            logger.error("Error playing track: {}", e.getMessage());
        } catch (IOException e) {
            logger.error("Error playing track: {}", e.getMessage());
        }
    }

    private void pauseTrack() {
        try {
            String deviceName = playbackInfo.getDeviceName();
            String deviceId = savedDevices.get(deviceName).getId();
            spotifyApi.pauseUsersPlayback().device_id(deviceId).build().execute();
        } catch (SpotifyWebApiException e) {
            logger.error("Error playing track: {}", e.getMessage());
        } catch (IOException e) {
            logger.error("Error playing track: {}", e.getMessage());
        }
    }

    private void seekToPosition(int newPositionMs) {
        try {
            String deviceName = playbackInfo.getDeviceName();
            String deviceId = savedDevices.get(deviceName).getId();

            SeekToPositionInCurrentlyPlayingTrackRequest seekToPositionInCurrentlyPlayingTrackRequest = spotifyApi
                    .seekToPositionInCurrentlyPlayingTrack(newPositionMs).device_id(deviceId).build();

            Future<String> stringFuture = seekToPositionInCurrentlyPlayingTrackRequest.executeAsync();
            stringFuture.get();
        } catch (InterruptedException e) {
            logger.error("Erro seeking to new position: {}", e.getMessage());
        } catch (ExecutionException e) {
            logger.error("Error seeking to new position: {}", e.getMessage());
        }
    }

    private void startPlaylist(String playlistName) {
        try {
            String deviceName = playbackInfo.getDeviceName();
            String deviceId = savedDevices.get(deviceName).getId();
            String playlistId = savedPlaylists.get(playlistName).getId();
            String userId = user.getId();
            String contextUri = String.format("spotify:user:%s:playlist:%s", userId, playlistId);
            logger.debug("Starting playlist '{}' ('{}') on device '{}' ('{}')", playlistName, contextUri, deviceName,
                    deviceId);
            spotifyApi.startResumeUsersPlayback().context_uri(contextUri).device_id(deviceId).build().execute();
        } catch (SpotifyWebApiException e) {
            logger.error("Error starting playlist: {}", e.getMessage());
        } catch (IOException e) {
            logger.error("Error starting playlist: {}", e.getMessage());
        }

    }
}
