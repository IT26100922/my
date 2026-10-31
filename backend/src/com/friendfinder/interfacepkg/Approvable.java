package com.friendfinder.interfacepkg;

public interface Approvable {
    void approve(long moderatorId);
    void reject(long moderatorId);
}
