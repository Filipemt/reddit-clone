package com.motadev.clone_reddit.community.controller;

import com.motadev.clone_reddit.community.dtos.request.CreateCommunityRequestDTO;
import com.motadev.clone_reddit.community.dtos.response.CommunityResponseDTO;
import com.motadev.clone_reddit.community.service.CommunityMembershipServiceI;
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
    private final CommunityMembershipServiceI communityMembershipServiceI;

    public CommunityController(CommunityServiceI communityServiceI,
                               CommunityMembershipServiceI communityMembershipServiceI) {
        this.communityServiceI = communityServiceI;
        this.communityMembershipServiceI = communityMembershipServiceI;
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

    @GetMapping("/me")
    public ResponseEntity<PagedResponseDTO<CommunityResponseDTO>> listJoined(
            @PageableDefault Pageable pageable
    ) {
        return ResponseEntity.ok(communityServiceI.listJoined(pageable));
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

    @DeleteMapping("/{communityId}")
    public ResponseEntity<Void> delete(@PathVariable UUID communityId) {
        communityServiceI.delete(communityId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{communityId}/membership")
    public ResponseEntity<Void> join(@PathVariable UUID communityId) {
        communityMembershipServiceI.join(communityId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{communityId}/membership")
    public ResponseEntity<Void> leave(@PathVariable UUID communityId) {
        communityMembershipServiceI.leave(communityId);
        return ResponseEntity.noContent().build();
    }
}
