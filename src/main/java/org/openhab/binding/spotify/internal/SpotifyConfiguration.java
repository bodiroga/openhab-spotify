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
package org.openhab.binding.spotify.internal;

/**
 * The {@link SpotifyConfiguration} class contains fields mapping thing configuration paramters.
 *
 * @author Aitor Iturrioz - Initial contribution
 */
public class SpotifyConfiguration {

    /** Client ID from the Spotify account application. */
    public String clientId;

    /** Client secret from the Spotify account application. */
    public String clientSecret;

    /** Redirect URI host from the Spotify account application. */
    public String redirectUriHost;

    /** Redirect URI port from the Spotify account application. */
    public int redirectUriPort;

    /** Redirect URI resource from the Spotify account application. */
    public String redirectUriResource;

    /** Refresh interval for playback information. */
    public int playbackRefreshInterval;

    /** Refresh interval for devices information. */
    public int devicesRefreshInterval;

    /** Refresh interval for playlists information. */
    public int playlistsRefreshInterval;

    /** Refresh token from the Spotify API connection. */
    public String refreshToken;
}
