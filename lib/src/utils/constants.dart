const providedDepsBoxName = 'ai2-provided-deps';
const buildLibsBoxName = 'build-libs';
const extensionDepsBoxName = 'deps';

const timestampBoxName = 'timestamps';
const configTimestampKey = 'bolt-yaml';
const androidManifestTimestampKey = 'android-manifest-xml';

const defaultKtVersion = '1.8.0';

// ProGuard version that Bolt bundles by default.  Template files interpolate
// this constant when generating a new project so that the configuration file
// can refer to it without hard‑coding the number.
const defaultProguardVersion = '7.8.2';

const annotationProcVersion = '2.0.4';
const ai2RuntimeVersion = 'nb190b.1';
const ai2RuntimeCoord = 'io.github.techhamara.bolt:runtime:$ai2RuntimeVersion';
const ai2AnnotationVersion = '2.0.1';

const androidPlatformSdkVersion = '35';
