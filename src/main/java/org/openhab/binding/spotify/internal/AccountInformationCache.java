/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.spotify.internal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.wrapper.spotify.model_objects.miscellaneous.Device;
import com.wrapper.spotify.model_objects.specification.PlaylistSimplified;
import com.wrapper.spotify.model_objects.specification.User;

public class AccountInformationCache {

    private Map<String, Device> availableDevices;
    private Map<String, PlaylistSimplified> savedPlaylists;
    private User user;

    public AccountInformationCache() {
        this.availableDevices = new ConcurrentHashMap<>();
        this.savedPlaylists = new ConcurrentHashMap<>();
    }

    public Map<String, Device> getAvailableDevices() {
        return this.availableDevices;
    }

    public void setAvailableDevices(Map<String, Device> devices) {
        this.availableDevices = devices;
    }

    public Map<String, PlaylistSimplified> getSavedPlaylists() {
        return this.savedPlaylists;
    }

    public void setSavedPlaylists(Map<String, PlaylistSimplified> playlists) {
        this.savedPlaylists = playlists;
    }

    public User getUser() {
        return this.user;
    }

    public void setUser(User user) {
        this.user = user;
    }

}
