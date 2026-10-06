package com.motadev.clone_reddit.community.controller;

import com.motadev.clone_reddit.community.dtos.request.CreateCommunityRequestDTO;
import com.motadev.clone_reddit.community.dtos.response.CommunityResponseDTO;
import com.motadev.clone_reddit.community.service.CommunityServiceI;
import com.motadev.clone_reddit.shared.dtos.response.PagedResponseDTO;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/communities")
public class CommunityController {

    private final CommunityServiceI communityServiceI;

    public CommunityController(CommunityServiceI communityServiceI) {
        this.communityServiceI = communityServiceI;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<CommunityResponseDTO> create(
            @RequestPart("data") @Valid CreateCommunityRequestDTO dto,
            @RequestPart(value = "icon", required = false) MultipartFile iconFile,
            @RequestPart(value = "banner", required = false) MultipartFile bannerFile
    ) {
        var response = communityServiceI.create(dto, iconFile, bannerFile);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PagedResponseDTO<CommunityResponseDTO>> list(
            @PageableDefault Pageable pageable
    ) {
        return ResponseEntity.ok(communityServiceI.list(pageable));
    }

    @GetMapping("/{communityId}")
    public ResponseEntity<CommunityResponseDTO> getById(@PathVariable UUID communityId) {
        return ResponseEntity.ok(communityServiceI.getById(communityId));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<CommunityResponseDTO> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(communityServiceI.getBySlug(slug));
    }

    @PutMapping(value = "/{communityId}/icon", consumes = "multipart/form-data")
    public ResponseEntity<CommunityResponseDTO> replaceIcon(
            @PathVariable UUID communityId,
            @RequestPart("file") MultipartFile iconFile
    ) {
        return ResponseEntity.ok(communityServiceI.replaceIcon(communityId, iconFile));
    }

    @PutMapping(value = "/{communityId}/banner", consumes = "multipart/form-data")
    public ResponseEntity<CommunityResponseDTO> replaceBanner(
            @PathVariable UUID communityId,
            @RequestPart("file") MultipartFile bannerFile
    ) {
        return ResponseEntity.ok(communityServiceI.replaceBanner(communityId, bannerFile));
    }

    @DeleteMapping("/{communityId}/icon")
    public ResponseEntity<Void> removeIcon(@PathVariable UUID communityId) {
        communityServiceI.removeIcon(communityId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{communityId}/banner")
    public ResponseEntity<Void> removeBanner(@PathVariable UUID communityId) {
        communityServiceI.removeBanner(communityId);
        return ResponseEntity.noContent().build();
    }
}
