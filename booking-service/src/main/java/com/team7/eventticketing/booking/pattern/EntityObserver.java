package com.team7.eventticketing.booking.pattern;

public interface EntityObserver {
	void onEvent(String action, Object payload);
}
