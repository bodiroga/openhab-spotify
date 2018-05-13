package org.openhab.binding.spotify.internal;

public class PlaybackInformationCache {

    private String trackTitle;
    private String trackArtist;
    private String trackAlbum;
    private Integer trackProgressMs;
    private Integer trackDuration;
    private String deviceName;
    private Integer deviceVolume;
    private Boolean isPlaying;

    public PlaybackInformationCache() {
        this.trackTitle = "";
        this.trackArtist = "";
        this.trackAlbum = "";
        this.deviceName = "";
    }

    public String getTrackTitle() {
        return this.trackTitle;
    }

    public String getTrackArtist() {
        return this.trackArtist;
    }

    public String getTrackAlbum() {
        return this.trackAlbum;
    }

    public Integer getTrackProgressMs() {
        return this.trackProgressMs;
    }

    public Integer getTrackDuration() {
        return this.trackDuration;
    }

    public String getDeviceName() {
        return this.deviceName;
    }

    public Integer getDeviceVolume() {
        return this.deviceVolume;
    }

    public Boolean isPlaying() {
        return this.isPlaying;
    }

    public void setTrackTitle(String trackTitle) {
        this.trackTitle = trackTitle;
    }

    public void setTrackArtist(String trackArtist) {
        this.trackArtist = trackArtist;
    }

    public void setTrackAlbum(String trackAlbum) {
        this.trackAlbum = trackAlbum;
    }

    public void setTrackProgressMs(Integer trackProgressMs) {
        this.trackProgressMs = trackProgressMs;
    }

    public void setTrackDuration(Integer trackDuration) {
        this.trackDuration = trackDuration;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public void setDeviceVolume(Integer deviceVolume) {
        this.deviceVolume = deviceVolume;
    }

    public void setIsPlaying(Boolean isPlaying) {
        this.isPlaying = isPlaying;
    }
}
