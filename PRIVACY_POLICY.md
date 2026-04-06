# SpeQA Privacy Policy

*Last updated: April 13, 2026*

## Overview

SpeQA runs entirely on your local machine. It does not require an internet
connection and does not collect any data during normal operation.

The only network feature is opt-in error reporting, described below.

## Error Reporting (opt-in)

When an unhandled error occurs, the IDE displays a standard error dialog. You
may choose to report the error by clicking **Report to SpeQA**. No data is sent
unless you explicitly click the button.

### What is sent

- Exception class names, method names, and line numbers
- Plugin version
- IDE product and build version
- Operating system name and version
- Java runtime version
- Your comment, if you choose to add one (limited to 1 000 characters)

### What is NOT sent

- Exception messages
- File paths or project structure
- Source code or file contents
- Usage analytics or telemetry

### Personal data

SpeQA does not collect personal information such as names, emails, or account
data. Error reports contain only technical diagnostic information listed above.

Your IP address is visible to Sentry as part of the HTTPS connection. SpeQA
does not access, store, or process IP addresses. Sentry's handling of IP
addresses is governed by [Sentry's privacy policy](https://sentry.io/privacy/).

If you include personal information in the optional comment field, it will be
sent to Sentry as part of the error report.

### Where data is sent

Error reports are transmitted over HTTPS to [Sentry](https://sentry.io), hosted
in the European Union (Frankfurt, Germany). Data retention is governed by
Sentry's policies.

SpeQA does not operate its own servers and does not store error reports.

## Changes to This Policy

Material changes will be noted in the plugin's change notes on JetBrains
Marketplace.
