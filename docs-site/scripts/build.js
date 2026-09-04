const { spawnSync } = require('child_process');

const nodeMajor = parseInt(process.versions.node.split('.')[0], 10);
const env = { ...process.env };

// Node 25+ has experimental Web Storage API enabled by default which conflicts
// with Docusaurus SSR / multi-locale build when --localstorage-file is not provided.
if (nodeMajor >= 22) {
  env.NODE_OPTIONS = `${env.NODE_OPTIONS || ''} --no-webstorage`.trim();
}

const result = spawnSync('npx', ['docusaurus', 'build', ...process.argv.slice(2)], {
  stdio: 'inherit',
  env,
  shell: true,
});

process.exit(result.status ?? 0);
