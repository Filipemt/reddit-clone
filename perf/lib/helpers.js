import http from 'k6/http';
import { check } from 'k6';

export function baseUrl() {
  return __ENV.BASE_URL || 'http://localhost:8080';
}

export function jsonHeaders(token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  return headers;
}

export function authHeaders(token) {
  return { Authorization: `Bearer ${token}` };
}

export function uniqueSuffix() {
  const vu = typeof __VU !== 'undefined' ? __VU : 0;
  const iter = typeof __ITER !== 'undefined' ? __ITER : 0;
  return `${Date.now()}_${vu}_${iter}_${Math.random().toString(36).slice(2, 8)}`;
}

export function registerUser(username, password, email) {
  const res = http.post(
    `${baseUrl()}/users/register`,
    JSON.stringify({ username, password, email }),
    { headers: jsonHeaders() }
  );
  check(res, {
    'register status 201 or 409': (r) => r.status === 201 || r.status === 409,
  });
  return res;
}

export function login(username, password) {
  const res = http.post(
    `${baseUrl()}/authentication/login`,
    JSON.stringify({ username, password }),
    { headers: jsonHeaders() }
  );
  check(res, { 'login status 200': (r) => r.status === 200 });
  if (res.status !== 200) {
    return null;
  }
  return res.json();
}

export function createCommunity(token, name, slug) {
  const payload = {
    name,
    slug,
    description: `k6 community ${slug}`,
    topicId: 1,
    typeId: 1,
    statusId: 1,
  };
  const form = {
    data: http.file(JSON.stringify(payload), 'data.json', 'application/json'),
  };
  const res = http.post(`${baseUrl()}/communities`, form, {
    headers: authHeaders(token),
  });
  check(res, { 'create community status 201': (r) => r.status === 201 });
  if (res.status !== 201) {
    return null;
  }
  return res.json();
}

export function createPost(token, communityId, title, body) {
  const payload = { title, body };
  const form = {
    data: http.file(JSON.stringify(payload), 'data.json', 'application/json'),
  };
  const res = http.post(`${baseUrl()}/communities/${communityId}/posts`, form, {
    headers: authHeaders(token),
  });
  check(res, { 'create post status 201': (r) => r.status === 201 });
  if (res.status !== 201) {
    return null;
  }
  return res.json();
}

export function listCommunities(token) {
  const res = http.get(`${baseUrl()}/communities?page=0&size=20`, {
    headers: authHeaders(token),
  });
  check(res, { 'list communities status 200': (r) => r.status === 200 });
  return res;
}

export function getPost(token, postId) {
  const res = http.get(`${baseUrl()}/posts/${postId}`, {
    headers: authHeaders(token),
  });
  check(res, { 'get post status 200': (r) => r.status === 200 });
  return res;
}

export function votePost(token, postId, value) {
  const res = http.put(
    `${baseUrl()}/posts/${postId}/vote`,
    JSON.stringify({ value }),
    { headers: jsonHeaders(token) }
  );
  check(res, { 'vote post status 200': (r) => r.status === 200 });
  return res;
}
