package app.morphe.extension.youtube.shared;
public enum ShortsPlayerState { CLOSED, OPEN; public static ShortsPlayerState cur=CLOSED; public static ShortsPlayerState getCurrent(){ return cur; } public boolean isOpen(){ return this==OPEN; } }
