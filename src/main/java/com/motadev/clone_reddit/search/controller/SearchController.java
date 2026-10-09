package com.motadev.clone_reddit.search.controller;

import com.motadev.clone_reddit.search.dtos.response.SearchResponseDTO;
import com.motadev.clone_reddit.search.service.SearchServiceI;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/search")
public class SearchController {

    private final SearchServiceI searchServiceI;

    public SearchController(SearchServiceI searchServiceI) {
        this.searchServiceI = searchServiceI;
    }

    @GetMapping
    public ResponseEntity<SearchResponseDTO> search(
            @RequestParam("q") String query,
            @PageableDefault Pageable pageable
    ) {
        return ResponseEntity.ok(searchServiceI.search(query, pageable));
    }
}
