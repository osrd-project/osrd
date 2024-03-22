import { mkdtemp, readFile } from 'node:fs/promises';
import os from 'node:os';

import { glob } from 'glob';
import * as I18nextParser from 'i18next-parser';
import { jsonKeyPathList } from 'json-key-path-list';
import vfs from 'vinyl-fs';

const IGNORE_MISSING: RegExp[] = [
  // key used by a t function in modules/trainschedule/components/ManageTrainSchedule/helpers/formatConf.ts
  /translation:errorMessages\..*/,
  /translation:error/,
  /translation:default/,
  /translation:error/,
  /translation:unspecified/,
];
const IGNORE_UNUSED: RegExp[] = [
  /.*-generated$/,
  /.*\.generated\..*$/,
  /errors:.*/, // Errors are generated and used dynamicly
  /infraEditor:.*/, // Translation of properties object for the form
  /infraEditor:__main____.*/, // Found by error by i18n parser in a json-schema
  /translation:Editor\.tools\..*/, // Editor tool's label are generated
  /translation:Editor.obj-types\..*/, // Type of object are translated dynamicly on the sumpup popin
  /translation:Editor.directions\..*/,
  /translation:Editor.layers\..*/,
  /Editor\.item-statuses\..*/,
  /translation:Editor\.infra-errors\.error-type\..*/, // Infra error types are generated
  /translation:Editor\.infra-errors\.error-level\..*/, // Infra error level are generated
  /translation:Editor\.infra-errors\.corrector-modal\..*/,
  /home\/navbar:language\..*/, // Language selector which is generated with the locale
];

/**
 * Read a file and returns its content as a JSON
 */
async function readJsonFile<T extends { [key: string]: unknown }>(filePath: string): Promise<T> {
  const data = await readFile(filePath, 'utf-8');
  return JSON.parse(data);
}

/**
 * Given a locales folder, return the list of all i18n keys.
 */
async function getLocalesKeys(localePath: string): Promise<Set<string>> {
  const pathForFrenchLng = `${localePath}/fr/`;
  const files = await glob(`${pathForFrenchLng}/**/*.json`);
  const allKeys = (
    await Promise.all(
      files.map(async (file) => {
        const data = await readJsonFile(file);
        const namespace = file.replace(pathForFrenchLng, '').replace(/\.json$/, '');
        return jsonKeyPathList(data).map(
          (key) => `${namespace}:${key.replace(/_(zero|one|other|many)$/, '')}`
        );
      })
    )
  )
    .flat()
    .sort();
  return new Set(allKeys);
}

/**
 * Scan the source code and generate a locale folder structure
 * in a temp folder, with all the i18n key found.
 *
 * @returns The location of the temp folder
 */
async function scanCode(): Promise<string> {
  const tempDir = await mkdtemp(`${os.tmpdir()}/osrd-i18n-`);
  return new Promise((resolve, reject) => {
    const stream = vfs
      .src(`${process.cwd()}/src/**/*.{ts,tsx}`)
      .pipe(
        // eslint-disable-next-line
        new (I18nextParser as any).gulp({
          locales: ['fr'],
          output: '$LOCALE/$NAMESPACE.json',
        })
      )
      .pipe(vfs.dest(tempDir));

    stream.on('finish', () => resolve(tempDir));
    stream.on('error', (e: Error) => reject(e));
  });
}

/**
 * The script execution
 */
async function run() {
  try {
    const appNamespacedKeys = await getLocalesKeys(`${process.cwd()}/public/locales`);

    const scannedLocalePath = await scanCode();
    const scannedNamespacedKeys = await getLocalesKeys(scannedLocalePath);

    // Search for unused keys
    const unusedKeys: string[] = [];
    appNamespacedKeys.forEach((key) => {
      if (
        !scannedNamespacedKeys.has(key) &&
        IGNORE_UNUSED.every((pattern) => !key.match(pattern))
      ) {
        unusedKeys.push(key);
      }
    });

    // Search for missing traduction
    const missingKeys: string[] = [];
    scannedNamespacedKeys.forEach((keyWithNS) => {
      if (
        !appNamespacedKeys.has(keyWithNS) &&
        IGNORE_MISSING.every((pattern) => !keyWithNS.match(pattern))
      ) {
        missingKeys.push(keyWithNS);
      }
    });

    /* eslint-disable no-console */
    if (unusedKeys.length > 0) {
      console.log(`Unused keys (${unusedKeys.length})`);
      console.log('----------------------------------');
      console.log(unusedKeys.join('\n'));
      console.log();
    }

    if (missingKeys.length > 0) {
      console.log(`Missing keys (${missingKeys.length})`);
      console.log('------------------------------------');
      console.log(missingKeys.join('\n'));
      console.log();
      console.log('/!\\ Failed: missing keys are not allowed');
      process.exit(1);
    }
  } catch (err) {
    console.error(err);
    process.exit(1);
  } finally {
    process.exit();
  }
}

run();
