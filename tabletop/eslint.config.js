// @ts-check
import js from '@eslint/js';
import tseslint from 'typescript-eslint';

/**
 * Confini architetturali (master prompt §5, AC di S0-01).
 * Le feature non si importano fra loro: quello che condividono sta in packages/shared.
 * Il dominio non conosce React ne' SDK di rete.
 */
const confiniFeature = {
  files: ['apps/mobile/src/features/*/**/*.{ts,tsx}'],
  rules: {
    'no-restricted-imports': ['error', {
      patterns: [{
        group: ['**/features/*/**', '@/features/*'],
        message:
          'Import fra feature vietato: una feature non conosce le altre. ' +
          'Cio\' che serve a entrambe va in packages/shared.',
      }],
    }],
  },
};

const dominioPuro = {
  files: ['packages/shared/**/*.ts'],
  rules: {
    'no-restricted-imports': ['error', {
      paths: [
        { name: 'react', message: 'packages/shared e\' dominio puro: niente React.' },
        { name: 'react-native', message: 'packages/shared e\' dominio puro: niente React Native.' },
        { name: 'axios', message: 'packages/shared e\' dominio puro: niente I/O di rete.' },
        { name: 'node:fs', message: 'packages/shared e\' dominio puro: niente I/O su disco.' },
      ],
      patterns: [{
        group: ['@supabase/*', 'firebase*', 'pg', 'fastify*'],
        message: 'packages/shared e\' dominio puro: niente SDK di infrastruttura.',
      }],
    }],
  },
};

export default tseslint.config(
  { ignores: ['**/dist/**', '**/node_modules/**', '**/*.tsbuildinfo'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    rules: {
      '@typescript-eslint/consistent-type-imports': 'error',
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
      'no-console': ['warn', { allow: ['warn', 'error'] }],
    },
  },
  confiniFeature,
  dominioPuro,
);
