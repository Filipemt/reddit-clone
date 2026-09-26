package com.motadev.clone_reddit.community.controller;

import com.motadev.clone_reddit.community.dtos.request.CreateCommunityRequestDTO;
import com.motadev.clone_reddit.community.dtos.response.CommunityResponseDTO;
import com.motadev.clone_reddit.community.service.CommunityServiceI;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/communities")
public class CommunityController {

    private final CommunityServiceI communityServiceI;

    public CommunityController(CommunityServiceI communityServiceI) {
        this.communityServiceI = communityServiceI;
    }

    @PostMapping
    public ResponseEntity<CommunityResponseDTO> create(@RequestBody @Valid CreateCommunityRequestDTO requestDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(communityServiceI.create(requestDTO));
    }
}
