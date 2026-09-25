package com.motadev.clone_reddit.community.service;

import com.motadev.clone_reddit.community.dtos.request.CreateCommunityRequestDTO;

public interface CommunityServiceI {
    void create(CreateCommunityRequestDTO createCommunityRequestDTO);
}
