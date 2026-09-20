// Metro in monorepo: senza questo, l'app non risolve @tabletop/shared.
const { getDefaultConfig } = require('expo/metro-config');
const path = require('node:path');

const progetto = __dirname;
const radice = path.resolve(progetto, '../..');

const config = getDefaultConfig(progetto);
config.watchFolders = [radice];
config.resolver.nodeModulesPaths = [
  path.resolve(progetto, 'node_modules'),
  path.resolve(radice, 'node_modules'),
];
// pnpm usa link simbolici: senza questo Metro segue il link e perde il contesto.
config.resolver.unstable_enableSymlinks = true;
config.resolver.disableHierarchicalLookup = true;

module.exports = config;
