package com.motadev.clone_reddit.post.service;

import com.motadev.clone_reddit.post.dtos.request.CreatePostRequestDTO;
import com.motadev.clone_reddit.post.dtos.response.PostResponseDTO;
import com.motadev.clone_reddit.shared.dtos.response.PagedResponseDTO;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface PostServiceI {

    PostResponseDTO create(UUID communityId, CreatePostRequestDTO dto, MultipartFile mediaFile);

    PostResponseDTO getById(UUID postId);

    PagedResponseDTO<PostResponseDTO> listByCommunity(UUID communityId, Pageable pageable);

    void delete(UUID postId);

    UUID requireActiveAuthorId(UUID postId);

    void adjustCommentCount(UUID postId, long delta);

    UUID applyVoteDelta(UUID postId, long scoreDelta, long upDelta, long downDelta);
}
