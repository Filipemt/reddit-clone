package com.motadev.clone_reddit.search.service;

import com.motadev.clone_reddit.search.dtos.response.SearchResponseDTO;
import org.springframework.data.domain.Pageable;

public interface SearchServiceI {

    SearchResponseDTO search(String query, Pageable pageable);
}
