/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.spotify.internal;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.requests.data.player.SeekToPositionInCurrentlyPlayingTrackRequest;

public class PlaybackControl {

    private final Logger logger = LoggerFactory.getLogger(PlaybackControl.class);

    private SpotifyApi spotifyApi;
    private PlaybackInformationCache playbackInfo;
    private AccountInformationCache accountInfo;

    public PlaybackControl(SpotifyApi api, PlaybackInformationCache playbackCache,
            AccountInformationCache accountCache) {
        this.spotifyApi = api;
        this.playbackInfo = playbackCache;
        this.accountInfo = accountCache;
    }

    public void transferPlayback(String newDeviceName) {
        if (!accountInfo.getAvailableDevices().containsKey(newDeviceName)) {
            logger.error("Device '{}' is not available", newDeviceName);
        }
        String deviceId = accountInfo.getAvailableDevices().get(newDeviceName).getId();
        JsonArray deviceIds = new JsonParser().parse(String.format("[\'%s\']", deviceId)).getAsJsonArray();
        try {
            spotifyApi.transferUsersPlayback(deviceIds).play(true).build().execute();
        } catch (SpotifyWebApiException e) {
            logger.error("Error transfering playback: {}", e.getMessage());
        } catch (IOException e) {
            logger.error("Error transfering playback: {}", e.getMessage());
        }
    }

    public void setPlaybackVolume(int volume) {
        try {
            String deviceName = playbackInfo.getDeviceName();
            String deviceId = accountInfo.getAvailableDevices().get(deviceName).getId();
            spotifyApi.setVolumeForUsersPlayback(volume).device_id(deviceId).build().execute();
        } catch (SpotifyWebApiException e) {
            logger.error("Error setting plackback volume: {}", e.getMessage());
        } catch (IOException e) {
            logger.error("Error setting plackback volume: {}", e.getMessage());
        }
    }

    public void nextTrack() {
        try {
            String deviceName = playbackInfo.getDeviceName();
            String deviceId = accountInfo.getAvailableDevices().get(deviceName).getId();
            spotifyApi.skipUsersPlaybackToNextTrack().device_id(deviceId).build().execute();
        } catch (SpotifyWebApiException e) {
            logger.error("Error playing next track: {}", e.getMessage());
        } catch (IOException e) {
            logger.error("Error playing next track: {}", e.getMessage());
        }
    }

    public void previousTrack() {
        try {
            String deviceName = playbackInfo.getDeviceName();
            String deviceId = accountInfo.getAvailableDevices().get(deviceName).getId();
            spotifyApi.skipUsersPlaybackToPreviousTrack().device_id(deviceId).build().execute();
        } catch (SpotifyWebApiException e) {
            logger.error("Error playing previous track: {}", e.getMessage());
        } catch (IOException e) {
            logger.error("Error playing previous track: {}", e.getMessage());
        }
    }

    public void playTrack() {
        try {
            String deviceName = playbackInfo.getDeviceName();
            String deviceId = accountInfo.getAvailableDevices().get(deviceName).getId();
            spotifyApi.startResumeUsersPlayback().device_id(deviceId).build().execute();
        } catch (SpotifyWebApiException e) {
            logger.error("Error playing track: {}", e.getMessage());
        } catch (IOException e) {
            logger.error("Error playing track: {}", e.getMessage());
        }
    }

    public void pauseTrack() {
        try {
            String deviceName = playbackInfo.getDeviceName();
            String deviceId = accountInfo.getAvailableDevices().get(deviceName).getId();
            spotifyApi.pauseUsersPlayback().device_id(deviceId).build().execute();
        } catch (SpotifyWebApiException e) {
            logger.error("Error playing track: {}", e.getMessage());
        } catch (IOException e) {
            logger.error("Error playing track: {}", e.getMessage());
        }
    }

    public void seekToPosition(int newPositionMs) {
        try {
            String deviceName = playbackInfo.getDeviceName();
            String deviceId = accountInfo.getAvailableDevices().get(deviceName).getId();

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

    public void startPlaylist(String playlistName) {
        try {
            String deviceName = playbackInfo.getDeviceName();
            String deviceId = accountInfo.getAvailableDevices().get(deviceName).getId();
            String playlistId = accountInfo.getSavedPlaylists().get(playlistName).getId();
            String userId = accountInfo.getUser().getId();
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
