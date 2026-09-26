package com.motadev.clone_reddit.community.service;

import com.motadev.clone_reddit.community.dtos.request.CreateCommunityRequestDTO;
import com.motadev.clone_reddit.community.dtos.response.CommunityResponseDTO;

public interface CommunityServiceI {
    CommunityResponseDTO create(CreateCommunityRequestDTO createCommunityRequestDTO);
}
