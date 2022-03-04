# jar-deprecate
tool to automatically deprecate every method, field, and class in a given jar file, supporting both sources and binary jars. sources jars are currently detected based on the filename ending with `-sources.jar`.

usage: `java -jar jar-deprecate-[version]-all.jar [[input-path] [output-path]]... [--parallelism [int]] [--message [deprecation-message]]`

- multiple pairs of input/output paths may be provided, should be before other options
- parallelism defaults to `4` when not set
- deprecation message defaults to `Deprecated API.` when not set
- `--message` consumes all remaining input and therefore must be the last argument
