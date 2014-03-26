/*
 * Copyright 2013 Stormpath, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.stormpath.sdk.authc;

import com.stormpath.sdk.directory.AccountStore;

/**
 * @since 0.2
 */
public class UsernamePasswordRequest implements AuthenticationRequest<String, char[]> {

    private String username;
    private char[] password;
    private String host;
    private AccountStore accountStore;

    public UsernamePasswordRequest(String username, String password) {
        this(username, password, null, null);
    }

    public UsernamePasswordRequest(String username, char[] password) {
        this(username, password, null, null);
    }

    public UsernamePasswordRequest(String username, String password, String host) {
        this(username, password, host, null);
    }

    public UsernamePasswordRequest(String username, char[] password, String host) {
        this(username, password, host, null);
    }

    public UsernamePasswordRequest(String username, String password, AccountStore accountStore) {
        this(username, password, null, accountStore);
    }

    public UsernamePasswordRequest(String username, char[] password, AccountStore accountStore) {
        this(username, password, null, accountStore);
    }

    public UsernamePasswordRequest(String username, String password, String host, AccountStore accountStore) {
        this(username, password != null ? password.toCharArray() : "".toCharArray(), host, accountStore);
    }

    public UsernamePasswordRequest(String username, char[] password, String host, AccountStore accountStore) {
        this.username = username;
        this.password = password;
        this.host = host;
        this.accountStore = accountStore;
    }

    @Override
    public String getPrincipals() {
        return username;
    }

    @Override
    public char[] getCredentials() {
        return password;
    }

    @Override
    public String getHost() {
        return this.host;
    }

    /**
     * Returns the specific account store this authentication request will be targeted to.
     * @return the specific account store this authentication request will be targeted to.
     * @since 0.9.4
     */
    @Override
    public AccountStore getAccountStore() {
        return this.accountStore;
    }

    /**
     * Clears out (nulls) the username, password, and host.  The password bytes are explicitly set to
     * <tt>0x00</tt> to eliminate the possibility of memory access at a later time.
     */
    @Override
    public void clear() {
        this.username = null;
        this.host = null;
        this.accountStore = null;

        char[] password = this.password;
        this.password = null;

        if (password != null) {
            for (int i = 0; i < password.length; i++) {
                password[i] = 0x00;
            }
        }

    }

}
