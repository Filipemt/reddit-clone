package com.motadev.clone_reddit.post.controller;

import com.motadev.clone_reddit.post.dtos.request.CreatePostRequestDTO;
import com.motadev.clone_reddit.post.dtos.response.PostResponseDTO;
import com.motadev.clone_reddit.post.service.PostServiceI;
import com.motadev.clone_reddit.shared.dtos.response.PagedResponseDTO;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping
public class PostController {

    private final PostServiceI postServiceI;

    public PostController(PostServiceI postServiceI) {
        this.postServiceI = postServiceI;
    }

    @PostMapping(value = "/communities/{communityId}/posts", consumes = "multipart/form-data")
    public ResponseEntity<PostResponseDTO> create(
            @PathVariable UUID communityId,
            @RequestPart("data") @Valid CreatePostRequestDTO dto,
            @RequestPart(value = "media", required = false) MultipartFile mediaFile
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postServiceI.create(communityId, dto, mediaFile));
    }

    @GetMapping("/communities/{communityId}/posts")
    public ResponseEntity<PagedResponseDTO<PostResponseDTO>> listByCommunity(
            @PathVariable UUID communityId,
            @PageableDefault Pageable pageable,
            @RequestParam(defaultValue = "new") String sort,
            @RequestParam(defaultValue = "all") String period
    ) {
        return ResponseEntity.ok(postServiceI.listByCommunity(communityId, pageable, sort, period));
    }

    @GetMapping("/posts/{postId}")
    public ResponseEntity<PostResponseDTO> getById(@PathVariable UUID postId) {
        return ResponseEntity.ok(postServiceI.getById(postId));
    }

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<Void> delete(@PathVariable UUID postId) {
        postServiceI.delete(postId);
        return ResponseEntity.noContent().build();
    }
}
