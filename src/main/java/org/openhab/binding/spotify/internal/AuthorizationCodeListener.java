package org.openhab.binding.spotify.internal;

import org.eclipse.jdt.annotation.NonNull;

public interface AuthorizationCodeListener {

    void setAuthorizationCode(@NonNull String code);

}
