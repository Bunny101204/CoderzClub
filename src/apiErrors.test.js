import test from 'node:test';
import assert from 'node:assert/strict';
import { apiErrorMessage } from './apiErrors.js';

test('server and network failures never display diagnostic text', () => {
  for (const data of ['Internal host secret', { error: 'Database detail' }, { message: 'Connection failed' }]) {
    assert.equal(apiErrorMessage({ response: { status: 503, data } }, 'Try again'), 'Try again');
  }
  assert.equal(apiErrorMessage(new Error('internal-host:27017'), 'Try again'), 'Try again');
});

test('legacy client errors cannot expose MongoDB, connection or stack details', () => {
  for (const message of ['MongoDB code 263', 'OperationNotSupportedInTransaction',
    'mongodb+srv://user:password@cluster', 'java.lang.IllegalStateException',
    'Connection to 127.0.0.1 refused', 'redis://internal:6379', 'stack trace']) {
    for (const data of [message, { error: message }, { message }]) {
      assert.equal(apiErrorMessage({ response: { status: 400, data } }, 'Try again'), 'Try again');
    }
  }
});

test('safe validation and business messages remain useful', () => {
  assert.equal(apiErrorMessage({ response: { status: 429, data: { message: 'Please wait before submitting again.' } } }),
    'Please wait before submitting again.');
});
