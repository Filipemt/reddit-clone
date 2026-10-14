import { sleep } from 'k6';
import {
  createCommunity,
  createPost,
  getPost,
  listCommunities,
  login,
  registerUser,
  uniqueSuffix,
} from './lib/helpers.js';

export const options = {
  stages: [
    { duration: '15s', target: 5 },
    { duration: '30s', target: 5 },
    { duration: '15s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1000'],
  },
};

export function setup() {
  const suffix = uniqueSuffix();
  const username = `k6load_${suffix}`.slice(0, 20);
  const password = 'password123';
  const email = `${username}@example.com`;

  registerUser(username, password, email);
  const tokens = login(username, password);
  if (!tokens || !tokens.accessToken) {
    throw new Error('load-read setup: login failed');
  }

  const token = tokens.accessToken;
  const community = createCommunity(
    token,
    `K6Load ${suffix}`.slice(0, 100),
    `k6load-${suffix}`.slice(0, 100)
  );
  if (!community || !community.communityId) {
    throw new Error('load-read setup: community creation failed');
  }

  const post = createPost(
    token,
    community.communityId,
    `Load fixture ${suffix}`.slice(0, 300),
    'Fixture post for k6 load-read'
  );
  if (!post || !post.postId) {
    throw new Error('load-read setup: post creation failed');
  }

  return {
    username,
    password,
    postId: post.postId,
  };
}

export default function (data) {
  const tokens = login(data.username, data.password);
  if (!tokens || !tokens.accessToken) {
    return;
  }

  const token = tokens.accessToken;
  listCommunities(token);
  getPost(token, data.postId);
  sleep(1);
}
