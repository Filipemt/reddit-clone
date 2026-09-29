package com.motadev.clone_reddit.community.controller;

import com.motadev.clone_reddit.community.dtos.request.CreateCommunityRequestDTO;
import com.motadev.clone_reddit.community.dtos.response.CommunityResponseDTO;
import com.motadev.clone_reddit.community.service.CommunityServiceI;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
}
