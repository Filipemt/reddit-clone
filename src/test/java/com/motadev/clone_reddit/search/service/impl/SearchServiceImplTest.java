package com.motadev.clone_reddit.search.service.impl;

import com.motadev.clone_reddit.auth.logging.AuthEventLog;
import com.motadev.clone_reddit.community.converter.CommunityConverter;
import com.motadev.clone_reddit.community.repository.CommunityRepository;
import com.motadev.clone_reddit.community.service.CommunityMembershipServiceI;
import com.motadev.clone_reddit.media.service.MediaServiceI;
import com.motadev.clone_reddit.post.converter.PostConverter;
import com.motadev.clone_reddit.post.repository.PostRepository;
import com.motadev.clone_reddit.shared.exception.ResourceInvalidException;
import com.motadev.clone_reddit.shared.security.AuthenticatedUserProvider;
import com.motadev.clone_reddit.user.convert.UserConvert;
import com.motadev.clone_reddit.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private PostRepository postRepository;
    @Mock
    private CommunityRepository communityRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CommunityConverter communityConverter;
    @Mock
    private UserConvert userConvert;
    @Mock
    private MediaServiceI mediaServiceI;
    @Mock
    private CommunityMembershipServiceI communityMembershipServiceI;

    private SearchServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SearchServiceImpl(
                postRepository,
                communityRepository,
                userRepository,
                new PostConverter(),
                communityConverter,
                userConvert,
                mediaServiceI,
                communityMembershipServiceI,
                new AuthenticatedUserProvider(new AuthEventLog())
        );
        setAuthenticatedUser(USER_ID);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsBlankQuery() {
        assertThatThrownBy(() -> service.search("  ", PageRequest.of(0, 10)))
                .isInstanceOf(ResourceInvalidException.class);
    }

    @Test
    void searchDelegatesToIlikeRepositories() {
        when(postRepository.searchActiveIlike(eq("java"), any())).thenReturn(Page.empty());
        when(communityRepository.searchActiveIlike(eq("java"), any())).thenReturn(Page.empty());
        when(userRepository.searchByUsernameIlike(eq("java"), any())).thenReturn(new PageImpl<>(List.of()));
        when(mediaServiceI.getUrls(anyCollection())).thenReturn(Map.of());
        when(communityMembershipServiceI.findJoinedCommunityIds(eq(USER_ID), anyCollection())).thenReturn(Set.of());

        service.search("java", PageRequest.of(0, 10));

        verify(postRepository).searchActiveIlike(eq("java"), any());
        verify(communityRepository).searchActiveIlike(eq("java"), any());
        verify(userRepository).searchByUsernameIlike(eq("java"), any());
    }

    private static void setAuthenticatedUser(UUID userId) {
        var auth = new UsernamePasswordAuthenticationToken(
                userId.toString(),
                "n/a",
                List.of(new SimpleGrantedAuthority("SCOPE_BASIC"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
