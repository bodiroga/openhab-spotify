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
package org.openhab.binding.spotify;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.thing.ThingTypeUID;

/**
 * The {@link SpotifyBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Aitor Iturrioz - Initial contribution
 */
@NonNullByDefault
public class SpotifyBindingConstants {

    private static final String BINDING_ID = "spotify";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_SPOTIFY = new ThingTypeUID(BINDING_ID, "spotify");

    // List of all Thing parameters
    public static final String CLIENT_ID_PARAMETER = "clientId";
    public static final String CLIENT_SECRET_PARAMETER = "clientSecret";
    public static final String REDIRECT_URI_HOST_PARAMETER = "redirectUriHost";
    public static final String REDIRECT_URI_PORT_PARAMETER = "redirectUriPort";
    public static final String REDIRECT_URI_RESOURCE_PARAMETER = "redirectUriResource";
    public static final String PLAYBACK_REFRESH_INTERVAL_PARAMETER = "playbackRefreshInterval";
    public static final String DEVICES_REFRESH_INTERVAL_PARAMETER = "devicesRefreshInterval";
    public static final String PLAYLISTS_REFRESH_INTERVAL_PARAMETER = "playlistsRefreshInterval";
    public static final String REFRESH_TOKEN_PARAMETER = "refresToken";

    // List of all Channel ids
    public static final String CHANNEL_PLAYER_CONTROL = "playerControl";
    public static final String CHANNEL_DEVICE_NAME = "deviceName";
    public static final String CHANNEL_DEVICE_VOLUME = "deviceVolume";
    public static final String CHANNEL_TRACK_ARTIST = "trackArtist";
    public static final String CHANNEL_TRACK_TITLE = "trackTitle";
    public static final String CHANNEL_TRACK_ALBUM = "trackAlbum";
    public static final String CHANNEL_TRACK_DURATION = "trackDuration";
    public static final String CHANNEL_TRACK_PROGRESS = "trackProgress";
    public static final String CHANNEL_USERS_PLAYLISTS = "usersPlaylists";

}
