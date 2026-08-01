#!/usr/bin/env node
'use strict';

const { main } = require('../src/index');

main(process.argv.slice(2)).then((code) => {
  if (typeof code === 'number') process.exitCode = code;
}).catch((err) => {
  console.error(err && err.message ? err.message : String(err));
  process.exitCode = 1;
});
