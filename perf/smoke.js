import { check, sleep } from 'k6';
import {
  createCommunity,
  createPost,
  getPost,
  listCommunities,
  login,
  registerUser,
  uniqueSuffix,
  votePost,
} from './lib/helpers.js';

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

export default function () {
  const suffix = uniqueSuffix();
  const username = `k6_${suffix}`.slice(0, 20);
  const password = 'password123';
  const email = `${username}@example.com`;

  registerUser(username, password, email);
  const tokens = login(username, password);
  if (!tokens || !tokens.accessToken) {
    throw new Error('smoke: login did not return accessToken');
  }

  const token = tokens.accessToken;
  const community = createCommunity(
    token,
    `K6 ${suffix}`.slice(0, 100),
    `k6-${suffix}`.slice(0, 100)
  );
  if (!community || !community.communityId) {
    throw new Error('smoke: community creation failed');
  }

  const post = createPost(
    token,
    community.communityId,
    `Smoke post ${suffix}`.slice(0, 300),
    'Created by k6 smoke script'
  );
  if (!post || !post.postId) {
    throw new Error('smoke: post creation failed');
  }

  listCommunities(token);
  getPost(token, post.postId);
  const vote = votePost(token, post.postId, 1);
  check(vote, {
    'vote body has value 1': (r) => {
      try {
        return r.json('value') === 1;
      } catch (e) {
        return false;
      }
    },
  });

  sleep(0.5);
}
