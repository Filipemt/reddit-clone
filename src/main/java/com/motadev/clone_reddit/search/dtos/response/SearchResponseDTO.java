package com.motadev.clone_reddit.search.dtos.response;

import com.motadev.clone_reddit.community.dtos.response.CommunityResponseDTO;
import com.motadev.clone_reddit.post.dtos.response.PostResponseDTO;
import com.motadev.clone_reddit.shared.dtos.response.PagedResponseDTO;
import com.motadev.clone_reddit.user.dtos.response.UserResponseDTO;

public record SearchResponseDTO(
        PagedResponseDTO<PostResponseDTO> posts,
        PagedResponseDTO<CommunityResponseDTO> communities,
        PagedResponseDTO<UserResponseDTO> users
) {
}
