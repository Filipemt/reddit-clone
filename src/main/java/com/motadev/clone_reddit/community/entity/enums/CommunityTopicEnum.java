package com.motadev.clone_reddit.community.entity.enums;

import com.motadev.clone_reddit.shared.exception.ResourceNotFoundException;

public enum CommunityTopicEnum {

    GAMES(1L, "Games"),
    TECHNOLOGY(2L, "Technology"),
    SPORT(3L, "Sport"),
    MUSIC(4L, "Music"),
    SCIENCE(5L, "Science"),
    MOVIES(6L, "Movies"),
    SERIES(7L, "Series"),
    BOOKS(8L, "Books"),
    ANIME(9L, "Anime"),
    ART(10L, "Art"),
    PHOTOGRAPHY(11L, "Photography"),
    FOOD_COOKING(12L, "Food & Cooking"),
    TRAVEL(13L, "Travel"),
    FITNESS(14L, "Fitness"),
    PROGRAMMING(15L, "Programming"),
    ECONOMY(16L, "Economy"),
    HISTORY(17L, "History"),
    NATURE(18L, "Nature"),
    DESIGN(19L, "Design"),
    HEALTH(20L, "Health");

    private final Long id;
    private final String name;

    CommunityTopicEnum(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public static CommunityTopicEnum fromId(Long id) {
        for (CommunityTopicEnum value : values()) {
            if (value.id.equals(id)) {
                return value;
            }
        }
        throw new ResourceNotFoundException("No CommunityTopicEnum with id " + id);
    }

    public static CommunityTopicEnum fromName(String name) {
        for (CommunityTopicEnum value : values()) {
            if (value.name.equals(name)) {
                return value;
            }
        }
        throw new ResourceNotFoundException("No CommunityTopicEnum with name " + name);
    }
}