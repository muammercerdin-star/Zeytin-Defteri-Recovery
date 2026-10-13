'use strict';
const {createHash} = require('node:crypto');
exports.key = (...parts) => createHash('sha256').update(JSON.stringify(parts)).digest('hex');
exports.shouldNotify = (post, comment) => Boolean(post && comment &&
  typeof post.user_id === 'string' && post.user_id &&
  typeof comment.user_id === 'string' && comment.user_id &&
  post.user_id !== comment.user_id && comment.is_active !== 0 && post.is_active !== 0 &&
  typeof comment.content === 'string' && comment.content.trim());
exports.body = (name, content) => `${(name || 'Bir üretici').replace(/\s+/g, ' ').slice(0, 60)} gönderine yanıt verdi: “${content.replace(/\s+/g, ' ').slice(0, 130)}”`;
exports.invalidToken = code => ['messaging/registration-token-not-registered','messaging/invalid-registration-token'].includes(code);
