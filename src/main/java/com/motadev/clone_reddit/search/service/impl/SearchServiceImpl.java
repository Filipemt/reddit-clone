package com.motadev.clone_reddit.search.service.impl;

import com.motadev.clone_reddit.community.converter.CommunityConverter;
import com.motadev.clone_reddit.community.entity.Community;
import com.motadev.clone_reddit.community.repository.CommunityRepository;
import com.motadev.clone_reddit.community.service.CommunityMembershipServiceI;
import com.motadev.clone_reddit.media.service.MediaServiceI;
import com.motadev.clone_reddit.post.converter.PostConverter;
import com.motadev.clone_reddit.post.entity.Post;
import com.motadev.clone_reddit.post.repository.PostRepository;
import com.motadev.clone_reddit.search.dtos.response.SearchResponseDTO;
import com.motadev.clone_reddit.search.service.SearchServiceI;
import com.motadev.clone_reddit.shared.dtos.response.PagedResponseDTO;
import com.motadev.clone_reddit.shared.exception.ResourceInvalidException;
import com.motadev.clone_reddit.shared.security.AuthenticatedUserProvider;
import com.motadev.clone_reddit.user.convert.UserConvert;
import com.motadev.clone_reddit.user.entity.User;
import com.motadev.clone_reddit.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class SearchServiceImpl implements SearchServiceI {

    private static final int FALLBACK_PAGE_SIZE = 20;

    private final PostRepository postRepository;
    private final CommunityRepository communityRepository;
    private final UserRepository userRepository;
    private final PostConverter postConverter;
    private final CommunityConverter communityConverter;
    private final UserConvert userConvert;
    private final MediaServiceI mediaServiceI;
    private final CommunityMembershipServiceI communityMembershipServiceI;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    public SearchServiceImpl(
            PostRepository postRepository,
            CommunityRepository communityRepository,
            UserRepository userRepository,
            PostConverter postConverter,
            CommunityConverter communityConverter,
            UserConvert userConvert,
            MediaServiceI mediaServiceI,
            CommunityMembershipServiceI communityMembershipServiceI,
            AuthenticatedUserProvider authenticatedUserProvider
    ) {
        this.postRepository = postRepository;
        this.communityRepository = communityRepository;
        this.userRepository = userRepository;
        this.postConverter = postConverter;
        this.communityConverter = communityConverter;
        this.userConvert = userConvert;
        this.mediaServiceI = mediaServiceI;
        this.communityMembershipServiceI = communityMembershipServiceI;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    @Override
    @Transactional
    public SearchResponseDTO search(String query, Pageable pageable) {
        String q = query == null ? "" : query.trim();
        if (q.isBlank()) {
            throw new ResourceInvalidException("Search query must not be blank.");
        }

        Pageable page = withoutSort(pageable);
        UUID userId = authenticatedUserProvider.extractUserIdFromAuthentication();

        Page<Post> posts = postRepository.searchActiveIlike(q, page);
        Page<Community> communities = communityRepository.searchActiveIlike(q, page);
        Page<User> users = userRepository.searchByUsernameIlike(q, page);

        var postUrls = mediaServiceI.getUrls(
                posts.getContent().stream().map(Post::getMediaId).filter(Objects::nonNull).toList()
        );
        var communityUrls = mediaServiceI.getUrls(
                communities.getContent().stream()
                        .flatMap(c -> Stream.of(c.getIconMediaId(), c.getBannerMediaId()))
                        .filter(Objects::nonNull)
                        .toList()
        );
        Set<UUID> joined = communityMembershipServiceI.findJoinedCommunityIds(
                userId,
                communities.getContent().stream().map(Community::getCommunityId).toList()
        );

        return new SearchResponseDTO(
                PagedResponseDTO.from(posts, post -> postConverter.toResponseDto(post, postUrls)),
                PagedResponseDTO.from(
                        communities,
                        community -> communityConverter.toResponseDto(
                                community,
                                communityUrls,
                                joined.contains(community.getCommunityId())
                        )
                ),
                PagedResponseDTO.from(users, userConvert::convertEntityToDto)
        );
    }

    private static Pageable withoutSort(Pageable pageable) {
        if (!pageable.isPaged()) {
            return PageRequest.of(0, FALLBACK_PAGE_SIZE);
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }
}
