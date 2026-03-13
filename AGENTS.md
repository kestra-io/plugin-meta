# Kestra Meta Plugin

## What

description = 'Meta Plugin for Kestra Exposes 13 plugin components (tasks, triggers, and/or conditions).

## Why

Enables Kestra workflows to interact with Meta, allowing orchestration of Meta-based operations as part of data pipelines and automation workflows.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin`:

- `meta`

Infrastructure dependencies (Docker Compose services):

- `app`

### Key Plugin Classes

- `io.kestra.plugin.meta.facebook.posts.Create`
- `io.kestra.plugin.meta.facebook.posts.Delete`
- `io.kestra.plugin.meta.facebook.posts.GetInsights`
- `io.kestra.plugin.meta.facebook.posts.List`
- `io.kestra.plugin.meta.facebook.posts.Schedule`
- `io.kestra.plugin.meta.instagram.media.CreateCarousel`
- `io.kestra.plugin.meta.instagram.media.CreateImage`
- `io.kestra.plugin.meta.instagram.media.CreateVideo`
- `io.kestra.plugin.meta.instagram.media.GetInsights`
- `io.kestra.plugin.meta.instagram.media.List`
- `io.kestra.plugin.meta.messenger.MessengerExecution`
- `io.kestra.plugin.meta.whatsapp.WhatsAppExecution`
- `io.kestra.plugin.meta.whatsapp.WhatsAppIncomingWebhook`

### Project Structure

```
plugin-meta/
├── src/main/java/io/kestra/plugin/meta/whatsapp/
├── src/test/java/io/kestra/plugin/meta/whatsapp/
├── build.gradle
└── README.md
```

### Important Commands

```bash
# Build the plugin
./gradlew shadowJar

# Run tests
./gradlew test

# Build without tests
./gradlew shadowJar -x test
```

### Configuration

All tasks and triggers accept standard Kestra plugin properties. Credentials should use
`{{ secret('SECRET_NAME') }}` — never hardcode real values.

## Agents

**IMPORTANT:** This is a Kestra plugin repository (prefixed by `plugin-`, `storage-`, or `secret-`). You **MUST** delegate all coding tasks to the `kestra-plugin-developer` agent. Do NOT implement code changes directly — always use this agent.
